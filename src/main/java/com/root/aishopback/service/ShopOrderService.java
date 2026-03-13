package com.root.aishopback.service;

import com.root.aishopback.dto.CreateOrderRequest;
import com.root.aishopback.vo.OrderCreateVO;

import java.util.List;
import java.util.Map;

public interface ShopOrderService {
    OrderCreateVO createOrder(long userId, CreateOrderRequest request);
    Map<String, Object> getOrderByNo(long userId, String orderNo);
    Map<String, Object> payOrder(long userId, String orderNo);
    List<Map<String, Object>> listOrders(long userId);
}
