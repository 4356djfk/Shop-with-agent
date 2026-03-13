package com.root.aishopback.service;

import com.root.aishopback.vo.ProductVO;

import java.util.List;

public interface ShopProductService {
    List<ProductVO> listProducts(String category, String keyword);
    ProductVO getProductDetail(Long id);
}

