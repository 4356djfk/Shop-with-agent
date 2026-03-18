package com.root.aishopback.service;

import com.root.aishopback.vo.ProductVO;

import java.util.List;
import java.util.Map;

public interface AiShoppingAgentService {

    AgentReply reply(String message, Long userId);
    AgentHealth health();
    LlmPingResult llmPing(String message);
    void clearContext(Long userId);

    record AgentReply(String content, List<ProductVO> products) {}
    record AgentHealth(
        boolean aiEnabled,
        boolean chatModelEnabled,
        boolean embeddingEnabled,
        String chatBaseUrl,
        String chatModelName,
        String chatApiKeyHint,
        String embeddingStoreType,
        boolean milvusConfigured,
        int indexedProductCount,
        String lastError,
        long totalRequests,
        long emptyResults,
        double emptyRate,
        long clarifyCount,
        long handoffCount,
        double avgReturnedProducts,
        long llmChatCallsTotal,
        long llmChatCallErrors,
        Map<String, Long> llmChatCallsByScene,
        Map<String, Long> actionCounts,
        Map<String, Long> scopeCounts,
        Map<String, Long> intentCounts,
        int catalogTopicTokenCount,
        List<String> topCatalogTopics
    ) {}

    record LlmPingResult(
        boolean success,
        String modelReply,
        String error,
        long llmChatCallsBefore,
        long llmChatCallsAfter,
        long llmChatCallsDelta,
        long llmChatErrorsBefore,
        long llmChatErrorsAfter,
        long llmChatErrorsDelta,
        Map<String, Long> llmChatCallsByScene
    ) {}
}
