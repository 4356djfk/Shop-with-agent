package com.root.aishopback.service;

import com.root.aishopback.vo.CartItemVO;

import java.util.List;

public interface ShopCartService {
    List<CartItemVO> listCart(long userId);
    CartItemVO addToCart(long userId, long productId, int quantity);
    void updateCart(long userId, Long cartId, Long productId, int quantity);
    void removeCart(long userId, Long cartId, Long productId);
}
