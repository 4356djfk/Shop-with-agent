package com.root.aishopback.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.root.aishopback.mapper.QueryNormalizationLexiconMapper;
import com.root.aishopback.mapper.SearchAliasLexiconMapper;
import com.root.aishopback.service.AiShoppingAgentService;
import com.root.aishopback.service.ShopCartService;
import com.root.aishopback.service.ShopOrderService;
import com.root.aishopback.service.ShopProductService;
import com.root.aishopback.vo.ProductVO;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;

class LangChain4jShoppingAgentOfflineEvalTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldPassOfflineEvalCases() throws Exception {
        LangChain4jShoppingAgentService service = buildServiceWithFixtureProducts();
        List<ConversationCase> cases = loadCases();

        int totalTurns = 0;
        int passedTurns = 0;
        List<String> failures = new ArrayList<>();

        for (ConversationCase c : cases) {
            for (int i = 0; i < c.turns.size(); i++) {
                TurnExpectation t = c.turns.get(i);
                totalTurns++;
                AiShoppingAgentService.AgentReply reply = service.reply(t.input, c.userId);
                String reason = checkTurn(reply, t);
                if (reason == null) {
                    passedTurns++;
                } else {
                    failures.add("[" + c.name + "][turn " + (i + 1) + "] " + reason + " | input=" + t.input + " | reply=" + safe(reply.content()));
                }
            }
        }

        double passRate = totalTurns == 0 ? 0.0 : (passedTurns * 1.0 / totalTurns);
        System.out.println("=== Offline Agent Eval Summary ===");
        System.out.println("cases=" + cases.size() + ", turns=" + totalTurns + ", passed=" + passedTurns + ", failed=" + failures.size());
        System.out.println("passRate=" + String.format(Locale.ROOT, "%.2f%%", passRate * 100));
        if (!failures.isEmpty()) {
            System.out.println("--- Failures ---");
            failures.forEach(System.out::println);
        }

        assertTrue(passRate >= 0.95, "offline eval pass rate below threshold: " + passRate);
    }

    private String checkTurn(AiShoppingAgentService.AgentReply reply, TurnExpectation t) {
        List<ProductVO> products = reply.products() == null ? List.of() : reply.products();
        String content = safe(reply.content()).toLowerCase(Locale.ROOT);

        if (Boolean.TRUE.equals(t.expectNonEmpty) && products.isEmpty()) {
            return "expected non-empty products but got empty";
        }
        if (Boolean.TRUE.equals(t.expectEmptyProducts) && !products.isEmpty()) {
            return "expected empty products but got " + products.size();
        }
        if (t.expectReplyContains != null) {
            for (String s : t.expectReplyContains) {
                if (!content.contains(s.toLowerCase(Locale.ROOT))) {
                    return "reply missing expected substring: " + s;
                }
            }
        }
        if (t.expectReplyContainsAny != null && !t.expectReplyContainsAny.isEmpty()) {
            boolean ok = t.expectReplyContainsAny.stream().anyMatch(s -> content.contains(s.toLowerCase(Locale.ROOT)));
            if (!ok) {
                return "reply missing any of expected substrings: " + t.expectReplyContainsAny;
            }
        }
        if (t.expectAllProductNamesContain != null) {
            for (ProductVO p : products) {
                String name = safe(p.getName());
                for (String s : t.expectAllProductNamesContain) {
                    if (!name.contains(s)) {
                        return "product name not containing required token '" + s + "': " + name;
                    }
                }
            }
        }
        if (t.expectAllProductNamesContainAny != null && !t.expectAllProductNamesContainAny.isEmpty()) {
            for (ProductVO p : products) {
                String ln = safe(p.getName()).toLowerCase(Locale.ROOT);
                boolean ok = t.expectAllProductNamesContainAny.stream().anyMatch(s -> ln.contains(s.toLowerCase(Locale.ROOT)));
                if (!ok) {
                    return "product name not matching any token " + t.expectAllProductNamesContainAny + ": " + p.getName();
                }
            }
        }
        return null;
    }

    private List<ConversationCase> loadCases() throws Exception {
        String path = "agent-offline-eval-cases.json";
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("missing resource: " + path);
            }
            byte[] data = is.readAllBytes();
            return objectMapper.readValue(new String(data, StandardCharsets.UTF_8), new TypeReference<>() {});
        }
    }

    private LangChain4jShoppingAgentService buildServiceWithFixtureProducts() {
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

        return new LangChain4jShoppingAgentService(
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

    private List<ProductVO> fixtureProducts() {
        return List.of(
            product(1L, "通勤跑鞋", "鞋靴 > 运动鞋", 59.9, 4.6, 3000),
            product(2L, "日常休闲鞋", "鞋靴 > 休闲鞋", 69.9, 4.5, 2400),
            product(3L, "降噪蓝牙耳机", "数码电子 > 音频设备", 199.0, 4.7, 5300),
            product(4L, "头戴式耳机", "数码电子 > 音频设备", 299.0, 4.5, 4200),
            product(5L, "无线音箱", "数码电子 > 音响", 159.0, 4.4, 2800),
            product(6L, "耳机保护壳", "数码电子 > 配件", 19.9, 4.2, 1100),
            product(7L, "旗舰手机", "数码电子 > 手机", 999.0, 4.8, 8600),
            product(8L, "双肩背包", "箱包 > 双肩包", 99.0, 4.3, 1900),
            product(9L, "手提包", "箱包 > 手提包", 129.0, 4.4, 1600),
            product(10L, "游戏鼠标", "数码电子 > 外设", 49.0, 4.3, 2000)
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

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private static class ConversationCase {
        public String name;
        public Long userId;
        public List<TurnExpectation> turns;
    }

    private static class TurnExpectation {
        public String input;
        public Boolean expectNonEmpty;
        public Boolean expectEmptyProducts;
        public List<String> expectReplyContains;
        public List<String> expectReplyContainsAny;
        public List<String> expectAllProductNamesContain;
        public List<String> expectAllProductNamesContainAny;
    }
}
