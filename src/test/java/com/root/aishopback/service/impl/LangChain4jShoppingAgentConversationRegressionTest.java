package com.root.aishopback.service.impl;

import com.root.aishopback.mapper.QueryNormalizationLexiconMapper;
import com.root.aishopback.mapper.SearchAliasLexiconMapper;
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

class LangChain4jShoppingAgentConversationRegressionTest {

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
    void topicShouldBeInheritedForRecommendOneFollowUp() {
        AiShoppingAgentService.AgentReply first = service.reply("recommend shoes", 5001L);
        assertFalse(first.products().isEmpty(), first.content());
        assertTrue(first.products().stream().allMatch(this::isShoeName));

        AiShoppingAgentService.AgentReply second = service.reply("for daily wear", 5001L);
        assertFalse(second.products().isEmpty(), second.content());
        assertTrue(second.products().stream().allMatch(this::isShoeName));

        AiShoppingAgentService.AgentReply third = service.reply("recommend one", 5001L);
        assertFalse(third.products().isEmpty(), third.content());
        assertTrue(third.products().stream().allMatch(this::isShoeName));
    }

    @Test
    void headphoneFollowUpShouldNotDriftToSpeakerOrCase() {
        AiShoppingAgentService.AgentReply first = service.reply("noise cancelling headphone", 5002L);
        assertFalse(first.products().isEmpty(), first.content());
        assertTrue(first.products().stream().allMatch(this::isHeadphoneName));

        AiShoppingAgentService.AgentReply second = service.reply("any more", 5002L);
        assertFalse(second.products().isEmpty(), second.content());
        assertTrue(second.products().stream().allMatch(this::isHeadphoneName));
    }

    @Test
    void compareWithoutContextShouldNotTriggerNewSearch() {
        AiShoppingAgentService.AgentReply reply = service.reply("which has the highest rating", 5003L);
        assertTrue(reply.products().isEmpty());
    }

    @Test
    void compareWithinCurrentBatchShouldNotPullUnrelated() {
        AiShoppingAgentService.AgentReply first = service.reply("recommend headphone", 5004L);
        assertFalse(first.products().isEmpty(), first.content());
        assertTrue(first.products().stream().allMatch(this::isHeadphoneName));

        AiShoppingAgentService.AgentReply second = service.reply("which one is cheaper", 5004L);
        assertFalse(second.products().isEmpty());
        assertTrue(second.products().stream().allMatch(this::isHeadphoneName));
    }

    @Test
    void intentSwitchShouldCutOldTopic() {
        AiShoppingAgentService.AgentReply first = service.reply("recommend headphone", 5005L);
        assertFalse(first.products().isEmpty(), first.content());
        assertTrue(first.products().stream().allMatch(this::isHeadphoneName));

        AiShoppingAgentService.AgentReply second = service.reply("don't want headphone, recommend phone", 5005L);
        assertFalse(second.products().isEmpty(), second.content());
        assertTrue(second.products().stream().allMatch(this::isPhoneName));
    }

    @Test
    void unknownSpecificDemandShouldFailClosedInsteadOfReturningUnrelatedItems() {
        AiShoppingAgentService.AgentReply reply = service.reply("i want bicycle", 5006L);
        assertTrue(reply.products().isEmpty(), reply.content());
    }

    @Test
    void hikingIntentShouldReturnOutdoorProducts() {
        AiShoppingAgentService.AgentReply reply = service.reply("我要去登山，给我推荐点商品", 5007L);
        assertFalse(reply.products().isEmpty(), reply.content());
        assertTrue(reply.products().stream().allMatch(this::isOutdoorLikeName), reply.content());
    }

    private boolean isHeadphoneName(ProductVO p) {
        String n = p.getName() == null ? "" : p.getName().toLowerCase();
        return n.contains("headphone") || n.contains("earbud") || n.contains("headset");
    }

    private boolean isShoeName(ProductVO p) {
        String n = p.getName() == null ? "" : p.getName().toLowerCase();
        return n.contains("shoe") || n.contains("sneaker");
    }

    private boolean isPhoneName(ProductVO p) {
        String n = p.getName() == null ? "" : p.getName().toLowerCase();
        return n.contains("phone");
    }

    private boolean isOutdoorLikeName(ProductVO p) {
        String n = p.getName() == null ? "" : p.getName().toLowerCase();
        return n.contains("hiking") || n.contains("trek") || n.contains("camp") || n.contains("backpack") || n.contains("outdoor");
    }

    private List<ProductVO> fixtureProducts() {
        return List.of(
            product(1L, "Commuter Running Shoes", "Shoes > Running", 59.9, 4.6, 3000),
            product(2L, "Daily Casual Sneakers", "Shoes > Casual", 69.9, 4.5, 2400),
            product(3L, "ANC Bluetooth Headphone", "Electronics > Audio", 199.0, 4.7, 5300),
            product(4L, "Over-Ear Wireless Headset", "Electronics > Audio", 299.0, 4.5, 4200),
            product(5L, "Portable Bluetooth Speaker", "Electronics > Speaker", 159.0, 4.4, 2800),
            product(6L, "Headphone Protective Case", "Electronics > Accessories", 19.9, 4.2, 1100),
            product(7L, "Flagship Smartphone", "Electronics > Phone", 999.0, 4.8, 8600),
            product(8L, "Gaming Mouse", "Electronics > Peripherals", 49.0, 4.3, 2000),
            product(9L, "Hiking Backpack 45L Waterproof", "Sports > Outdoor > Hiking", 89.0, 4.6, 1800),
            product(10L, "Trekking Pole Carbon Ultralight", "Sports > Outdoor > Camping", 39.0, 4.5, 1200)
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
