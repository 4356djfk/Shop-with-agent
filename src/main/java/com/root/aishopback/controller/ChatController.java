package com.root.aishopback.controller;

import com.root.aishopback.common.ApiResponse;
import com.root.aishopback.dto.ChatReplyRequest;
import com.root.aishopback.service.AiShoppingAgentService;
import com.root.aishopback.service.ChatHistoryService;
import com.root.aishopback.vo.ProductVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.alibaba.fastjson2.JSON;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final AiShoppingAgentService aiShoppingAgentService;
    private final ChatHistoryService chatHistoryService;

    public ChatController(
        AiShoppingAgentService aiShoppingAgentService,
        ChatHistoryService chatHistoryService
    ) {
        this.aiShoppingAgentService = aiShoppingAgentService;
        this.chatHistoryService = chatHistoryService;
    }

    @GetMapping("/history")
    public ApiResponse<List<Map<String, Object>>> history(
        HttpServletRequest request,
        @RequestParam(value = "limit", required = false, defaultValue = "40") int limit
    ) {
        Long userId = resolveOptionalUserId(request);
        if (userId != null) {
            List<Map<String, Object>> history = chatHistoryService.listRecent(userId, limit);
            if (!history.isEmpty()) {
                return ApiResponse.ok(history);
            }
        }
        return ApiResponse.ok(List.of(greetingMessage()));
    }

    @PostMapping("/new")
    public ApiResponse<List<Map<String, Object>>> newConversation(HttpServletRequest request) {
        Long userId = resolveOptionalUserId(request);
        if (userId == null) {
            return ApiResponse.fail("请先登录");
        }
        aiShoppingAgentService.clearContext(userId);
        return ApiResponse.ok(List.of(greetingMessage()));
    }

    @DeleteMapping("/history")
    public ApiResponse<Map<String, Object>> deleteHistory(HttpServletRequest request) {
        Long userId = resolveOptionalUserId(request);
        if (userId == null) {
            return ApiResponse.fail("请先登录");
        }
        int deleted = chatHistoryService.deleteAllByUser(userId);
        aiShoppingAgentService.clearContext(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("deletedCount", deleted);
        return ApiResponse.ok(data);
    }

    @GetMapping("/health")
    public ApiResponse<AiShoppingAgentService.AgentHealth> health() {
        return ApiResponse.ok(aiShoppingAgentService.health());
    }

    @GetMapping("/llm-ping")
    public ApiResponse<AiShoppingAgentService.LlmPingResult> llmPingGet(
        @RequestParam(value = "message", required = false) String message
    ) {
        String prompt = message == null ? "" : message.trim();
        return ApiResponse.ok(aiShoppingAgentService.llmPing(prompt));
    }

    @PostMapping("/llm-ping")
    public ApiResponse<AiShoppingAgentService.LlmPingResult> llmPing(@RequestBody(required = false) ChatReplyRequest request) {
        String message = request == null || request.getMessage() == null ? "" : request.getMessage().trim();
        return ApiResponse.ok(aiShoppingAgentService.llmPing(message));
    }

    @PostMapping("/reply")
    public ApiResponse<Map<String, Object>> reply(
        @RequestBody ChatReplyRequest request,
        HttpServletRequest httpServletRequest
    ) {
        String message = request == null || request.getMessage() == null ? "" : request.getMessage().trim();
        Long userId = resolveOptionalUserId(httpServletRequest);

        AiShoppingAgentService.AgentReply agentReply = aiShoppingAgentService.reply(message, userId);
        String content = agentReply.content();
        List<ProductVO> cards = agentReply.products();

        chatHistoryService.appendConversation(userId, message, content, cards);

        Map<String, Object> data = new HashMap<>();
        data.put("content", content);
        data.put("products", cards);
        return ApiResponse.ok(data);
    }

    @PostMapping(value = "/reply/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter replyStream(
        @RequestBody ChatReplyRequest request,
        HttpServletRequest httpServletRequest
    ) {
        String message = request == null || request.getMessage() == null ? "" : request.getMessage().trim();
        Long userId = resolveOptionalUserId(httpServletRequest);
        SseEmitter emitter = new SseEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                emitter.send(SseEmitter.event().data(toStreamEvent("start", "ok")));
                emitter.send(SseEmitter.event().data(toStreamEvent("status", "正在检索商品...")));

                AiShoppingAgentService.AgentReply agentReply = aiShoppingAgentService.reply(message, userId);
                String content = agentReply.content() == null ? "" : agentReply.content();
                List<ProductVO> cards = agentReply.products() == null ? List.of() : agentReply.products();

                chatHistoryService.appendConversation(userId, message, content, cards);

                for (String chunk : splitForStream(content)) {
                    emitter.send(SseEmitter.event().data(toStreamEvent("delta", chunk)));
                    try {
                        Thread.sleep(18L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }

                Map<String, Object> payload = new HashMap<>();
                payload.put("content", content);
                payload.put("products", cards);
                emitter.send(SseEmitter.event().data(toStreamEvent("products", payload)));
                emitter.send(SseEmitter.event().data(toStreamEvent("done", "[DONE]")));
                emitter.complete();
            } catch (Exception ex) {
                try {
                    emitter.send(SseEmitter.event().data(toStreamEvent("error", ex.getMessage() == null ? "stream failed" : ex.getMessage())));
                } catch (IOException ignored) {
                    // no-op
                }
                emitter.completeWithError(ex);
            }
        });

        return emitter;
    }

    private Map<String, Object> greetingMessage() {
        Map<String, Object> greeting = new HashMap<>();
        greeting.put("role", "assistant");
        greeting.put("content", "你好，我是 AI 购物助手。告诉我你的预算、品类和用途，我会帮你找商品。你也可以直接说：加入购物车 商品ID 123 数量 2");
        greeting.put("products", List.of());
        return greeting;
    }

    private Long resolveOptionalUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return AuthController.resolveUserIdByToken(auth.substring(7).trim());
    }

    private List<String> splitForStream(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            buf.append(c);
            boolean punctuationBreak = c == '。' || c == '！' || c == '？' || c == '\n' || c == '.' || c == '!' || c == '?';
            if (punctuationBreak || buf.length() >= 8) {
                out.add(buf.toString());
                buf.setLength(0);
            }
        }
        if (buf.length() > 0) {
            out.add(buf.toString());
        }
        return out;
    }

    private String toStreamEvent(String type, Object data) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", type);
        event.put("data", data);
        return JSON.toJSONString(event);
    }
}
