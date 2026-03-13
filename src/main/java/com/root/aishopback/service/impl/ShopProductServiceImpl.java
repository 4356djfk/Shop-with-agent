package com.root.aishopback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.root.aishopback.entity.Product;
import com.root.aishopback.entity.ProductImage;
import com.root.aishopback.entity.ProductTag;
import com.root.aishopback.mapper.ProductImageMapper;
import com.root.aishopback.mapper.ProductMapper;
import com.root.aishopback.mapper.ProductTagMapper;
import com.root.aishopback.service.ShopProductService;
import com.root.aishopback.vo.ProductVO;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ShopProductServiceImpl implements ShopProductService {

    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final ProductTagMapper productTagMapper;

    public ShopProductServiceImpl(ProductMapper productMapper, ProductImageMapper productImageMapper, ProductTagMapper productTagMapper) {
        this.productMapper = productMapper;
        this.productImageMapper = productImageMapper;
        this.productTagMapper = productTagMapper;
    }

    @Override
    @Cacheable(
        cacheNames = "products:list",
        key = "(#category == null || #category.isBlank() ? 'ALL' : #category) + '::' + (#keyword == null || #keyword.isBlank() ? 'ALL' : #keyword)"
    )
    public List<ProductVO> listProducts(String category, String keyword) {
        LambdaQueryWrapper<Product> query = new LambdaQueryWrapper<>();
        query.eq(Product::getIsActive, true);
        if (category != null && !category.isBlank()) {
            query.eq(Product::getCategory, category);
        }
        if (keyword != null && !keyword.isBlank()) {
            query.and(w -> w
                .like(Product::getName, keyword)
                .or().like(Product::getCategory, keyword)
                .or().like(Product::getBrand, keyword)
                .or().like(Product::getDescription, keyword)
            );
        }
        query.orderByDesc(Product::getSales).orderByDesc(Product::getRating);

        List<Product> products = productMapper.selectList(query);
        if (products.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> ids = products.stream().map(Product::getId).filter(Objects::nonNull).toList();
        Map<Long, List<String>> imageMap = buildImageMap(ids);
        Map<Long, List<String>> tagMap = buildTagMap(ids);

        return products.stream()
            .map(product -> toProductVO(product, imageMap.get(product.getId()), tagMap.get(product.getId())))
            .toList();
    }

    @Override
    @Cacheable(cacheNames = "products:detail", key = "#id")
    public ProductVO getProductDetail(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            return null;
        }
        Map<Long, List<String>> imageMap = buildImageMap(List.of(id));
        Map<Long, List<String>> tagMap = buildTagMap(List.of(id));
        return toProductVO(product, imageMap.get(id), tagMap.get(id));
    }

    private Map<Long, List<String>> buildImageMap(List<Long> ids) {
        if (ids.isEmpty()) return Collections.emptyMap();
        LambdaQueryWrapper<ProductImage> imageQuery = new LambdaQueryWrapper<>();
        imageQuery.in(ProductImage::getProductId, ids).orderByAsc(ProductImage::getSortOrder);
        List<ProductImage> images = productImageMapper.selectList(imageQuery);
        Map<Long, List<String>> map = new LinkedHashMap<>();
        for (ProductImage image : images) {
            map.computeIfAbsent(image.getProductId(), key -> new ArrayList<>()).add(image.getImageUrl());
        }
        return map;
    }

    private Map<Long, List<String>> buildTagMap(List<Long> ids) {
        if (ids.isEmpty()) return Collections.emptyMap();
        LambdaQueryWrapper<ProductTag> tagQuery = new LambdaQueryWrapper<>();
        tagQuery.in(ProductTag::getProductId, ids);
        List<ProductTag> tags = productTagMapper.selectList(tagQuery);
        return tags.stream().collect(Collectors.groupingBy(
            ProductTag::getProductId,
            LinkedHashMap::new,
            Collectors.mapping(ProductTag::getTagName, Collectors.toList())
        ));
    }

    private ProductVO toProductVO(Product product, List<String> images, List<String> tags) {
        ProductVO vo = new ProductVO();
        vo.setId(product.getId());
        vo.setSku(product.getSku());
        vo.setName(product.getName());
        vo.setPrice(product.getPrice());
        vo.setOriginalPrice(product.getOriginalPrice());
        vo.setCategory(product.getCategory());
        vo.setSales(product.getSales());
        vo.setRating(product.getRating());
        vo.setStock(product.getStock());
        vo.setDescription(product.getDescription());
        vo.setBrand(product.getBrand());
        vo.setStore(product.getStoreName());
        vo.setCurrency(product.getCurrency());
        vo.setAvailability(product.getAvailability());
        vo.setSourceUrl(product.getSourceUrl());
        vo.setMpn(product.getMpn());
        vo.setGtin(product.getGtin());
        List<String> normalizedImages = images == null ? new ArrayList<>() : new ArrayList<>(images);
        if (normalizedImages.isEmpty() && product.getImageUrl() != null) {
            normalizedImages.add(product.getImageUrl());
        }
        vo.setImages(normalizedImages);
        vo.setImage(product.getImageUrl() != null ? product.getImageUrl() : (normalizedImages.isEmpty() ? "" : normalizedImages.get(0)));
        vo.setTags(tags == null ? Collections.emptyList() : tags);
        return vo;
    }
}
