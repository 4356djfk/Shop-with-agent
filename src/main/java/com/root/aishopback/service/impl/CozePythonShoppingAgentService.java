package com.root.aishopback.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.root.aishopback.service.AiShoppingAgentService;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;

@Service
@Primary
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "coze")
public class CozePythonShoppingAgentService implements AiShoppingAgentService {

    private static final long GUEST_USER_KEY = 0L;

    private final HttpClient httpClient;
    private final String cozeBaseUrl;
    private final String chatPath;
    private final String chatModel;
    private final String apiKey;
    private final double temperature;
    private final int maxHistoryTurns;
    private final long timeoutMillis;
    private final int retryMaxAttempts;
    private final long retryBaseBackoffMillis;

    private final Map<Long, Deque<Map<String, String>>> conversations = new ConcurrentHashMap<>();
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong failures = new AtomicLong(0);
    private volatile String lastError = "";

    public CozePythonShoppingAgentService(
        @Value("${app.ai.coze.base-url:http://127.0.0.1:5000}") String cozeBaseUrl,
        @Value("${app.ai.coze.chat-path:/run}") String chatPath,
        @Value("${app.ai.coze.chat-model:coze-agent}") String chatModel,
        @Value("${app.ai.coze.api-key:}") String apiKey,
        @Value("${app.ai.coze.temperature:0.4}") double temperature,
        @Value("${app.ai.coze.max-history-turns:10}") int maxHistoryTurns,
        @Value("${app.ai.coze.timeout-millis:30000}") long timeoutMillis,
        @Value("${app.ai.coze.retry-max-attempts:3}") int retryMaxAttempts,
        @Value("${app.ai.coze.retry-base-backoff-millis:800}") long retryBaseBackoffMillis
    ) {
        this.cozeBaseUrl = trimTrailingSlash(cozeBaseUrl);
        this.chatPath = chatPath.startsWith("/") ? chatPath : "/" + chatPath;
        this.chatModel = chatModel;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.temperature = temperature;
        this.maxHistoryTurns = Math.max(1, maxHistoryTurns);
        this.timeoutMillis = Math.max(1000L, timeoutMillis);
        this.retryMaxAttempts = Math.max(1, retryMaxAttempts);
        this.retryBaseBackoffMillis = Math.max(100L, retryBaseBackoffMillis);
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
            return new AgentReply(content, List.of());
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
            for (Map<String, String> msg : history) {
                if (msg == null) {
                    continue;
                }
                String role = msg.get("role");
                String content = msg.get("content");
                if (role == null || content == null || content.isBlank()) {
                    continue;
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
        return prompt == null ? "" : prompt.trim();
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

}
