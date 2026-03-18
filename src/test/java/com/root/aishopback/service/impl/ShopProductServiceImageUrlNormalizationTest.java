package com.root.aishopback.service.impl;

import com.root.aishopback.entity.Product;
import com.root.aishopback.entity.ProductImage;
import com.root.aishopback.mapper.ProductImageMapper;
import com.root.aishopback.mapper.ProductMapper;
import com.root.aishopback.mapper.ProductTagMapper;
import com.root.aishopback.vo.ProductVO;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;

class ShopProductServiceImageUrlNormalizationTest {

    @Test
    void shouldNormalizeLegacyAmazonRenderingImageUrl() {
        ProductMapper productMapper = Mockito.mock(ProductMapper.class);
        ProductImageMapper productImageMapper = Mockito.mock(ProductImageMapper.class);
        ProductTagMapper productTagMapper = Mockito.mock(ProductTagMapper.class);
        ShopProductServiceImpl service = new ShopProductServiceImpl(productMapper, productImageMapper, productTagMapper);

        Product p = new Product();
        p.setId(818L);
        p.setName("Planner");
        p.setPrice(BigDecimal.valueOf(12.34));
        p.setIsActive(true);
        p.setImageUrl("https://m.media-amazon.com/images/W/IMAGERENDERING_521856-T2/images/I/61QpRM-e+sL._AC_SY300_SX300_.jpg");

        ProductImage pi = new ProductImage();
        pi.setProductId(818L);
        pi.setSortOrder(0);
        pi.setImageUrl("https://m.media-amazon.com/images/W/IMAGERENDERING_521856-T2/images/I/61QpRM-e+sL._AC_SY300_SX300_.jpg");

        Mockito.when(productMapper.selectList(any())).thenReturn(List.of(p));
        Mockito.when(productImageMapper.selectList(any())).thenReturn(List.of(pi));
        Mockito.when(productTagMapper.selectList(any())).thenReturn(List.of());

        List<ProductVO> list = service.listProducts(null, "Planner");
        assertFalse(list.isEmpty());
        String expected = "https://m.media-amazon.com/images/I/61QpRM-e+sL._AC_SY300_SX300_.jpg";
        assertEquals(expected, list.get(0).getImage());
        assertFalse(list.get(0).getImages().isEmpty());
        assertEquals(expected, list.get(0).getImages().get(0));
    }
}

