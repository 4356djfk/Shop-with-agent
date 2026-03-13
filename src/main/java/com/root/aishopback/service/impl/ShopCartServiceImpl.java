package com.root.aishopback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.root.aishopback.entity.CartItem;
import com.root.aishopback.entity.Product;
import com.root.aishopback.mapper.AppUserMapper;
import com.root.aishopback.mapper.CartItemMapper;
import com.root.aishopback.mapper.ProductMapper;
import com.root.aishopback.service.ShopCartService;
import com.root.aishopback.vo.CartItemVO;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ShopCartServiceImpl implements ShopCartService {
    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;
    private final AppUserMapper appUserMapper;

    public ShopCartServiceImpl(CartItemMapper cartItemMapper, ProductMapper productMapper, AppUserMapper appUserMapper) {
        this.cartItemMapper = cartItemMapper;
        this.productMapper = productMapper;
        this.appUserMapper = appUserMapper;
    }

    @Override
    public List<CartItemVO> listCart(long userId) {
        long effectiveUserId = normalizeUserId(userId);
        LambdaQueryWrapper<CartItem> query = new LambdaQueryWrapper<>();
        query.eq(CartItem::getUserId, effectiveUserId).orderByDesc(CartItem::getId);
        List<CartItem> cartItems = cartItemMapper.selectList(query);
        return toCartVoList(cartItems);
    }

    @Override
    public CartItemVO addToCart(long userId, long productId, int quantity) {
        long effectiveUserId = normalizeUserId(userId);
        int qty = Math.max(1, quantity);
        LambdaQueryWrapper<CartItem> query = new LambdaQueryWrapper<>();
        query.eq(CartItem::getUserId, effectiveUserId).eq(CartItem::getProductId, productId).last("LIMIT 1");
        CartItem existing = cartItemMapper.selectOne(query);
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + qty);
            cartItemMapper.updateById(existing);
        } else {
            existing = new CartItem();
            existing.setUserId(effectiveUserId);
            existing.setProductId(productId);
            existing.setQuantity(qty);
            existing.setSelected(true);
            cartItemMapper.insert(existing);
        }
        return toCartVoList(List.of(existing)).stream().findFirst().orElse(null);
    }

    @Override
    public void updateCart(long userId, Long cartId, Long productId, int quantity) {
        long effectiveUserId = normalizeUserId(userId);
        CartItem existing = null;
        if (cartId != null) {
            LambdaQueryWrapper<CartItem> query = new LambdaQueryWrapper<>();
            query.eq(CartItem::getId, cartId).eq(CartItem::getUserId, effectiveUserId).last("LIMIT 1");
            existing = cartItemMapper.selectOne(query);
        }
        // Fallback path for JS bigint precision issues: locate by user + product.
        if (existing == null && productId != null) {
            LambdaQueryWrapper<CartItem> queryByProduct = new LambdaQueryWrapper<>();
            queryByProduct.eq(CartItem::getUserId, effectiveUserId)
                .eq(CartItem::getProductId, productId)
                .last("LIMIT 1");
            existing = cartItemMapper.selectOne(queryByProduct);
        }
        if (existing == null) {
            throw new IllegalArgumentException("Cart item not found");
        }
        existing.setQuantity(Math.max(1, quantity));
        int updated = cartItemMapper.updateById(existing);
        if (updated <= 0) {
            throw new IllegalArgumentException("Failed to update cart item");
        }
    }

    @Override
    public void removeCart(long userId, Long cartId, Long productId) {
        long effectiveUserId = normalizeUserId(userId);
        if (cartId == null && productId == null) {
            throw new IllegalArgumentException("cartId or productId is required");
        }

        int deleted = 0;
        if (cartId != null) {
            LambdaQueryWrapper<CartItem> query = new LambdaQueryWrapper<>();
            query.eq(CartItem::getId, cartId).eq(CartItem::getUserId, effectiveUserId);
            deleted = cartItemMapper.delete(query);
        }

        if (deleted <= 0 && productId != null) {
            LambdaQueryWrapper<CartItem> queryByProduct = new LambdaQueryWrapper<>();
            queryByProduct.eq(CartItem::getUserId, effectiveUserId).eq(CartItem::getProductId, productId);
            deleted = cartItemMapper.delete(queryByProduct);
        }

        if (deleted <= 0) {
            throw new IllegalArgumentException("Cart item not found");
        }
    }

    private long normalizeUserId(long userId) {
        if (appUserMapper.selectById(userId) == null) {
            throw new SecurityException("用户不存在，请重新登录");
        }
        return userId;
    }

    private List<CartItemVO> toCartVoList(List<CartItem> cartItems) {
        if (cartItems.isEmpty()) return Collections.emptyList();
        List<Long> productIds = cartItems.stream().map(CartItem::getProductId).filter(Objects::nonNull).distinct().toList();
        List<Product> products = productIds.isEmpty()
            ? Collections.emptyList()
            : productMapper.selectList(new LambdaQueryWrapper<Product>().in(Product::getId, productIds));
        Map<Long, Product> productMap = new HashMap<>();
        for (Product product : products) {
            productMap.put(product.getId(), product);
        }
        return cartItems.stream().map(item -> {
            Product product = productMap.get(item.getProductId());
            CartItemVO vo = new CartItemVO();
            vo.setId(item.getId());
            vo.setProductId(item.getProductId());
            vo.setQuantity(item.getQuantity());
            if (product != null) {
                vo.setName(product.getName());
                vo.setPrice(product.getPrice());
                vo.setImage(product.getImageUrl());
                vo.setCategory(product.getCategory());
                vo.setStock(product.getStock());
            } else {
                vo.setName("Unknown Product");
            }
            return vo;
        }).toList();
    }
}
