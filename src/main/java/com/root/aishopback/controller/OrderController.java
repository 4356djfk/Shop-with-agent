package com.root.aishopback.controller;

import com.root.aishopback.common.ApiResponse;
import com.root.aishopback.dto.CreateOrderRequest;
import com.root.aishopback.service.ShopOrderService;
import com.root.aishopback.util.UserContextUtil;
import com.root.aishopback.vo.OrderCreateVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    private final ShopOrderService shopOrderService;

    public OrderController(ShopOrderService shopOrderService) {
        this.shopOrderService = shopOrderService;
    }

    @PostMapping
    public ApiResponse<OrderCreateVO> create(@RequestBody CreateOrderRequest request, HttpServletRequest httpServletRequest) {
        long userId = UserContextUtil.resolveUserId(httpServletRequest);
        return ApiResponse.ok(shopOrderService.createOrder(userId, request));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<Map<String, Object>> getOrder(@PathVariable String orderId, HttpServletRequest httpServletRequest) {
        long userId = UserContextUtil.resolveUserId(httpServletRequest);
        return ApiResponse.ok(shopOrderService.getOrderByNo(userId, orderId));
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listOrders(HttpServletRequest httpServletRequest) {
        long userId = UserContextUtil.resolveUserId(httpServletRequest);
        return ApiResponse.ok(shopOrderService.listOrders(userId));
    }

    @PostMapping("/{orderId}/pay")
    public ApiResponse<Map<String, Object>> payOrder(@PathVariable String orderId, HttpServletRequest httpServletRequest) {
        long userId = UserContextUtil.resolveUserId(httpServletRequest);
        return ApiResponse.ok(shopOrderService.payOrder(userId, orderId));
    }
}
