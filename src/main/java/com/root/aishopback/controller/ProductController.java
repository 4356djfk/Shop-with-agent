package com.root.aishopback.controller;

import com.root.aishopback.common.ApiResponse;
import com.root.aishopback.service.ShopProductService;
import com.root.aishopback.vo.ProductVO;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*")
public class ProductController {
    private final ShopProductService shopProductService;

    public ProductController(ShopProductService shopProductService) {
        this.shopProductService = shopProductService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String sortBy,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "24") Integer size
    ) {
        return ApiResponse.ok(shopProductService.listProducts(category, keyword, sortBy, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductVO> detail(@PathVariable Long id) {
        ProductVO detail = shopProductService.getProductDetail(id);
        return ApiResponse.ok(detail);
    }

    @GetMapping("/search")
    public ApiResponse<Map<String, Object>> search(@RequestParam String keyword) {
        return ApiResponse.ok(shopProductService.listProducts(null, keyword, null, 1, 24));
    }

    @GetMapping("/categories")
    public ApiResponse<Map<String, Object>> categories() {
        Map<String, Object> data = new HashMap<>();
        data.put("list", shopProductService.listCategoryOptions());
        return ApiResponse.ok(data);
    }
}

