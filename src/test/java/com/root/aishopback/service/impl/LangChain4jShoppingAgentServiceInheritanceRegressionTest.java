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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LangChain4jShoppingAgentServiceInheritanceRegressionTest {

    private LangChain4jShoppingAgentService service;

    @BeforeEach
    void setUp() throws Exception {
        ShopProductService shopProductService = Mockito.mock(ShopProductService.class);
        ShopCartService shopCartService = Mockito.mock(ShopCartService.class);
        ShopOrderService shopOrderService = Mockito.mock(ShopOrderService.class);
        SearchAliasLexiconMapper searchAliasLexiconMapper = Mockito.mock(SearchAliasLexiconMapper.class);
        QueryNormalizationLexiconMapper queryNormalizationLexiconMapper = Mockito.mock(QueryNormalizationLexiconMapper.class);
        ElasticsearchProductSearchService es = new ElasticsearchProductSearchService(false, "localhost", 9200, "products_search", "", "");

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

        // Seed one product into cache for compare-scoping test.
        ProductVO p = new ProductVO();
        p.setId(1L);
        p.setName("测试耳机");
        p.setPrice(BigDecimal.valueOf(99));
        p.setRating(BigDecimal.valueOf(4.5));
        p.setSales(1000);
        Field cacheField = LangChain4jShoppingAgentService.class.getDeclaredField("productCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Long, ProductVO> cache = (Map<Long, ProductVO>) cacheField.get(service);
        cache.put(1L, p);
    }

    @Test
    void shouldUseLastMentionedIntentWhenPromptHasMultipleDomains() throws Exception {
        Object intent = invoke("detectIntent", new Class<?>[]{String.class}, "不要耳机，推荐手机");
        assertEquals("ELECTRONICS", intent.toString());
    }

    @Test
    void shouldDetectExplicitIntentSwitchPrompt() throws Exception {
        Object memory = newMemory();
        setMemoryField(memory, "lastIntent", enumConst("IntentType", "HEADPHONE"));
        Object current = invoke("detectIntent", new Class<?>[]{String.class}, "不要耳机，推荐手机");
        Object switched = invoke(
            "isExplicitIntentSwitchPrompt",
            new Class<?>[]{String.class, memory.getClass(), current.getClass()},
            "不要耳机，推荐手机",
            memory,
            current
        );
        assertTrue((Boolean) switched);
    }

    @Test
    void shouldKeepRefinementAsFollowUpUnderSameTopic() throws Exception {
        Object memory = newMemory();
        setMemoryField(memory, "lastIntent", enumConst("IntentType", "SHOE"));
        Object followUp = invoke(
            "isRefinementFollowUp",
            new Class<?>[]{String.class, memory.getClass()},
            "我想要日常穿的",
            memory
        );
        assertTrue((Boolean) followUp);
    }

    @Test
    void shouldDetectAddToCartIntentForThisItemPhrase() throws Exception {
        Object result = invoke("isAddToCartIntent", new Class<?>[]{String.class}, "\u628a\u8fd9\u4e2a\u52a0\u5165\u8d2d\u7269\u8f66");
        assertTrue((Boolean) result);
    }

    @Test
    void shouldTreatPlainCancelAsInterruptiveDrop() throws Exception {
        Object memory = newMemory();
        setMemoryField(memory, "lastIntent", enumConst("IntentType", "HEADPHONE"));
        setMemoryField(memory, "lastShownProductIds", java.util.List.of(1L));
        Object dropped = invoke(
            "isCategoryDropWithoutReplacement",
            new Class<?>[]{String.class, memory.getClass()},
            "\u73b0\u5728\u6211\u4e0d\u60f3\u8981\u4e86",
            memory
        );
        assertTrue((Boolean) dropped);
    }

    @Test
    void shouldInheritIntentFromContextForActionTurn() throws Exception {
        Object memory = newMemory();
        setMemoryField(memory, "lastIntent", enumConst("IntentType", "HEADPHONE"));
        setMemoryField(memory, "lastShownProductIds", java.util.List.of(1L));
        Object dialogAct = enumConst("DialogAct", "ACTION");
        Object inherited = invoke(
            "shouldInheritIntentFromContext",
            new Class<?>[]{String.class, String.class, memory.getClass(), dialogAct.getClass(), boolean.class, boolean.class},
            "\u6362\u6210\u8fd9\u4e2a",
            "\u6362\u6210\u8fd9\u4e2a",
            memory,
            dialogAct,
            false,
            false
        );
        assertTrue((Boolean) inherited);
    }

    @Test
    void shouldNotFallbackToOldBatchWhenCurrentScopeHasSingleItem() throws Exception {
        Object memory = newMemory();
        setMemoryField(memory, "lastShownProductIds", java.util.List.of(1L));
        setMemoryField(memory, "lastAttributeSourceIds", java.util.List.of(1L));
        Object reply = invoke(
            "handleCompareFromLastShown",
            new Class<?>[]{String.class, memory.getClass()},
            "这个哪个评分最高",
            memory
        );
        assertNotNull(reply);
        AiShoppingAgentService.AgentReply cast = (AiShoppingAgentService.AgentReply) reply;
        assertTrue(cast.content().contains("只有一个"));
    }

    private Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = LangChain4jShoppingAgentService.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(service, args);
    }

    private Object enumConst(String nestedEnumSimpleName, String constant) throws Exception {
        Class<?> c = Class.forName(LangChain4jShoppingAgentService.class.getName() + "$" + nestedEnumSimpleName);
        @SuppressWarnings("unchecked")
        Object val = Enum.valueOf((Class<Enum>) c.asSubclass(Enum.class), constant);
        return val;
    }

    private Object newMemory() throws Exception {
        Class<?> c = Class.forName(LangChain4jShoppingAgentService.class.getName() + "$ConversationMemory");
        Constructor<?> ctor = c.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private void setMemoryField(Object memory, String name, Object value) throws Exception {
        Field f = memory.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(memory, value);
    }
}
