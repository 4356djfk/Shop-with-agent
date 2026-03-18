package com.root.aishopback.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.root.aishopback.entity.ChatMessage;
import com.root.aishopback.mapper.ChatMessageMapper;
import com.root.aishopback.service.ChatHistoryService;
import com.root.aishopback.vo.ProductVO;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatHistoryServiceImpl implements ChatHistoryService {

    private static final int MAX_LIMIT = 100;

    private final ChatMessageMapper chatMessageMapper;
    private final JdbcTemplate jdbcTemplate;

    public ChatHistoryServiceImpl(ChatMessageMapper chatMessageMapper, JdbcTemplate jdbcTemplate) {
        this.chatMessageMapper = chatMessageMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS chat_message (
                id BIGSERIAL PRIMARY KEY,
                user_id BIGINT NOT NULL,
                role VARCHAR(16) NOT NULL,
                content TEXT NOT NULL,
                products_json TEXT,
                created_at TIMESTAMP NOT NULL DEFAULT NOW()
            );
            """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_chat_message_user_created ON chat_message(user_id, created_at DESC);");
    }

    @Override
    public void appendConversation(Long userId, String userMessage, String assistantMessage, List<ProductVO> products) {
        if (userId == null) {
            return;
        }
        if (userMessage != null && !userMessage.isBlank()) {
            insert(userId, "user", userMessage, null);
        }
        if (assistantMessage != null && !assistantMessage.isBlank()) {
            String productsJson = products == null || products.isEmpty() ? "[]" : JSON.toJSONString(products);
            insert(userId, "assistant", assistantMessage, productsJson);
        }
    }

    @Override
    public List<Map<String, Object>> listRecent(Long userId, int limit) {
        if (userId == null) {
            return List.of();
        }
        int n = Math.max(1, Math.min(MAX_LIMIT, limit));
        List<ChatMessage> rows = chatMessageMapper.selectList(
            new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getUserId, userId)
                .orderByDesc(ChatMessage::getCreatedAt)
                .last("LIMIT " + n)
        );
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (ChatMessage row : rows) {
            Map<String, Object> item = new HashMap<>();
            item.put("role", row.getRole());
            item.put("content", row.getContent());
            item.put("products", parseProducts(row.getProductsJson()));
            item.put("createdAt", row.getCreatedAt());
            out.add(item);
        }
        Collections.reverse(out);
        return out;
    }

    @Override
    public int deleteAllByUser(Long userId) {
        if (userId == null) {
            return 0;
        }
        return chatMessageMapper.delete(
            new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getUserId, userId)
        );
    }

    private void insert(Long userId, String role, String content, String productsJson) {
        ChatMessage m = new ChatMessage();
        m.setUserId(userId);
        m.setRole(role);
        m.setContent(content);
        m.setProductsJson(productsJson == null ? "[]" : productsJson);
        m.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(m);
    }

    private List<Map<String, Object>> parseProducts(String productsJson) {
        if (productsJson == null || productsJson.isBlank()) {
            return List.of();
        }
        try {
            return JSON.parseObject(productsJson, List.class);
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
