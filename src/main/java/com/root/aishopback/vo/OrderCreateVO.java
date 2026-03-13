package com.root.aishopback.vo;

import java.math.BigDecimal;
import java.util.List;

public class OrderCreateVO {
    private String orderId;
    private BigDecimal totalAmount;
    private List<CartItemVO> items;

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public List<CartItemVO> getItems() { return items; }
    public void setItems(List<CartItemVO> items) { this.items = items; }
}

