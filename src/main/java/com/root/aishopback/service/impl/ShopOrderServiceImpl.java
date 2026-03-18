package com.root.aishopback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.root.aishopback.dto.CreateOrderRequest;
import com.root.aishopback.dto.OrderItemRequest;
import com.root.aishopback.entity.OrderItem;
import com.root.aishopback.entity.Product;
import com.root.aishopback.entity.ShopOrder;
import com.root.aishopback.mapper.AppUserMapper;
import com.root.aishopback.mapper.CartItemMapper;
import com.root.aishopback.mapper.OrderItemMapper;
import com.root.aishopback.mapper.ProductMapper;
import com.root.aishopback.mapper.ShopOrderMapper;
import com.root.aishopback.service.ShopOrderService;
import com.root.aishopback.vo.CartItemVO;
import com.root.aishopback.vo.OrderCreateVO;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ShopOrderServiceImpl implements ShopOrderService {

    private final ShopOrderMapper shopOrderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final AppUserMapper appUserMapper;
    private final CartItemMapper cartItemMapper;
    private final CacheManager cacheManager;

    public ShopOrderServiceImpl(
        ShopOrderMapper shopOrderMapper,
        OrderItemMapper orderItemMapper,
        ProductMapper productMapper,
        AppUserMapper appUserMapper,
        CartItemMapper cartItemMapper,
        CacheManager cacheManager
    ) {
        this.shopOrderMapper = shopOrderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.appUserMapper = appUserMapper;
        this.cartItemMapper = cartItemMapper;
        this.cacheManager = cacheManager;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderCreateVO createOrder(long userId, CreateOrderRequest request) {
        long effectiveUserId = normalizeUserId(userId);
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("order items cannot be empty");
        }
        String consigneeName = firstNonBlank(request.getName(), request.getConsignee());
        if (isBlank(consigneeName) || isBlank(request.getPhone()) || isBlank(request.getAddress())) {
            throw new IllegalArgumentException("shipping info is incomplete");
        }

        String orderNo = "ORDER" + System.currentTimeMillis();
        ShopOrder order = new ShopOrder();
        order.setOrderNo(orderNo);
        order.setUserId(effectiveUserId);
        order.setOrderStatus("pending_payment");
        order.setPaymentStatus("unpaid");
        order.setConsigneeName(consigneeName);
        order.setConsigneePhone(request.getPhone());
        order.setConsigneeAddress(request.getAddress());
        order.setShippingAmount(BigDecimal.ZERO);

        BigDecimal total = BigDecimal.ZERO;
        List<CartItemVO> responseItems = new ArrayList<>();
        for (OrderItemRequest itemReq : request.getItems()) {
            Long productId = itemReq.getProductId();
            if (productId == null && itemReq.getId() != null) {
                productId = itemReq.getId();
            }
            if (productId == null) {
                throw new IllegalArgumentException("productId is required");
            }
            Product product = productMapper.selectById(productId);
            if (product == null) {
                throw new IllegalArgumentException("product not found: " + productId);
            }
            int qty = Math.max(1, itemReq.getQuantity() == null ? 1 : itemReq.getQuantity());
            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(qty));

            CartItemVO vo = new CartItemVO();
            vo.setProductId(productId);
            vo.setName(product.getName());
            vo.setPrice(product.getPrice());
            vo.setImage(product.getImageUrl());
            vo.setCategory(product.getCategory());
            vo.setStock(product.getStock());
            vo.setQuantity(qty);
            vo.setId(itemReq.getCartId());
            responseItems.add(vo);
            total = total.add(lineTotal);
        }
        order.setTotalAmount(total);
        order.setPayAmount(total);

        shopOrderMapper.insert(order);

        for (CartItemVO item : responseItems) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setProductId(item.getProductId());
            orderItem.setProductName(item.getName());
            orderItem.setProductImage(item.getImage());
            orderItem.setUnitPrice(item.getPrice());
            orderItem.setQuantity(item.getQuantity());
            orderItem.setTotalAmount(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            orderItemMapper.insert(orderItem);
        }

        OrderCreateVO vo = new OrderCreateVO();
        vo.setOrderId(orderNo);
        vo.setTotalAmount(order.getPayAmount());
        vo.setItems(responseItems);
        return vo;
    }

    @Override
    public Map<String, Object> getOrderByNo(long userId, String orderNo) {
        long effectiveUserId = normalizeUserId(userId);
        LambdaQueryWrapper<ShopOrder> query = new LambdaQueryWrapper<>();
        query.eq(ShopOrder::getOrderNo, orderNo).eq(ShopOrder::getUserId, effectiveUserId).last("LIMIT 1");
        ShopOrder order = shopOrderMapper.selectOne(query);
        if (order == null) return null;

        List<OrderItem> items = orderItemMapper.selectList(
            new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId())
        );

        Map<String, Object> data = new HashMap<>();
        data.put("orderId", order.getOrderNo());
        data.put("orderStatus", order.getOrderStatus());
        data.put("paymentStatus", order.getPaymentStatus());
        data.put("totalAmount", order.getTotalAmount());
        data.put("payAmount", order.getPayAmount());
        data.put("items", items);
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> payOrder(long userId, String orderNo) {
        long effectiveUserId = normalizeUserId(userId);
        LambdaQueryWrapper<ShopOrder> query = new LambdaQueryWrapper<>();
        query.eq(ShopOrder::getOrderNo, orderNo).eq(ShopOrder::getUserId, effectiveUserId).last("LIMIT 1");
        ShopOrder order = shopOrderMapper.selectOne(query);
        if (order == null) {
            throw new IllegalArgumentException("order not found");
        }

        if ("paid".equalsIgnoreCase(order.getPaymentStatus())) {
            return Map.of(
                "orderId", order.getOrderNo(),
                "orderStatus", order.getOrderStatus(),
                "paymentStatus", order.getPaymentStatus(),
                "payAmount", order.getPayAmount()
            );
        }

        List<OrderItem> items = orderItemMapper.selectList(
            new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId())
        );
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("order items not found");
        }

        for (OrderItem item : items) {
            int qty = Math.max(1, item.getQuantity() == null ? 1 : item.getQuantity());
            int affected = productMapper.decreaseStock(item.getProductId(), qty);
            if (affected <= 0) {
                throw new IllegalArgumentException("库存不足: " + item.getProductName());
            }
        }

        List<Long> productIds = items.stream()
            .map(OrderItem::getProductId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (!productIds.isEmpty()) {
            cartItemMapper.delete(
                new LambdaQueryWrapper<com.root.aishopback.entity.CartItem>()
                    .eq(com.root.aishopback.entity.CartItem::getUserId, effectiveUserId)
                    .in(com.root.aishopback.entity.CartItem::getProductId, productIds)
            );
        }

        order.setOrderStatus("paid");
        order.setPaymentStatus("paid");
        shopOrderMapper.updateById(order);
        clearProductCaches();

        return Map.of(
            "orderId", order.getOrderNo(),
            "orderStatus", order.getOrderStatus(),
            "paymentStatus", order.getPaymentStatus(),
            "payAmount", order.getPayAmount()
        );
    }

    @Override
    public List<Map<String, Object>> listOrders(long userId) {
        long effectiveUserId = normalizeUserId(userId);
        List<ShopOrder> orders = shopOrderMapper.selectList(
            new LambdaQueryWrapper<ShopOrder>()
                .eq(ShopOrder::getUserId, effectiveUserId)
                .orderByDesc(ShopOrder::getId)
        );

        List<Map<String, Object>> result = new ArrayList<>();
        for (ShopOrder order : orders) {
            List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                    .eq(OrderItem::getOrderId, order.getId())
                    .orderByAsc(OrderItem::getId)
            );
            int totalQuantity = items.stream()
                .map(OrderItem::getQuantity)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum);

            Map<String, Object> data = new HashMap<>();
            data.put("orderId", order.getOrderNo());
            data.put("orderStatus", order.getOrderStatus());
            data.put("paymentStatus", order.getPaymentStatus());
            data.put("payAmount", order.getPayAmount());
            data.put("totalAmount", order.getTotalAmount());
            data.put("itemCount", items.size());
            data.put("quantityCount", totalQuantity);
            data.put("items", items);
            result.add(data);
        }
        return result;
    }

    private void clearProductCaches() {
        Cache listCache = cacheManager.getCache("products:list");
        if (listCache != null) {
            listCache.clear();
        }
        Cache detailCache = cacheManager.getCache("products:detail");
        if (detailCache != null) {
            detailCache.clear();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private long normalizeUserId(long userId) {
        if (appUserMapper.selectById(userId) == null) {
            throw new SecurityException("用户不存在，请重新登录");
        }
        return userId;
    }
}
