package com.root.aishopback.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.root.aishopback.service.AiShoppingAgentService;
import com.root.aishopback.service.ShopCartService;
import com.root.aishopback.service.ShopProductService;
import com.root.aishopback.vo.CartItemVO;
import com.root.aishopback.vo.ProductVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Primary
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "coze")
public class CozePythonShoppingAgentService implements AiShoppingAgentService {

    private static final long GUEST_USER_KEY = 0L;
    private static final Pattern PRODUCT_ID_PATTERN = Pattern.compile("(?:商品\\s*ID|ID|id)\\s*[:：]?\\s*(\\d{3,10})");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d{1,3})");
    private static final Pattern QTY_PATTERN = Pattern.compile("(?:数量|x|X|\\*)\\s*[:：]?\\s*(\\d{1,3})|(\\d{1,3})\\s*(?:件|份|个|袋|包)");

    private final HttpClient httpClient;
    private final ShopCartService shopCartService;
    private final ShopProductService shopProductService;
    private final String cozeBaseUrl;
    private final String chatPath;
    private final String chatModel;
    private final String apiKey;
    private final double temperature;
    private final int maxHistoryTurns;
    private final long timeoutMillis;
    private final int retryMaxAttempts;
    private final long retryBaseBackoffMillis;
    private final boolean localCartFallbackEnabled;

    private final Map<Long, Deque<Map<String, String>>> conversations = new ConcurrentHashMap<>();
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong failures = new AtomicLong(0);
    private volatile String lastError = "";

    public CozePythonShoppingAgentService(
        ShopCartService shopCartService,
        ShopProductService shopProductService,
        @Value("${app.ai.coze.base-url:http://127.0.0.1:5000}") String cozeBaseUrl,
        @Value("${app.ai.coze.chat-path:/run}") String chatPath,
        @Value("${app.ai.coze.chat-model:coze-agent}") String chatModel,
        @Value("${app.ai.coze.api-key:}") String apiKey,
        @Value("${app.ai.coze.temperature:0.4}") double temperature,
        @Value("${app.ai.coze.max-history-turns:10}") int maxHistoryTurns,
        @Value("${app.ai.coze.timeout-millis:30000}") long timeoutMillis,
        @Value("${app.ai.coze.retry-max-attempts:3}") int retryMaxAttempts,
        @Value("${app.ai.coze.retry-base-backoff-millis:800}") long retryBaseBackoffMillis,
        @Value("${app.ai.coze.local-cart-fallback-enabled:false}") boolean localCartFallbackEnabled
    ) {
        this.shopCartService = shopCartService;
        this.shopProductService = shopProductService;
        this.cozeBaseUrl = trimTrailingSlash(cozeBaseUrl);
        this.chatPath = chatPath.startsWith("/") ? chatPath : "/" + chatPath;
        this.chatModel = chatModel;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.temperature = temperature;
        this.maxHistoryTurns = Math.max(1, maxHistoryTurns);
        this.timeoutMillis = Math.max(1000L, timeoutMillis);
        this.retryMaxAttempts = Math.max(1, retryMaxAttempts);
        this.retryBaseBackoffMillis = Math.max(100L, retryBaseBackoffMillis);
        this.localCartFallbackEnabled = localCartFallbackEnabled;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(this.timeoutMillis))
            .build();
    }

    @Override
    public AgentReply reply(String message, Long userId) {
        String prompt = message == null ? "" : message.trim();
        if (prompt.isBlank()) {
            return new AgentReply("\u8bf7\u544a\u8bc9\u6211\u4f60\u60f3\u4e70\u4ec0\u4e48\uff0c\u6bd4\u5982\uff1a300\u5143\u4ee5\u5185\u7684\u978b\u5b50\u3002", List.of());
        }

        long reqNo = totalRequests.incrementAndGet();
        long userKey = userId == null ? GUEST_USER_KEY : userId;
        Deque<Map<String, String>> history = conversations.computeIfAbsent(userKey, k -> new ArrayDeque<>());
        history.addLast(message("user", prompt));
        trimHistory(history);

        if (localCartFallbackEnabled) {
            AgentReply localHandled = tryHandleLocalCartIntent(prompt, userKey, history);
            if (localHandled != null) {
                history.addLast(message("assistant", localHandled.content()));
                trimHistory(history);
                return localHandled;
            }
        }

        try {
            String content = callCoze(history, userKey, prompt);
            if (content == null || content.isBlank()) {
                content = "\u6211\u6536\u5230\u4f60\u7684\u9700\u6c42\u4e86\uff0c\u4f46\u6682\u65f6\u6ca1\u6709\u62ff\u5230\u6709\u6548\u7ed3\u679c\uff0c\u8bf7\u518d\u8bd5\u4e00\u6b21\u3002";
            }
            if (userKey > 0L) {
                content = suppressUserIdPrompt(content);
            }
            history.addLast(message("assistant", content));
            trimHistory(history);
            lastError = "";
            return new AgentReply(content, extractProductCards(content));
        } catch (Exception ex) {
            failures.incrementAndGet();
            lastError = "request#" + reqNo + ": " + ex.getMessage();
            String fallback = "\u5bfc\u8d2d\u52a9\u624b\u6682\u65f6\u4e0d\u53ef\u7528\u3002 " + safe(ex.getMessage());
            return new AgentReply(fallback, List.of());
        }
    }

    @Override
    public AgentHealth health() {
        boolean reachable;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(cozeBaseUrl + "/health"))
                .timeout(Duration.ofMillis(timeoutMillis))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            reachable = response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ignored) {
            reachable = false;
        }
        long req = totalRequests.get();
        long err = failures.get();
        return new AgentHealth(
            reachable,
            reachable,
            false,
            cozeBaseUrl,
            chatModel,
            maskKey(apiKey),
            "none",
            false,
            0,
            lastError,
            req,
            0,
            0.0,
            0,
            0,
            0.0,
            req,
            err,
            Map.of("coze", req),
            Map.of(),
            Map.of(),
            Map.of(),
            0,
            List.of()
        );
    }

    @Override
    public LlmPingResult llmPing(String message) {
        long callsBefore = totalRequests.get();
        long errorsBefore = failures.get();
        AgentReply reply = reply(message == null || message.isBlank() ? "ping" : message, null);
        long callsAfter = totalRequests.get();
        long errorsAfter = failures.get();
        boolean success = errorsAfter == errorsBefore;
        return new LlmPingResult(
            success,
            reply.content(),
            success ? null : lastError,
            callsBefore,
            callsAfter,
            callsAfter - callsBefore,
            errorsBefore,
            errorsAfter,
            errorsAfter - errorsBefore,
            Map.of("coze", callsAfter)
        );
    }

    @Override
    public void clearContext(Long userId) {
        long userKey = userId == null ? GUEST_USER_KEY : userId;
        conversations.remove(userKey);
    }

    private String callCoze(Deque<Map<String, String>> history, long userKey, String prompt) throws Exception {
        JSONObject payload = buildPayload(history, userKey, prompt);
        Exception lastEx = null;

        for (int attempt = 1; attempt <= retryMaxAttempts; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(cozeBaseUrl + chatPath))
                    .timeout(Duration.ofMillis(timeoutMillis))
                    .header("x-run-id", "chat-" + userKey + "-" + UUID.randomUUID())
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toJSONString(), StandardCharsets.UTF_8));
                if (!apiKey.isBlank()) {
                    builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
                }

                HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();
                String body = response.body() == null ? "" : response.body();
                if (status < 200 || status >= 300) {
                    IllegalStateException ex = new IllegalStateException("coze status=" + status + ", body=" + shrink(body, 400));
                    if (attempt < retryMaxAttempts && isRetryableStatus(status)) {
                        sleepBackoff(attempt);
                        lastEx = ex;
                        continue;
                    }
                    throw ex;
                }

                JSONObject root = JSON.parseObject(body);
                String content = extractContent(root);
                return content == null ? "" : content.trim();
            } catch (Exception ex) {
                if (attempt < retryMaxAttempts && isRetryableException(ex)) {
                    lastEx = ex;
                    sleepBackoff(attempt);
                    continue;
                }
                throw ex;
            }
        }

        if (lastEx != null) {
            throw lastEx;
        }
        throw new IllegalStateException("coze request failed after retries");
    }

    private JSONObject buildPayload(Deque<Map<String, String>> history, long userKey, String prompt) {
        String enrichedPrompt = enrichPromptWithUser(userKey, prompt);
        if ("/run".equals(chatPath)) {
            JSONObject payload = new JSONObject();
            JSONArray messages = new JSONArray();
            JSONObject userContext = new JSONObject();
            userContext.put("role", "system");
            userContext.put("content", buildUserContextInstruction(userKey));
            messages.add(userContext);
            for (Map<String, String> msg : history) {
                if (msg == null) {
                    continue;
                }
                String role = msg.get("role");
                String content = msg.get("content");
                if (role == null || content == null || content.isBlank()) {
                    continue;
                }
                if ("user".equals(role) && prompt != null && content.equals(prompt)) {
                    content = enrichedPrompt;
                }
                JSONObject row = new JSONObject();
                row.put("role", role);
                row.put("content", content);
                messages.add(row);
            }
            if (messages.isEmpty()) {
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", enrichedPrompt);
                messages.add(userMsg);
            }
            payload.put("messages", messages);
            return payload;
        }

        JSONObject payload = new JSONObject();
        payload.put("model", chatModel);
        payload.put("temperature", temperature);
        payload.put("stream", false);
        payload.put("messages", new ArrayList<>(history));
        return payload;
    }

    private String enrichPromptWithUser(long userKey, String prompt) {
        String base = prompt == null ? "" : prompt.trim();
        if (userKey <= 0L) {
            return base;
        }
        return "当前登录用户ID=" + userKey + "。用户原话：" + base;
    }

    private String buildUserContextInstruction(long userKey) {
        if (userKey > 0L) {
            return "系统上下文：用户已登录，当前用户ID=" + userKey
                + "。调用购物车、订单、个性化推荐工具时，直接使用该 user_id。"
                + "禁止向用户索要 user_id。";
        }
        return "系统上下文：用户未登录。涉及购物车、订单操作时，先提示用户登录。";
    }

    private String suppressUserIdPrompt(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        String[] lines = content.replace("\r\n", "\n").split("\n");
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String lower = line.toLowerCase();
            if (lower.contains("user_id") || lower.contains("user id")) {
                continue;
            }
            kept.add(line);
        }
        return String.join("\n", kept).trim();
    }

    private String extractContent(JSONObject root) {
        if (root == null) {
            return "";
        }

        JSONArray choices = root.getJSONArray("choices");
        if (choices != null && !choices.isEmpty()) {
            JSONObject c0 = choices.getJSONObject(0);
            if (c0 != null) {
                JSONObject msg = c0.getJSONObject("message");
                if (msg != null) {
                    Object contentObj = msg.get("content");
                    String content = flattenContent(contentObj);
                    if (!content.isBlank()) {
                        return content;
                    }
                }
            }
        }

        JSONArray messages = root.getJSONArray("messages");
        if (messages != null && !messages.isEmpty()) {
            JSONObject last = messages.getJSONObject(messages.size() - 1);
            if (last != null) {
                String content = last.getString("content");
                if (content != null && !content.isBlank()) {
                    return content;
                }
            }
        }

        String fallback = root.getString("output_text");
        return fallback == null ? "" : fallback;
    }

    private String flattenContent(Object contentObj) {
        if (contentObj == null) {
            return "";
        }
        if (contentObj instanceof String s) {
            return s;
        }
        if (contentObj instanceof JSONArray arr) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.size(); i++) {
                Object item = arr.get(i);
                if (item instanceof JSONObject jo) {
                    String text = jo.getString("text");
                    if (text != null) {
                        sb.append(text);
                    }
                } else if (item != null) {
                    sb.append(item);
                }
            }
            return sb.toString();
        }
        return String.valueOf(contentObj);
    }

    private void trimHistory(Deque<Map<String, String>> history) {
        int maxMessages = maxHistoryTurns * 2;
        while (history.size() > maxMessages) {
            history.removeFirst();
        }
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> msg = new HashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        return msg;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://127.0.0.1:5000";
        }
        String out = value.trim();
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private String maskKey(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String shrink(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen) + "...";
    }

    private boolean isRetryableStatus(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    private boolean isRetryableException(Exception ex) {
        String msg = ex == null || ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        return msg.contains("timed out")
            || msg.contains("timeout")
            || msg.contains("connection")
            || msg.contains("connect")
            || msg.contains("system cpu overloaded");
    }

    private void sleepBackoff(int attempt) {
        long sleepMillis = retryBaseBackoffMillis * (1L << Math.max(0, attempt - 1));
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private AgentReply tryHandleLocalCartIntent(String prompt, long userKey, Deque<Map<String, String>> history) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }
        String lowered = prompt.toLowerCase();
        boolean cartRelated = lowered.contains("购物车") || lowered.contains("加购") || lowered.contains("加入");
        if (!cartRelated) {
            return null;
        }
        if (userKey <= 0L) {
            return new AgentReply("当前未登录，暂时无法操作购物车，请先登录。", List.of());
        }

        if (isClearCartIntent(lowered)) {
            List<CartItemVO> items = shopCartService.listCart(userKey);
            for (CartItemVO item : items) {
                if (item != null && item.getId() != null) {
                    shopCartService.removeCart(userKey, item.getId(), null);
                }
            }
            return new AgentReply("已为你清空购物车。", List.of());
        }

        if (isRemoveCartIntent(lowered)) {
            Long pid = resolveTargetProductId(prompt, history);
            if (pid == null) {
                return new AgentReply("请告诉我要移除的商品ID，例如：移除 商品ID 11976。", List.of());
            }
            shopCartService.removeCart(userKey, null, pid);
            return new AgentReply("已从购物车移除商品ID " + pid + "。", List.of());
        }

        if (isUpdateCartIntent(lowered)) {
            Long pid = resolveTargetProductId(prompt, history);
            Integer qty = resolveQuantity(prompt, 1);
            if (pid == null) {
                return new AgentReply("请告诉我要修改的商品ID和数量，例如：商品ID 11976 数量 2。", List.of());
            }
            if (qty <= 0) {
                shopCartService.removeCart(userKey, null, pid);
                return new AgentReply("数量为0，已帮你移除商品ID " + pid + "。", List.of());
            }
            shopCartService.updateCart(userKey, null, pid, qty);
            return new AgentReply("已将商品ID " + pid + " 的数量改为 " + qty + "。", List.of());
        }

        if (isAddToCartIntent(lowered)) {
            Long pid = resolveTargetProductId(prompt, history);
            Integer qty = resolveQuantity(prompt, 1);
            if (pid == null) {
                return new AgentReply("我识别到你要加购，但还没拿到商品ID。你可以说：加入购物车 商品ID 11976 数量 1。", List.of());
            }
            CartItemVO item = shopCartService.addToCart(userKey, pid, qty);
            ProductVO product = shopProductService.getProductDetail(pid);
            String name = product == null || product.getName() == null ? ("商品ID " + pid) : product.getName();
            int finalQty = item == null || item.getQuantity() == null ? qty : item.getQuantity();
            return new AgentReply("已加入购物车：" + name + " x " + qty + "，当前购物车该商品数量 " + finalQty + "。", product == null ? List.of() : List.of(product));
        }

        if (isViewCartIntent(lowered)) {
            return buildCartViewReply(userKey);
        }
        return null;
    }

    private AgentReply buildCartViewReply(long userKey) {
        List<CartItemVO> items = shopCartService.listCart(userKey);
        if (items == null || items.isEmpty()) {
            return new AgentReply("你的购物车还是空的，可以先让我推荐商品后再加购。", List.of());
        }
        StringBuilder sb = new StringBuilder("当前购物车有 ").append(items.size()).append(" 件商品：\n");
        List<ProductVO> cards = new ArrayList<>();
        int idx = 1;
        for (CartItemVO it : items) {
            if (it == null) continue;
            sb.append(idx).append(". ID ").append(it.getProductId() == null ? "-" : it.getProductId())
                .append("｜").append(it.getName() == null ? "未命名商品" : it.getName())
                .append("｜数量 ").append(it.getQuantity() == null ? 1 : it.getQuantity())
                .append("\n");
            if (it.getProductId() != null) {
                ProductVO p = shopProductService.getProductDetail(it.getProductId());
                if (p != null && cards.size() < 4) {
                    cards.add(p);
                }
            }
            idx++;
        }
        return new AgentReply(sb.toString().trim(), cards);
    }

    private boolean isAddToCartIntent(String lowered) {
        return lowered.contains("加入购物车") || lowered.contains("加到购物车") || lowered.contains("加购") || lowered.contains("加入");
    }

    private boolean isViewCartIntent(String lowered) {
        if (!lowered.contains("购物车")) {
            return false;
        }
        if (isAddToCartIntent(lowered) || isRemoveCartIntent(lowered) || isUpdateCartIntent(lowered) || isClearCartIntent(lowered)) {
            return false;
        }
        return lowered.contains("看看")
            || lowered.contains("查看")
            || lowered.contains("看下")
            || lowered.contains("查购物车")
            || lowered.contains("购物车里有什么")
            || lowered.equals("购物车");
    }

    private boolean isRemoveCartIntent(String lowered) {
        return lowered.contains("移除") || lowered.contains("删除") || lowered.contains("去掉");
    }

    private boolean isClearCartIntent(String lowered) {
        return lowered.contains("清空购物车") || (lowered.contains("购物车") && lowered.contains("清空"));
    }

    private boolean isUpdateCartIntent(String lowered) {
        return lowered.contains("数量") || lowered.contains("改成") || lowered.contains("改为");
    }

    private Integer resolveQuantity(String prompt, int fallback) {
        Matcher m = QTY_PATTERN.matcher(prompt);
        while (m.find()) {
            String g1 = m.group(1);
            String g2 = m.group(2);
            String v = g1 != null && !g1.isBlank() ? g1 : g2;
            if (v == null || v.isBlank()) continue;
            try {
                return Math.max(0, Math.min(99, Integer.parseInt(v)));
            } catch (NumberFormatException ignored) {
                // continue
            }
        }
        return fallback;
    }

    private Long resolveTargetProductId(String prompt, Deque<Map<String, String>> history) {
        Long explicit = parseExplicitProductId(prompt);
        if (explicit != null) {
            return explicit;
        }
        int ordinal = parseOrdinal(prompt);
        if (ordinal > 0) {
            List<Long> ids = extractIdsFromLastAssistant(history);
            if (ids.size() >= ordinal) {
                return ids.get(ordinal - 1);
            }
            Long byName = resolveProductIdByAssistantName(history, ordinal);
            if (byName != null) {
                return byName;
            }
        }
        return null;
    }

    private Long parseExplicitProductId(String text) {
        Matcher m = PRODUCT_ID_PATTERN.matcher(text == null ? "" : text);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        Matcher num = NUMBER_PATTERN.matcher(text == null ? "" : text);
        while (num.find()) {
            String n = num.group(1);
            if (n == null) continue;
            try {
                long v = Long.parseLong(n);
                if (v >= 1000) {
                    return v;
                }
            } catch (NumberFormatException ignored) {
                // continue
            }
        }
        return null;
    }

    private int parseOrdinal(String text) {
        String t = text == null ? "" : text;
        if (t.contains("第一个") || t.contains("第1个") || t.contains("第一")) return 1;
        if (t.contains("第二个") || t.contains("第2个") || t.contains("第二")) return 2;
        if (t.contains("第三个") || t.contains("第3个") || t.contains("第三")) return 3;
        if (t.contains("第四个") || t.contains("第4个") || t.contains("第四")) return 4;
        return 0;
    }

    private List<Long> extractIdsFromLastAssistant(Deque<Map<String, String>> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        List<Map<String, String>> copy = new ArrayList<>(history);
        for (int i = copy.size() - 1; i >= 0; i--) {
            Map<String, String> msg = copy.get(i);
            if (msg == null) continue;
            if (!"assistant".equals(msg.get("role"))) continue;
            String content = msg.get("content");
            if (content == null || content.isBlank()) continue;
            LinkedHashSet<Long> ids = new LinkedHashSet<>();
            Matcher m = PRODUCT_ID_PATTERN.matcher(content);
            while (m.find()) {
                try {
                    ids.add(Long.parseLong(m.group(1)));
                } catch (Exception ignored) {
                    // no-op
                }
                if (ids.size() >= 8) break;
            }
            if (!ids.isEmpty()) {
                return new ArrayList<>(ids);
            }
        }
        return List.of();
    }

    private Long resolveProductIdByAssistantName(Deque<Map<String, String>> history, int ordinal) {
        if (history == null || history.isEmpty() || ordinal <= 0) {
            return null;
        }
        List<Map<String, String>> copy = new ArrayList<>(history);
        for (int i = copy.size() - 1; i >= 0; i--) {
            Map<String, String> msg = copy.get(i);
            if (msg == null || !"assistant".equals(msg.get("role"))) {
                continue;
            }
            String content = msg.get("content");
            if (content == null || content.isBlank()) {
                continue;
            }
            List<String> names = extractCandidateNames(content);
            if (names.size() >= ordinal) {
                String targetName = names.get(ordinal - 1);
                List<ProductVO> candidates = shopProductService.listProducts(null, targetName);
                if (candidates != null && !candidates.isEmpty() && candidates.get(0).getId() != null) {
                    return candidates.get(0).getId();
                }
            }
        }
        return null;
    }

    private List<String> extractCandidateNames(String content) {
        String[] lines = content.replace("\r\n", "\n").split("\n");
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            if (line == null) continue;
            String s = line.trim();
            if (s.isBlank()) continue;
            // Typical recommendation line patterns.
            if (!(s.contains("｜") || s.contains("|") || s.startsWith("-") || s.startsWith("**"))) {
                continue;
            }
            String cleaned = s
                .replaceAll("^[-*\\d.\\s]+", "")
                .replace("**", "")
                .replace("Top1 推荐", "")
                .trim();
            // Remove ID and price fragments.
            cleaned = cleaned
                .replaceAll("ID\\s*[:：]?\\s*\\d{3,10}", "")
                .replaceAll("￥\\s*\\d+(?:\\.\\d+)?", "")
                .replaceAll("¥\\s*\\d+(?:\\.\\d+)?", "")
                .replaceAll("\\|+", " ")
                .replaceAll("｜+", " ")
                .trim();
            if (cleaned.length() >= 4) {
                out.add(cleaned);
            }
            if (out.size() >= 6) {
                break;
            }
        }
        return out;
    }

    private List<ProductVO> extractProductCards(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        Matcher matcher = PRODUCT_ID_PATTERN.matcher(content);
        while (matcher.find()) {
            String idStr = matcher.group(1);
            if (idStr == null || idStr.isBlank()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(idStr));
            } catch (NumberFormatException ignored) {
                // skip invalid id token
            }
            if (ids.size() >= 6) {
                break;
            }
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        List<ProductVO> cards = new ArrayList<>();
        for (Long id : ids) {
            ProductVO p = shopProductService.getProductDetail(id);
            if (p != null) {
                cards.add(p);
            }
            if (cards.size() >= 4) {
                break;
            }
        }
        return cards;
    }

}
