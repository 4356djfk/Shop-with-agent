package com.root.aishopback.service;

import com.root.aishopback.vo.ProductVO;

import java.util.Map;
import java.util.List;

public interface ShopProductService {
    List<ProductVO> listProducts(String category, String keyword);
    Map<String, Object> listProducts(String category, String keyword, String sortBy, int page, int size);
    List<Map<String, String>> listCategoryOptions();
    ProductVO getProductDetail(Long id);
}

