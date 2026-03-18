package com.root.aishopback.service.impl;

import com.root.aishopback.mapper.SearchAliasLexiconMapper;
import com.root.aishopback.mapper.QueryNormalizationLexiconMapper;
import com.root.aishopback.service.AiShoppingAgentService;
import com.root.aishopback.service.ShopCartService;
import com.root.aishopback.service.ShopOrderService;
import com.root.aishopback.service.ShopProductService;
import com.root.aishopback.vo.ProductVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;

class LangChain4jShoppingAgentLampFollowUpRegressionTest {

    private LangChain4jShoppingAgentService service;

    @BeforeEach
    void setUp() {
        ShopProductService shopProductService = Mockito.mock(ShopProductService.class);
        ShopCartService shopCartService = Mockito.mock(ShopCartService.class);
        ShopOrderService shopOrderService = Mockito.mock(ShopOrderService.class);
        SearchAliasLexiconMapper searchAliasLexiconMapper = Mockito.mock(SearchAliasLexiconMapper.class);
        QueryNormalizationLexiconMapper queryNormalizationLexiconMapper = Mockito.mock(QueryNormalizationLexiconMapper.class);
        ElasticsearchProductSearchService es = new ElasticsearchProductSearchService(false, "localhost", 9200, "products_search", "", "");

        List<ProductVO> products = fixtureProducts();
        Map<Long, ProductVO> byId = products.stream().collect(Collectors.toMap(ProductVO::getId, Function.identity()));

        Mockito.when(shopProductService.listProducts(nullable(String.class), nullable(String.class))).thenReturn(products);
        Mockito.when(shopProductService.getProductDetail(anyLong())).thenAnswer(inv -> byId.get(inv.getArgument(0)));
        Mockito.when(shopCartService.listCart(anyLong())).thenReturn(List.of());
        Mockito.when(shopOrderService.listOrders(anyLong())).thenReturn(List.of());

        service = new LangChain4jShoppingAgentService(
            shopProductService,
            shopCartService,
            shopOrderService,
            es,
            searchAliasLexiconMapper,
            queryNormalizationLexiconMapper,
            false,
            false,
            true,
            false,
            4,
            "",
            "",
            "gpt-4o-mini",
            "text-embedding-3-small",
            "",
            "",
            0.45,
            false,
            "localhost",
            19530,
            "products_rag",
            1536,
            false
        );
    }

    @Test
    void lampFollowUpShouldStayInLampTopicOrReturnEmpty() {
        AiShoppingAgentService.AgentReply first = service.reply("\u63a8\u8350\u706f", 6101L);
        assertFalse(first.products().isEmpty(), first.content());
        assertTrue(first.products().stream().allMatch(this::isLampLike), first.content());

        AiShoppingAgentService.AgentReply second = service.reply("\u8fd8\u6709\u5417", 6101L);
        if (!second.products().isEmpty()) {
            assertTrue(second.products().stream().allMatch(this::isLampLike), second.content());
        } else {
            assertTrue(second.content().contains("\u540c\u7c7b"), second.content());
        }
    }

    private boolean isLampLike(ProductVO p) {
        String text = ((p.getName() == null ? "" : p.getName()) + " " + (p.getCategory() == null ? "" : p.getCategory())).toLowerCase();
        return text.contains("lamp") || text.contains("light") || text.contains("lighting") || text.contains("\u706f");
    }

    private List<ProductVO> fixtureProducts() {
        return List.of(
            product(101L, "\u53ef\u8c03\u8282\u684c\u706f Desk Lamp", "Home > \u7167\u660e Lighting", 29.9, 4.5, 1200),
            product(102L, "\u5e8a\u5934\u5c0f\u591c\u706f Bedside Light", "Home > \u7167\u660e Lighting", 15.9, 4.4, 980),
            product(103L, "LED \u58c1\u706f Wall Sconce Lamp", "Home > \u7167\u660e Lighting", 39.9, 4.6, 760),
            product(201L, "Laptop Stand Aluminum", "Electronics > Computer Accessories", 25.9, 4.3, 1500),
            product(202L, "Phone Case Shockproof", "Electronics > Mobile Accessories", 11.9, 4.2, 2300)
        );
    }

    private ProductVO product(Long id, String name, String category, double price, double rating, int sales) {
        ProductVO p = new ProductVO();
        p.setId(id);
        p.setName(name);
        p.setCategory(category);
        p.setCategoryPath(category);
        p.setPrice(BigDecimal.valueOf(price));
        p.setRating(BigDecimal.valueOf(rating));
        p.setSales(sales);
        p.setStock(120);
        p.setCurrency("USD");
        p.setDescription(name + " " + category);
        return p;
    }
}
