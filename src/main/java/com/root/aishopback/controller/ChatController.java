package com.root.aishopback.controller;

import com.root.aishopback.common.ApiResponse;
import com.root.aishopback.dto.ChatReplyRequest;
import com.root.aishopback.service.ShopProductService;
import com.root.aishopback.vo.ProductVO;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final ShopProductService shopProductService;

    public ChatController(ShopProductService shopProductService) {
        this.shopProductService = shopProductService;
    }

    @GetMapping("/history")
    public ApiResponse<List<Map<String, Object>>> history() {
        Map<String, Object> greeting = new HashMap<>();
        greeting.put("role", "assistant");
        greeting.put("content", "你好，我是 AI 购物助手。告诉我你的需求，我会从商品库里推荐。");
        greeting.put("products", List.of());
        return ApiResponse.ok(List.of(greeting));
    }

    @PostMapping("/reply")
    public ApiResponse<Map<String, Object>> reply(@RequestBody ChatReplyRequest request) {
        String message = request == null || request.getMessage() == null ? "" : request.getMessage().trim();
        List<ProductVO> matched = shopProductService.listProducts(null, message);
        List<ProductVO> cards = matched.stream().limit(4).toList();

        String content;
        if (message.isBlank()) {
            content = "你可以告诉我预算、品类或具体品牌，我会帮你筛选商品。";
        } else if (cards.isEmpty()) {
            content = "暂时没有找到完全匹配的商品，我先给你推荐一些热门商品。";
            cards = shopProductService.listProducts(null, null).stream().limit(4).toList();
        } else {
            content = "根据你的需求，我在商品库里找到以下推荐：";
        }

        Map<String, Object> data = new HashMap<>();
        data.put("content", content);
        data.put("products", cards);
        return ApiResponse.ok(data);
    }
}

