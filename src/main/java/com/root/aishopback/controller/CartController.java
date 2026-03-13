package com.root.aishopback.controller;

import com.root.aishopback.common.ApiResponse;
import com.root.aishopback.dto.CartAddRequest;
import com.root.aishopback.dto.CartUpdateRequest;
import com.root.aishopback.service.ShopCartService;
import com.root.aishopback.util.UserContextUtil;
import com.root.aishopback.vo.CartItemVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
@CrossOrigin(origins = "*")
public class CartController {

    private final ShopCartService shopCartService;

    public CartController(ShopCartService shopCartService) {
        this.shopCartService = shopCartService;
    }

    @GetMapping
    public ApiResponse<List<CartItemVO>> list(HttpServletRequest request) {
        long userId = UserContextUtil.resolveUserId(request);
        return ApiResponse.ok(shopCartService.listCart(userId));
    }

    @PostMapping
    public ApiResponse<CartItemVO> add(@RequestBody CartAddRequest request, HttpServletRequest httpServletRequest) {
        long userId = UserContextUtil.resolveUserId(httpServletRequest);
        CartItemVO item = shopCartService.addToCart(userId, request.getProductId(), request.getQuantity() == null ? 1 : request.getQuantity());
        return ApiResponse.ok(item);
    }

    @PutMapping
    public ApiResponse<Void> update(@RequestBody CartUpdateRequest request, HttpServletRequest httpServletRequest) {
        if (request.getCartId() == null && request.getProductId() == null) {
            throw new IllegalArgumentException("cartId or productId is required");
        }
        long userId = UserContextUtil.resolveUserId(httpServletRequest);
        shopCartService.updateCart(
            userId,
            request.getCartId(),
            request.getProductId(),
            request.getQuantity() == null ? 1 : request.getQuantity()
        );
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{cartId}")
    public ApiResponse<Void> remove(@PathVariable Long cartId, HttpServletRequest httpServletRequest) {
        long userId = UserContextUtil.resolveUserId(httpServletRequest);
        shopCartService.removeCart(userId, cartId, null);
        return ApiResponse.ok(null);
    }

    @DeleteMapping
    public ApiResponse<Void> removeByQuery(
        @RequestParam(required = false) Long cartId,
        @RequestParam(required = false) Long productId,
        HttpServletRequest httpServletRequest
    ) {
        long userId = UserContextUtil.resolveUserId(httpServletRequest);
        shopCartService.removeCart(userId, cartId, productId);
        return ApiResponse.ok(null);
    }
}
