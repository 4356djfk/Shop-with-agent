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
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;

class LangChain4jShoppingAgentCheaperFollowUpRegressionTest {

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
            2,
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
    void cheaperIntentShouldBeSeparatedFromCompareIntent() throws Exception {
        assertTrue(invokeBool("shouldTreatAsCheaperSearch", "\u6211\u60f3\u8981\u66f4\u4fbf\u5b9c\u7684"));
        assertFalse(invokeBool("shouldTreatAsCheaperSearch", "\u54ea\u4e2a\u66f4\u4fbf\u5b9c"));
        assertTrue(invokeBool("isExplicitCompareQuestion", "\u54ea\u4e2a\u66f4\u4fbf\u5b9c"));
    }

    @Test
    void compareCheaperShouldStayInCurrentBatch() {
        AiShoppingAgentService.AgentReply first = service.reply("headphone please", 71002L);
        assertFalse(first.products().isEmpty(), first.content());

        AiShoppingAgentService.AgentReply second = service.reply("\u54ea\u4e2a\u66f4\u4fbf\u5b9c", 71002L);
        assertFalse(second.products().isEmpty(), second.content());
        assertTrue(second.content() != null && second.content().contains("\u7ed3\u8bba"), second.content());
    }

    private boolean isHeadphoneLikeName(ProductVO p) {
        String n = p.getName() == null ? "" : p.getName().toLowerCase();
        return n.contains("headphone") || n.contains("headset") || n.contains("earbud");
    }

    private List<ProductVO> fixtureProducts() {
        return List.of(
            product(1L, "Premium ANC Headphone", "Audio", 299.0, 4.8, 9200),
            product(2L, "Tournament Gaming Headset", "Audio", 259.0, 4.7, 8100),
            product(3L, "Wireless Over-Ear Headphone", "Audio", 219.0, 4.6, 7600),
            product(4L, "Budget Bluetooth Headphone", "Audio", 129.0, 4.4, 1300),
            product(5L, "Entry Earbud", "Audio", 89.0, 4.2, 700),
            product(6L, "Portable Speaker", "Audio", 99.0, 4.5, 3500)
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
        p.setStock(100);
        p.setCurrency("USD");
        p.setDescription(name + " " + category);
        return p;
    }

    private boolean invokeBool(String method, String arg) throws Exception {
        Method m = LangChain4jShoppingAgentService.class.getDeclaredMethod(method, String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(service, arg);
    }
}
