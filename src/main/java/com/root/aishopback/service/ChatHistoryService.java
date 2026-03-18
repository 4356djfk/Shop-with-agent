package com.root.aishopback.service;

import com.root.aishopback.vo.ProductVO;

import java.util.List;
import java.util.Map;

public interface ChatHistoryService {
    void appendConversation(Long userId, String userMessage, String assistantMessage, List<ProductVO> products);

    List<Map<String, Object>> listRecent(Long userId, int limit);

    int deleteAllByUser(Long userId);
}
