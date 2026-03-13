package com.root.aishopback.dto;

import java.util.List;

public class CreateOrderRequest {
    private String name;
    private String phone;
    private String address;
    private List<OrderItemRequest> items;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public List<OrderItemRequest> getItems() { return items; }
    public void setItems(List<OrderItemRequest> items) { this.items = items; }
}

