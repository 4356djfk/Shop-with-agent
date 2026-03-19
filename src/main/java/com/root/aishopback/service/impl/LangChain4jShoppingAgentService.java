package com.root.aishopback.service.impl;

import com.root.aishopback.entity.SearchAliasLexicon;
import com.root.aishopback.entity.QueryNormalizationLexicon;
import com.root.aishopback.mapper.QueryNormalizationLexiconMapper;
import com.root.aishopback.mapper.SearchAliasLexiconMapper;
import com.root.aishopback.service.AiShoppingAgentService;
import com.root.aishopback.service.ShopCartService;
import com.root.aishopback.service.ShopOrderService;
import com.root.aishopback.service.ShopProductService;
import com.root.aishopback.dto.CreateOrderRequest;
import com.root.aishopback.dto.OrderItemRequest;
import com.root.aishopback.vo.CartItemVO;
import com.root.aishopback.vo.OrderCreateVO;
import com.root.aishopback.vo.ProductVO;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LangChain4jShoppingAgentService implements AiShoppingAgentService {

    private static final Pattern PRODUCT_ID_PATTERN = Pattern.compile("(?:product|id|ID|#|\\u5546\\u54c1)\\s*[:\\uff1a]?\\s*(\\d+)");
    private static final Pattern PRODUCT_IDS_INLINE_PATTERN = Pattern.compile("(?:product\\s*id|\\u5546\\u54c1\\s*id|id)\\s*[:\\uff1a]?\\s*(\\d+(?:\\s*[,\\uff0c]\\s*\\d+)+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRODUCT_IDS_SEPARATOR_PATTERN = Pattern.compile("[\\s,\\uff0c;\\uff1b\\u3001]+");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");
    private static final Pattern SHIP_NAME_PATTERN = Pattern.compile("(?:\\u6536\\u8d27\\u4eba|\\u59d3\\u540d|name)\\s*[:\\uff1a]?\\s*([^,\\uff0c;\\uff1b\\n]{2,30})", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHIP_PHONE_PATTERN = Pattern.compile("(?:\\u7535\\u8bdd|\\u624b\\u673a\\u53f7|\\u624b\\u673a|phone)\\s*[:\\uff1a]?\\s*([0-9+\\-\\s]{6,20})", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHIP_ADDRESS_PATTERN = Pattern.compile("(?:\\u5730\\u5740|\\u6536\\u8d27\\u5730\\u5740|address)\\s*[:\\uff1a]?\\s*([^;\\uff1b\\n]{4,120})", Pattern.CASE_INSENSITIVE);
    private static final Pattern CJK_PATTERN = Pattern.compile("[\\u4e00-\\u9fff]");
    private static final Pattern FOLLOW_UP_PATTERN = Pattern.compile(".*(\\u8fd8\\u6709|\\u522b\\u7684|\\u5176\\u4ed6|\\u518d\\u6765|\\u66f4\\u591a|\\u5c31\\u8fd9\\u4e9b|is that all|only these|show more|more).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern RANGE_PATTERN = Pattern.compile("(\\d{1,6})\\s*[-~\\u5230]\\s*(\\d{1,6})");
    private static final Pattern MAX_PRICE_PATTERN = Pattern.compile("(\\d{1,6})\\s*(?:\\u5143|\\u5757|rmb|usd|\\$)?\\s*(?:\\u4ee5\\u5185|\\u4ee5\\u4e0b|\\u4e4b\\u5185|\\u4e0d\\u8d85\\u8fc7|\\u5c01\\u9876)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MIN_PRICE_PATTERN = Pattern.compile("(?:\\u81f3\\u5c11|\\u4e0d\\u4f4e\\u4e8e|\\u9ad8\\u4e8e|\\u5927\\u4e8e)\\s*(\\d{1,6})", Pattern.CASE_INSENSITIVE);
    private static final Pattern COUNT_PATTERN = Pattern.compile("(\\d{1,2})\\s*(?:\\u4e2a|\\u6b3e|\\u4ef6|\\u6761|\\u4e2a\\u5546\\u54c1)", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUANTITY_LABELED_PATTERN = Pattern.compile(
        "(?:\\u6570\\u91cf|qty|quantity|\\u4ef6\\u6570|\\u4e70)\\s*[:\\uff1a]?\\s*(\\d{1,3})",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MULTIPLIER_QTY_PATTERN = Pattern.compile("(?:^|\\s)[xX\\u00d7\\*]\\s*(\\d{1,3})(?:\\s|$)");
    private static final Pattern BRAND_ONLY_PATTERN = Pattern.compile("(?:\\u53ea\\u770b|\\u53ea\\u8981|\\u5c31\\u8981|\\u54c1\\u724c)\\s*[:\\uff1a]?\\s*([\\p{L}\\p{N}\\- ]{2,30})", Pattern.CASE_INSENSITIVE);
    private static final Pattern BRAND_EXCLUDE_PATTERN = Pattern.compile("(?:\\u4e0d\\u8981|\\u6392\\u9664|\\u4e0d\\u770b)\\s*([\\p{L}\\p{N}\\- ]{2,30})", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPEC_TOKEN_PATTERN = Pattern.compile("(\\d+\\s*(?:g|gb|tb|\\u5bf8|inch|hz|w|mah))", Pattern.CASE_INSENSITIVE);
    private static final Pattern STANDALONE_PHONE_PATTERN = Pattern.compile("(?<![a-z])phone(?![a-z])", Pattern.CASE_INSENSITIVE);
    private static final Pattern MOBILE_PHONE_PATTERN = Pattern.compile("(?<![a-z])mobile\\s+phone(?![a-z])", Pattern.CASE_INSENSITIVE);
    private static final Pattern CELL_PHONE_PATTERN = Pattern.compile("(?<![a-z])cell\\s+phone(?![a-z])", Pattern.CASE_INSENSITIVE);
    private static final Pattern MOBILE_TERM_PATTERN = Pattern.compile("(?<![a-z])mobile(?![a-z])", Pattern.CASE_INSENSITIVE);
    private static final Pattern LLM_INTENT_PATTERN = Pattern.compile("INTENT\\s*=\\s*([A-Z_]+)");
    private static final Pattern LLM_SCOPE_PATTERN = Pattern.compile("SCOPE\\s*=\\s*([A-Z_]+)");
    private static final Pattern LLM_ACTION_PATTERN = Pattern.compile("ACTION\\s*=\\s*([A-Z_]+)");
    private static final Pattern LLM_CONF_PATTERN = Pattern.compile("CONF\\s*=\\s*([0-9]*\\.?[0-9]+)");
    private static final int EMBEDDING_DOC_MAX_CHARS = 1200;
    private static final int EMBEDDING_QUERY_MAX_CHARS = 400;
    private static final long RAG_TIMEOUT_MS = 1200L;
    private static final long NORMALIZATION_RULES_REFRESH_MS = 60_000L;
    private static final long PRODUCT_CACHE_REFRESH_MS = 30_000L;
    private static final int LEXICAL_GOOD_ENOUGH = 3;

    private static final String ZH_HELLO = "\u4f60\u597d";
    private static final String ZH_HI = "\u60a8\u597d";
    private static final String ZH_PHONE = "\u624b\u673a";
    private static final String ZH_HEADPHONE = "\u8033\u673a";
    private static final String ZH_ADD_CART = "\u52a0\u5165\u8d2d\u7269\u8f66";

    private static final List<String> PHONE_TERMS = List.of(
        ZH_PHONE, "phone", "smartphone", "iphone", "android", "cell phone", "mobile phone"
    );
    private static final List<String> PHONE_STRICT_TERMS = List.of(
        ZH_PHONE, "phone", "smartphone", "iphone", "android", "mobile"
    );
    private static final List<String> PHONE_EXCLUDE_TERMS = List.of(
        ZH_HEADPHONE, "headphone", "headset", "earphone", "earbud", "earbuds", "airpods", "speaker", "\u97f3\u7bb1"
    );

    private static final List<String> HEADPHONE_TERMS = List.of(
        ZH_HEADPHONE, "headphone", "headset", "earphone", "earbud", "earbuds", "tws", "bluetooth", "wireless", "airpods"
    );
    private static final List<String> HEADPHONE_STRICT_TERMS = List.of(
        ZH_HEADPHONE, "\u8033\u585e", "headphone", "headset", "earphone", "earbud", "earbuds", "airpods", "in-ear", "over-ear"
    );
    private static final List<String> HEADPHONE_EXCLUDE_TERMS = List.of(
        "\u97f3\u7bb1", "\u626c\u58f0\u5668", "speaker", "soundbar", "subwoofer",
        "\u9f20\u6807", "\u952e\u76d8", "mouse", "keyboard",
        "\u5c4f\u5e55", "\u89e6\u6478\u5c4f", "\u5c4f\u5e55\u603b\u6210", "screen", "display", "lcd", "digitizer",
        "\u80cc\u5305", "\u4e66\u5305", "backpack", "bag",
        "\u5934\u76d4", "\u751f\u53d1", "\u8131\u53d1", "helmet", "hair growth", "laser cap", "hair loss",
        "\u5145\u7535\u5ea7", "\u65e0\u7ebf\u5145\u7535", "\u652f\u67b6", "wireless charger", "charging dock", "dock charger", "charging stand"
        , "\u5145\u7535\u5934", "\u5145\u7535\u5757", "\u7535\u6e90\u9002\u914d\u5668", "wall charger", "power adapter", "charging brick"
    );
    private static final List<String> HEADPHONE_ACCESSORY_TERMS = List.of(
        "\u4fdd\u62a4\u58f3", "\u8033\u673a\u5957", "\u8033\u57ab", "case", "cover", "earpad", "replacement pad"
    );
    private static final List<String> HEADPHONE_ACCESSORY_ONLY_TERMS = List.of(
        "\u8033\u585e\u5957", "\u8033\u585e\u5957\u88c5", "\u8033\u5957", "\u8033\u673a\u5957", "\u4fdd\u62a4\u58f3", "\u4fdd\u62a4\u5957",
        "\u66ff\u6362", "\u66ff\u6362\u4ef6", "\u66ff\u6362\u88c5", "\u9002\u914d", "replacement", "replace", "for airpod",
        "compatible", "tips", "ear tips", "foam tips", "silicone tips", "earpad", "replacement pad", "case", "cover"
    );
    private static final List<String> HEADPHONE_MAIN_FEATURE_TERMS = List.of(
        "\u964d\u566a", "\u4e3b\u52a8\u964d\u566a", "\u84dd\u7259", "\u65e0\u7ebf", "\u7eed\u822a", "\u64ad\u653e\u65f6\u95f4", "\u9ea6\u514b\u98ce",
        "anc", "noise cancelling", "bluetooth", "wireless", "playtime", "battery", "mic", "microphone"
    );
    private static final List<String> MOUSE_TERMS = List.of(
        "\u9f20\u6807", "mouse", "wireless mouse", "gaming mouse", "bluetooth mouse", "ergonomic mouse"
    );
    private static final List<String> MOUSE_STRICT_TERMS = List.of(
        "\u9f20\u6807", "mouse", "gaming mouse", "wireless mouse"
    );
    private static final List<String> MOUSE_EXCLUDE_TERMS = List.of(
        "\u9f20\u6807\u57ab", "\u684c\u57ab", "\u952e\u9f20\u57ab", "\u8155\u6258",
        "mouse pad", "desk mat", "keyboard mat", "wrist rest",
        "kvm", "switcher", "docking station", "usb hub", "\u5207\u6362\u5668", "\u6269\u5c55\u575e",
        "keyboard", "\u952e\u76d8"
    );
    private static final List<String> KEYBOARD_TERMS = List.of(
        "\u952e\u76d8", "\u673a\u68b0\u952e\u76d8", "keyboard", "mechanical keyboard", "gaming keyboard", "wireless keyboard"
    );
    private static final List<String> KEYBOARD_STRICT_TERMS = List.of(
        "\u952e\u76d8", "\u673a\u68b0\u952e\u76d8", "keyboard", "mechanical keyboard", "gaming keyboard"
    );
    private static final List<String> KEYBOARD_EXCLUDE_TERMS = List.of(
        "\u952e\u76d8\u819c", "\u952e\u76d8\u5957", "\u952e\u76d8\u76ae\u80a4", "\u8155\u6258", "\u684c\u57ab", "\u952e\u9f20\u57ab",
        "keyboard cover", "keyboard skin", "keyboard case", "wrist rest", "desk mat", "keyboard mat", "keycap",
        "\u4fdd\u62a4\u58f3", "\u4fdd\u62a4\u5957", "macbook case", "ipad case", "tablet case", "laptop case",
        "kvm", "switcher", "docking station", "usb hub", "\u5207\u6362\u5668", "\u6269\u5c55\u575e"
    );
    private static final List<String> SHOE_TERMS = List.of(
        "\u978b", "\u978b\u5b50", "\u7403\u978b", "\u8dd1\u978b", "shoe", "shoes", "sneaker", "sneakers", "boots", "sandals"
    );
    private static final List<String> BAG_TERMS = List.of(
        "\u5305", "\u80cc\u5305", "\u624b\u63d0\u5305", "\u53cc\u80a9\u5305", "\u659c\u630e\u5305", "\u7bb1\u5305",
        "bag", "bags", "backpack", "handbag", "tote", "luggage", "satchel", "messenger"
    );
    private static final List<String> BAG_STRONG_TERMS = List.of(
        "\u80cc\u5305", "\u624b\u63d0\u5305", "\u53cc\u80a9\u5305", "\u659c\u630e\u5305", "\u7bb1\u5305", "\u884c\u674e", "\u884c\u674e\u7bb1", "\u65c5\u884c\u5305",
        "backpack", "handbag", "tote", "luggage", "suitcase", "duffel", "crossbody", "messenger", "satchel", "purse", "wallet", "clutch"
    );
    private static final List<String> BAG_NOISE_TERMS = List.of(
        "balm", "cream", "lotion", "ointment", "moisturizer", "salve", "skin", "soap", "shampoo",
        "\u62a4\u624b\u971c", "\u4e73\u6db2", "\u8f6f\u818f", "\u62a4\u80a4", "\u6d17\u53d1", "\u6c90\u6d74"
    );
    private static final List<String> ELECTRONICS_TERMS = List.of(
        "\u7535\u5b50", "\u6570\u7801", "\u79d1\u6280", "\u7535\u5b50\u4ea7\u54c1", "\u624b\u673a", "\u8033\u673a",
        "electronics", "digital", "gadget", "tech", "smartphone", "headphone", "earbuds", "bluetooth", "wireless"
    );
    private static final Set<String> ELECTRONICS_INTENT_EXCLUDE_TERMS = Set.of(
        "\u624b\u673a", "\u8033\u673a", "phone", "smartphone", "headphone", "earbuds", "bluetooth", "wireless"
    );
    private static final List<String> COMPUTER_TERMS = List.of(
        "\u7535\u8111", "\u7b14\u8bb0\u672c", "\u53f0\u5f0f\u673a", "\u6e38\u620f\u672c", "\u8f7b\u8584\u672c",
        "computer", "pc", "laptop", "notebook", "desktop", "macbook"
    );
    private static final List<String> DAILY_TERMS = List.of(
        "\u751f\u6d3b\u7528\u54c1", "\u65e5\u7528\u54c1", "\u5bb6\u5c45", "\u5bb6\u7528", "\u5bb6\u5ead", "\u6d74\u5ba4", "\u53a8\u623f", "\u6536\u7eb3",
        "household", "home", "daily", "lifestyle", "bathroom", "kitchen", "storage", "cleaning"
    );
    private static final List<String> FOOD_TERMS = List.of(
        "\u5403\u7684", "\u98df\u7269", "\u98df\u54c1", "\u96f6\u98df", "\u70b9\u5fc3", "\u996e\u6599", "\u8336", "\u5496\u5561", "\u997c\u5e72", "\u7cd6\u679c", "\u5de7\u514b\u529b",
        "food", "snack", "snacks", "drink", "drinks", "beverage", "beverages", "tea", "coffee", "cookie", "cookies", "candy", "chocolate"
    );
    private static final List<String> FOOD_EXCLUDE_TERMS = List.of(
        "\u72d7\u7cae", "\u732b\u7cae", "\u5ba0\u7269", "\u9972\u6599", "\u5582\u98df",
        "pet food", "dog food", "cat food", "pet", "feed"
    );
    private static final List<String> FOOD_NON_EDIBLE_TERMS = List.of(
        "\u9a6c\u6876", "\u5ea7\u5708", "\u9a6c\u6876\u5ea7\u5708", "\u50a8\u7269\u5bb9\u5668", "\u98df\u7269\u50a8\u5b58", "\u4fbf\u5f53\u76d2", "\u73bb\u7483\u76d2", "\u5207\u788e\u673a", "\u98df\u7269\u5207\u788e\u673a", "\u7af9\u4e32", "\u4e32\u7b7e",
        "\u5348\u9910\u888b", "\u96f6\u98df\u888b", "\u4fdd\u6e29\u888b",
        "toilet", "toilet seat", "seat cover", "storage container", "food storage", "meal prep container", "container set",
        "lunch box", "bento box", "lunch bag", "snack bag", "insulated bag", "chopper", "food chopper", "rack", "skewer", "bamboo skewer"
    );
    private static final List<String> FOOD_EDIBLE_HINT_TERMS = List.of(
        "\u96f6\u98df", "\u98df\u54c1", "\u996e\u6599", "\u8336", "\u5496\u5561", "\u997c\u5e72", "\u5de7\u514b\u529b", "\u7cd6\u679c", "\u86cb\u767d\u7c89", "\u87ba\u65cb\u85fb", "\u8d85\u7ea7\u98df\u7269",
        "snack", "food", "beverage", "drink", "tea", "coffee", "cookie", "chocolate", "candy", "protein", "powder", "superfood", "organic"
    );
    private static final List<String> BEDDING_TERMS = List.of(
        "\u88ab\u5b50", "\u88ab\u82af", "\u88ab\u5957", "\u5e8a\u54c1", "\u5e8a\u4e0a\u7528\u54c1", "\u88ab\u5b50\u5957\u88c5", "\u56db\u4ef6\u5957",
        "comforter", "quilt", "duvet", "bedding", "bed set", "bed in a bag", "quilt set", "comforter set"
    );
    private static final List<String> BEDDING_EXCLUDE_TERMS = List.of(
        "pet bed", "dog bed", "cat bed", "flea", "tick", "collar",
        "\u5ba0\u7269\u5e8a", "\u72d7\u7a9d", "\u732b\u7a9d", "\u8df3\u86a4", "\u8731\u866b", "\u9879\u5708"
    );
    private static final List<String> DAILY_STRONG_TERMS = List.of(
        "\u53a8\u623f", "\u6d74\u5ba4", "\u6536\u7eb3", "\u7f6e\u7269", "\u6e05\u6d01", "\u6d17\u6da4", "\u67b6", "\u7bee", "\u6876", "\u5bb9\u5668", "\u67dc",
        "kitchen", "bathroom", "storage", "organizer", "container", "basket", "rack", "shelf", "bin", "cabinet", "cleaning"
    );
    private static final List<String> DAILY_EXCLUDE_TERMS = List.of(
        "\u5730\u6bef", "\u5730\u57ab", "\u7a97\u5e18", "\u5e18\u5b50", "\u88c5\u9970\u753b",
        "rug", "carpet", "curtain", "drape", "wall art", "frame"
    );
    private static final List<String> LIGHT_TERMS = List.of(
        "\u706f", "\u706f\u5177", "\u7167\u660e", "\u53f0\u706f", "\u58c1\u706f", "\u843d\u5730\u706f", "\u5438\u9876\u706f",
        "lamp", "light", "lighting", "led", "bulb", "sconce", "chandelier", "night light", "desk lamp", "floor lamp"
    );
    private static final List<String> BIKE_TERMS = List.of(
        "\u81ea\u884c\u8f66", "\u5355\u8f66", "\u9a91\u884c", "\u5c71\u5730\u8f66", "\u516c\u8def\u8f66", "\u7535\u52a8\u81ea\u884c\u8f66", "\u4e09\u8f6e\u8f66",
        "bicycle", "bike", "cycling", "cyclist", "road bike", "mountain bike", "mtb", "bmx", "tricycle", "ebike", "e-bike"
    );
    private static final List<String> BIKE_EXCLUDE_TERMS = List.of(
        "\u6469\u6258", "\u8d8a\u91ce\u6469\u6258",
        "motorcycle", "motocross", "dirt bike"
    );
    private static final List<String> BIKE_PRODUCT_TERMS = List.of(
        "\u81ea\u884c\u8f66", "\u5c71\u5730\u8f66", "\u516c\u8def\u8f66", "\u9a91\u884c", "\u5934\u76d4", "\u8f6e\u80ce", "\u8f66\u94fe", "\u8e0f\u677f", "\u8f66\u628a", "\u5ea7\u57ab",
        "bicycle", "mountain bike", "road bike", "cycling", "bike helmet", "pedal", "chain", "handlebar", "saddle", "wheel", "tire", "bmx bike"
    );
    private static final List<String> OUTDOOR_TERMS = List.of(
        "\u6237\u5916", "\u767b\u5c71", "\u5f92\u6b65", "\u9732\u8425", "\u91ce\u8425", "\u5f92\u6b65\u978b", "\u767b\u5c71\u978b", "\u767b\u5c71\u9774", "\u5f92\u6b65\u9774", "\u6237\u5916\u978b", "\u51b2\u950b\u8863", "\u9632\u6c34", "\u9632\u98ce",
        "\u767b\u5c71\u5305", "\u6237\u5916\u5305", "\u7761\u888b", "\u5e10\u7bf7", "\u767b\u5c71\u6756", "\u5934\u706f", "\u6c34\u58f6", "\u6c34\u888b", "\u4fdd\u6696", "\u901f\u5e72",
        "outdoor", "outdoor shoes", "hiking", "hiking shoe", "hiking shoes", "trekking", "mountaineering", "camping", "backpacking", "trail", "trail shoes", "trail running", "hiking boots", "trek shoes", "rain jacket",
        "windbreaker", "waterproof", "sleeping bag", "tent", "trekking pole", "headlamp", "hydration", "daypack", "climbing backpack"
    );
    private static final List<String> OUTDOOR_SCENARIO_TERMS = List.of(
        "\u53bb\u767b\u5c71", "\u53bb\u5f92\u6b65", "\u51c6\u5907\u767b\u5c71", "\u51c6\u5907\u9732\u8425", "\u6237\u5916\u6d3b\u52a8", "\u6237\u5916\u8fd0\u52a8",
        "go hiking", "go trekking", "go camping", "for hiking", "for trekking", "for camping", "mountain trip", "outdoor trip"
    );
    private static final List<String> EAT_SCENARIO_TERMS = List.of(
        "\u8981\u5403\u7684", "\u4e70\u5403\u7684", "\u4e70\u70b9\u5403\u7684", "\u60f3\u5403\u4e1c\u897f", "\u6211\u60f3\u5403\u4e1c\u897f", "\u5403\u4e1c\u897f", "\u5403\u70b9\u4e1c\u897f", "\u60f3\u5403\u70b9",
        "\u56e4\u5403\u7684", "\u56e4\u53e3\u7cae", "\u5403\u70b9\u4ec0\u4e48", "\u4eca\u5929\u5403\u4ec0\u4e48",
        "\u505a\u996d", "\u505a\u83dc", "\u505a\u987f\u996d", "\u70e7\u83dc", "\u716e\u996d",
        "\u56e4\u96f6\u98df", "\u96f6\u98df\u56e4\u8d27", "\u56e4\u996e\u6599", "\u8865\u7ed9\u996e\u6599", "\u4e70\u8336", "\u4e70\u5496\u5561",
        "\u591c\u5bb5", "\u65e9\u9910", "\u5348\u9910", "\u665a\u9910", "\u4e0b\u996d\u83dc", "\u96f6\u5634", "\u53bb\u9732\u8425\u5e26\u5403\u7684", "\u56e4\u98df\u6750", "\u51c6\u5907\u98df\u6750",
        "something to eat", "buy snacks", "stock snacks", "food run", "grab some food",
        "make dinner", "cook at home", "cook tonight", "meal prep", "food supplies", "grocery supplies", "something to drink",
        "buy drinks", "drink supplies", "breakfast supplies", "dinner supplies", "snack supplies"
    );
    private static final List<String> STAY_SCENARIO_TERMS = List.of(
        "\u8981\u4f4f", "\u4f4f\u9152\u5e97", "\u4f4f\u6c11\u5bbf", "\u79df\u623f", "\u642c\u5bb6", "\u521a\u79df\u623f", "\u65b0\u5bb6\u5165\u4f4f", "\u5bbf\u820d\u5165\u4f4f",
        "\u5367\u5ba4\u7528\u54c1", "\u5e8a\u4e0a\u7528\u54c1", "\u88ab\u5b50\u6795\u5934", "\u5bb6\u7eba", "\u5e8a\u54c1\u56db\u4ef6\u5957",
        "\u4f4f\u5bb6", "\u5c45\u5bb6\u4f4f\u5bbf", "\u641e\u5367\u5ba4", "\u5bb6\u91cc\u7761\u89c9\u7528",
        "place to stay", "stay in hotel", "stay in hostel", "move into apartment", "new apartment setup",
        "dorm essentials", "bedroom essentials", "home stay setup", "bedding setup", "sleep setup", "move-in essentials"
    );
    private static final List<String> TRAVEL_SCENARIO_TERMS = List.of(
        "\u51fa\u884c", "\u53bb\u65c5\u6e38", "\u51fa\u5dee", "\u901a\u52e4", "\u5750\u9ad8\u94c1", "\u5750\u98de\u673a", "\u5750\u706b\u8f66", "\u81ea\u9a7e",
        "\u77ed\u9014\u65c5\u884c", "\u957f\u9014\u65c5\u884c", "\u5468\u672b\u51fa\u884c", "\u8282\u5047\u65e5\u51fa\u884c", "\u51c6\u5907\u884c\u88c5",
        "\u884c\u674e\u6536\u7eb3", "\u884c\u674e\u7bb1", "\u767b\u673a\u884c\u674e", "\u51fa\u95e8\u5e26\u4ec0\u4e48", "\u65c5\u884c\u88c5\u5907", "\u901a\u52e4\u88c5\u5907",
        "travel", "trip", "business trip", "commute", "flight essentials", "airport essentials", "train trip",
        "road trip", "weekend trip", "holiday trip", "travel packing", "pack for trip", "packing list", "carry-on essentials"
    );
    private static final List<String> OUTDOOR_EXCLUDE_TERMS = List.of(
        "phone case", "earbud case", "screen protector", "keyboard cover", "mouse pad", "lipstick", "foundation", "pet food"
    );
    private static final List<String> BIKE_NON_PRODUCT_TERMS = List.of(
        "vans", "old skool", "shoe", "shoes", "sneaker", "sneakers", "loafer", "sandals", "boots", "skate shoe"
    );
    private static final List<String> KITCHEN_CORE_TERMS = List.of(
        "\u53a8\u623f", "kitchen"
    );
    private static final List<String> STORAGE_CORE_TERMS = List.of(
        "\u6536\u7eb3", "\u7f6e\u7269", "storage", "organizer", "container", "rack", "shelf", "bin", "cabinet"
    );
    private static final List<String> BOOK_TEXTBOOK_CORE_TERMS = List.of(
        "\u6559\u6750", "textbook", "workbook", "study guide", "book", "books"
    );
    private static final List<String> MAKEUP_CORE_TERMS = List.of(
        "\u7f8e\u5986", "\u5f69\u5986", "\u53e3\u7ea2", "cosmetic", "makeup", "lipstick", "foundation", "eyeliner", "serum"
    );
    private static final List<String> MAKEUP_QUERY_STRICT_TERMS = List.of(
        "\u7f8e\u5986", "\u53e3\u7ea2", "cosmetic", "makeup", "lipstick", "foundation", "eyeliner", "serum"
    );
    private static final List<String> PET_BOWL_TERMS = List.of(
        "\u72d7\u7897", "\u732b\u7897", "\u5ba0\u7269\u7897", "\u5582\u98df\u7897", "\u98df\u76c6", "\u996d\u76c6", "\u6c34\u7897",
        "dog bowl", "cat bowl", "pet bowl", "feeding bowl", "food bowl", "water bowl", "slow feeder", "pet feeder"
    );
    private static final List<String> PET_TERMS = List.of(
        "\u5ba0\u7269", "\u5ba0\u7269\u7528\u54c1", "\u72d7", "\u732b", "\u72d7\u7cae", "\u732b\u7cae", "pet", "dog", "cat", "pet supplies", "pet toy", "pet food"
    );
    private static final List<String> BABY_TERMS = List.of(
        "\u6bcd\u5a74", "\u5a74\u513f", "\u5a74\u7ae5", "\u5b9d\u5b9d", "\u65b0\u751f\u513f", "\u63a8\u8f66", "\u5a74\u513f\u5e8a", "\u5c3f\u4e0d\u6e7f",
        "baby", "infant", "newborn", "stroller", "crib", "diaper", "toddler"
    );
    private static final List<String> BOOK_TERMS = List.of(
        "\u56fe\u4e66", "\u4e66", "\u5c0f\u8bf4", "\u6559\u6750", "\u6587\u5b66", "\u7535\u5b50\u4e66", "book", "books", "novel", "textbook", "kindle", "literature"
    );
    private static final List<String> BOOK_STRONG_TERMS = List.of(
        "\u56fe\u4e66", "\u5c0f\u8bf4", "\u6559\u6750", "\u7535\u5b50\u4e66", "\u4f5c\u8005",
        "novel", "textbook", "literature", "hardcover", "paperback", "ebook", "kindle edition", "book series"
    );
    private static final List<String> BOOK_EXCLUDE_TERMS = List.of(
        "macbook", "notebook computer", "laptop", "keyboard", "stand", "case", "\u7b14\u8bb0\u672c\u7535\u8111", "\u7535\u8111"
    );
    private static final List<String> TOY_TERMS = List.of(
        "\u73a9\u5177", "\u79ef\u6728", "\u62fc\u56fe", "\u515a\u5177", "\u5361\u724c\u6e38\u620f", "toy", "lego", "puzzle", "doll", "board game", "card game"
    );
    private static final List<String> TOY_CHILD_TERMS = List.of(
        "\u513f\u7ae5", "\u5b69\u5b50", "\u7537\u5b69", "\u5973\u5b69", "\u5e7c\u513f", "\u5b9d\u5b9d",
        "children", "kids", "kid", "child", "toddler", "boy", "girl"
    );
    private static final List<String> TOY_PET_EXCLUDE_TERMS = List.of(
        "\u72d7", "\u732b", "\u5ba0\u7269", "\u72d7\u7897", "\u5582\u98df", "\u996d\u76c6",
        "dog", "cat", "pet", "feeder", "bowl", "pet supplies"
    );
    private static final List<String> MAKEUP_TERMS = List.of(
        "\u7f8e\u5986", "\u5f69\u5986", "\u53e3\u7ea2", "\u773c\u7ebf\u7b14", "\u7c89\u5e95", "\u7cbe\u534e\u6db2", "makeup", "cosmetic", "lipstick", "eyeliner", "foundation", "serum"
    );
    private static final List<String> MAKEUP_EXCLUDE_TERMS = List.of(
        "\u5de5\u5177", "\u626d\u624b", "\u94bb\u5934", "\u6c7d\u8f66", "wrench", "drill", "tool", "car accessory"
    );
    private static final Set<String> QUERY_STOP_WORDS = Set.of(
        "\u63a8\u8350", "\u4e00\u4e0b", "\u5e2e\u6211", "\u5e2e\u6211\u627e", "\u6211\u60f3", "\u6211\u60f3\u4e70",
        "\u6211\u8981", "\u60f3\u8981", "\u9700\u8981", "\u8981\u4e70",
        "\u5546\u54c1", "\u4ea7\u54c1", "\u6709\u6ca1\u6709", "\u6709\u4ec0\u4e48", "\u6709\u5565", "\u6709\u6ca1", "\u6709\u6728\u6709",
        "\u6765\u70b9", "\u6574\u70b9", "\u641e\u70b9", "\u6c42\u63a8\u8350", "\u770b\u770b", "\u9ebb\u70e6\u63a8\u8350", "\u6709\u6ca1\u6709\u63a8\u8350",
        "\u5417", "\u5462", "\u5440", "\u5427",
        "\u597d\u7528", "\u67e5\u627e", "\u641c\u7d22", "recommend", "find", "search", "show", "me", "please"
    );
    private static final Set<String> ALIAS_NOISE_TERMS = Set.of(
        "\u4e0a\u4e00\u9875", "\u4e0b\u4e00\u9875", "\u8bbf\u95ee\u5546\u5e97", "\u6dfb\u52a0\u5230\u8d2d\u7269\u8f66",
        "\u5ba2\u6237\u8bc4\u8bba", "\u4ea7\u54c1\u63cf\u8ff0", "\u4ea7\u54c1\u7279\u70b9", "\u5305\u88c5\u6e05\u5355",
        "\u989c\u8272", "\u5c3a\u5bf8", "\u5c3a\u7801", "\u89c4\u683c", "\u4ef7\u683c", "\u4ef6", "\u4ef6\u88c5",
        "\u7684", "\u548c", "\u4e0e", "\u6216", "\u6211", "\u4f60", "\u4ed6", "\u5979", "\u5b83",
        "\u8fd9\u6b3e", "\u9002\u5408", "\u9002\u7528\u4e8e", "\u91c7\u7528", "\u6ce8\u610f", "\u5305\u62ec",
        "for", "the", "and", "with", "this", "that", "from", "into", "module", "link", "img", "apm", "aplus", "row", "text"
    );
    private static final List<String> VALUE_TERMS = List.of(
        "\u5212\u7b97", "\u6700\u5212\u7b97", "\u6027\u4ef7\u6bd4", "\u9ad8\u6027\u4ef7\u6bd4", "\u8d85\u503c", "\u5b9e\u60e0", "\u4fbf\u5b9c", "\u4fbf\u5b9c\u70b9", "\u5ec9\u4ef7", "\u4f4e\u4ef7", "\u7701\u94b1",
        "value", "best value", "cost-effective", "budget", "cheap", "affordable"
    );
    private static final List<String> PREMIUM_TERMS = List.of(
        "\u9ad8\u7aef", "\u8d35\u4e00\u70b9", "\u54c1\u8d28\u597d", "\u5957\u9910\u5347\u7ea7",
        "premium", "expensive", "high-end"
    );
    private static final List<String> HOT_TERMS = List.of(
        "\u70ed\u95e8", "\u7206\u6b3e", "\u9500\u91cf\u9ad8", "\u70ed\u5356",
        "hot", "best seller", "popular"
    );
    private static final List<String> RATING_TERMS = List.of(
        "\u9ad8\u5206", "\u8bc4\u5206\u9ad8", "\u53e3\u7891", "\u597d\u8bc4",
        "rating", "top rated"
    );
    private static final List<String> COMPARE_TERMS = List.of(
        "\u54ea\u4e2a\u597d", "\u54ea\u4e2a\u66f4\u597d", "\u54ea\u4e2a\u6700\u597d", "\u8c01\u66f4\u597d",
        "\u8bc4\u5206\u6700\u9ad8", "\u54ea\u4e2a\u8bc4\u5206\u6700\u9ad8", "\u8fd9\u51e0\u4ef6\u91cc", "\u8fd9\u51e0\u4e2a\u91cc",
        "\u533a\u522b", "\u5bf9\u6bd4", "\u6bd4\u8f83", "\u600e\u4e48\u9009",
        "which is better", "difference", "compare", "comparison"
    );
    private static final List<String> STOCK_TERMS = List.of(
        "\u6709\u73b0\u8d27", "\u5e93\u5b58", "\u5269\u4f59", "\u8d27\u591f\u4e0d\u591f", "in stock", "stock"
    );
    private static final List<String> SHIPPING_TERMS = List.of(
        "\u591a\u4e45\u53d1\u8d27", "\u4ec0\u4e48\u65f6\u5019\u53d1\u8d27", "\u53d1\u8d27\u65f6\u95f4", "\u5230\u8d27", "\u51e0\u5929\u5230",
        "shipping", "delivery", "ship time"
    );
    private static final List<String> ATTRIBUTE_TERMS = List.of(
        "\u8f7b\u8584", "\u6e38\u620f", "\u529e\u516c", "\u7eed\u822a", "\u9759\u97f3", "\u9ad8\u6027\u80fd", "\u4fbf\u643a", "\u5b66\u751f",
        "gaming", "office", "portable", "lightweight", "performance", "battery", "silent"
    );
    private static final List<String> HANDOFF_TERMS = List.of(
        "\u9000\u6b3e", "\u9000\u8d27", "\u552e\u540e", "\u6295\u8bc9", "\u5dee\u8bc4", "\u6b3a\u8bc8", "\u98ce\u9669", "\u5c01\u53f7",
        "refund", "return", "complaint", "chargeback", "fraud", "cancel order"
    );
    private static final List<String> DISCOURSE_FILLERS = List.of(
        "\u6211\u7684\u610f\u601d\u662f", "\u6211\u662f\u8fd9\u4e2a\u60f3\u7684", "\u6211\u662f\u8fd9\u6837\u60f3\u7684", "\u6211\u60f3\u8bf4\u7684\u662f",
        "\u5176\u5b9e", "\u5c31\u662f\u8bf4", "\u5c31\u662f", "\u600e\u4e48\u8bf4\u5462", "\u600e\u4e48\u8bf4",
        "\u7136\u540e", "\u90a3\u4e2a", "\u8fd9\u4e2a", "\u6069", "\u554a", "\u5450", "\u563f",
        "i mean", "my point is", "what i mean is", "you know", "actually", "like", "well"
    );
    private static final List<String> SCOPE_CURRENT_TERMS = List.of(
        "\u8fd9\u4e2a", "\u8fd9\u6b3e", "\u8fd9\u4ef6", "\u5b83", "\u5c31\u8fd9\u4e2a", "\u5f53\u524d\u8fd9\u4e2a", "\u5c31\u5b83",
        "this one", "this item", "it", "current one"
    );
    private static final List<String> SCOPE_OTHER_TERMS = List.of(
        "\u53e6\u4e00\u4e2a", "\u53e6\u4e00\u4ef6", "\u6362\u4e00\u4e2a", "\u6362\u4e2a\u522b\u7684", "\u522b\u7684\u90a3\u4e2a", "\u5176\u4ed6\u90a3\u4e2a",
        "\u7b2c\u4e8c\u4e2a", "\u4e0b\u4e00\u4e2a", "\u4e0a\u4e00\u4e2a", "\u524d\u4e00\u4e2a",
        "another one", "other one", "different one", "next one", "previous one", "second one"
    );
    private static final List<String> SCOPE_BATCH_TERMS = List.of(
        "\u539f\u6765\u90a3\u4e9b", "\u521a\u624d\u90a3\u6279", "\u524d\u9762\u90a3\u4e9b", "\u90a3\u51e0\u4e2a", "\u90fd\u7b97\u4e0a", "\u5168\u90e8\u5019\u9009",
        "those before", "the previous batch", "earlier ones", "all candidates"
    );
    private static final List<String> SCOPE_RESET_TERMS = List.of(
        "\u6362\u4e00\u6279", "\u91cd\u65b0\u63a8\u8350", "\u91cd\u6765", "\u4e0d\u6309\u521a\u624d\u7684", "\u91cd\u65b0\u5f00\u59cb",
        "new batch", "recommend again", "start over", "reset"
    );

    private final ShopProductService shopProductService;
    private final ShopCartService shopCartService;
    private final ShopOrderService shopOrderService;
    private final ElasticsearchProductSearchService elasticsearchProductSearchService;
    private final SearchAliasLexiconMapper searchAliasLexiconMapper;
    private final QueryNormalizationLexiconMapper queryNormalizationLexiconMapper;

    private final boolean aiEnabled;
    private final boolean llmPrimaryModeEnabled;
    private final boolean contextInheritLlmEnabled;
    private final boolean forceLlmEveryTurn;
    private final boolean milvusConfigured;
    private final boolean observabilityEnabled;
    private final int topK;
    private final double ragMinScore;
    private final String configuredChatBaseUrl;
    private final String configuredChatModelName;
    private final String configuredChatApiKeyHint;

    private final EmbeddingModel embeddingModel;
    private final ChatModel chatModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    private final Map<Long, ProductVO> productCache = new LinkedHashMap<>();
    private final Map<Long, ConversationMemory> memoryStore = new ConcurrentHashMap<>();
    private final List<Map.Entry<String, String>> normalizationRules = new ArrayList<>();
    private final Set<String> catalogTopicTokens = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> catalogTopicFreq = new ConcurrentHashMap<>();
    private final Map<String, List<String>> aliasExpansionMap = new ConcurrentHashMap<>();
    private volatile long normalizationRulesLastLoadAt = 0L;
    private volatile long productCacheLoadedAt = 0L;
    private volatile boolean cacheLoaded = false;
    private volatile boolean indexed = false;
    private volatile boolean indexingInProgress = false;
    private volatile boolean esSyncInProgress = false;
    private volatile String lastError = "";
    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong emptyResultCount = new AtomicLong(0);
    private final AtomicLong clarifyCount = new AtomicLong(0);
    private final AtomicLong handoffCount = new AtomicLong(0);
    private final AtomicLong returnedProductSum = new AtomicLong(0);
    private final AtomicLong llmChatCallsTotal = new AtomicLong(0);
    private final AtomicLong llmChatCallErrors = new AtomicLong(0);
    private final Map<String, AtomicLong> llmChatCallsByScene = new ConcurrentHashMap<>();
    private final EnumMap<AgentActionRouter.FrameAction, AtomicLong> actionCounters = initActionCounters();
    private final EnumMap<ScopeType, AtomicLong> scopeCounters = initScopeCounters();
    private final EnumMap<IntentType, AtomicLong> intentCounters = initIntentCounters();
    private final AgentActionRouter actionRouter = new AgentActionRouter();
    private final AgentActionExecutorRegistry actionExecutorRegistry = new AgentActionExecutorRegistry();

    public LangChain4jShoppingAgentService(
        ShopProductService shopProductService,
        ShopCartService shopCartService,
        ShopOrderService shopOrderService,
        ElasticsearchProductSearchService elasticsearchProductSearchService,
        SearchAliasLexiconMapper searchAliasLexiconMapper,
        QueryNormalizationLexiconMapper queryNormalizationLexiconMapper,
        @Value("${app.ai.enabled:false}") boolean aiEnabled,
        @Value("${app.ai.llm-primary-mode:false}") boolean llmPrimaryModeEnabled,
        @Value("${app.ai.context-inherit-llm.enabled:true}") boolean contextInheritLlmEnabled,
        @Value("${app.ai.force-llm-every-turn:false}") boolean forceLlmEveryTurn,
        @Value("${app.ai.top-k:6}") int topK,
        @Value("${app.ai.openai-chat-api-key:}") String chatApiKey,
        @Value("${app.ai.openai-embedding-api-key:}") String embeddingApiKey,
        @Value("${app.ai.chat-model:gpt-4o-mini}") String chatModelName,
        @Value("${app.ai.embedding-model:text-embedding-3-small}") String embeddingModelName,
        @Value("${app.ai.openai-chat-base-url:}") String chatBaseUrl,
        @Value("${app.ai.openai-embedding-base-url:}") String embeddingBaseUrl,
        @Value("${app.ai.rag.min-score:0.45}") double ragMinScore,
        @Value("${app.ai.milvus.enabled:true}") boolean milvusEnabled,
        @Value("${app.ai.milvus.host:localhost}") String milvusHost,
        @Value("${app.ai.milvus.port:19530}") int milvusPort,
        @Value("${app.ai.milvus.collection:products_rag}") String milvusCollection,
        @Value("${app.ai.milvus.dimension:1536}") int milvusDimension,
        @Value("${app.ai.observability.enabled:true}") boolean observabilityEnabled
    ) {
        this.shopProductService = shopProductService;
        this.shopCartService = shopCartService;
        this.shopOrderService = shopOrderService;
        this.elasticsearchProductSearchService = elasticsearchProductSearchService;
        this.searchAliasLexiconMapper = searchAliasLexiconMapper;
        this.queryNormalizationLexiconMapper = queryNormalizationLexiconMapper;
        boolean chatEnabled = aiEnabled && hasText(chatApiKey);
        boolean embeddingEnabled = aiEnabled && hasText(embeddingApiKey);
        this.aiEnabled = aiEnabled && (chatEnabled || embeddingEnabled);
        this.llmPrimaryModeEnabled = llmPrimaryModeEnabled;
        this.contextInheritLlmEnabled = contextInheritLlmEnabled;
        this.forceLlmEveryTurn = forceLlmEveryTurn;
        this.milvusConfigured = milvusEnabled;
        this.observabilityEnabled = observabilityEnabled;
        this.topK = Math.max(1, topK);
        this.ragMinScore = Math.max(0.0, Math.min(1.0, ragMinScore));
        this.configuredChatBaseUrl = nvl(chatBaseUrl);
        this.configuredChatModelName = nvl(chatModelName);
        this.configuredChatApiKeyHint = maskKey(chatApiKey);

        if (chatEnabled) {
            OpenAiChatModel.OpenAiChatModelBuilder chatBuilder = OpenAiChatModel.builder()
                .apiKey(chatApiKey)
                .modelName(chatModelName);
            if (hasText(chatBaseUrl)) {
                chatBuilder.baseUrl(chatBaseUrl);
            }
            this.chatModel = chatBuilder.build();
        } else {
            this.chatModel = null;
        }

        if (embeddingEnabled) {
            OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder embeddingBuilder = OpenAiEmbeddingModel.builder()
                .apiKey(embeddingApiKey)
                .modelName(embeddingModelName);
            if (hasText(embeddingBaseUrl)) {
                embeddingBuilder.baseUrl(embeddingBaseUrl);
            }
            this.embeddingModel = embeddingBuilder.build();
            this.embeddingStore = milvusEnabled
                ? buildMilvusStoreOrFallback(milvusHost, milvusPort, milvusCollection, milvusDimension)
                : new InMemoryEmbeddingStore<>();
        } else {
            this.embeddingModel = null;
            this.embeddingStore = null;
        }
    }

    @PostConstruct
    public void init() {
        try {
            loadNormalizationDict();
            ensureProductCacheLoaded();
            syncElasticsearchAsync();
            startIndexingAsync();
        } catch (Exception ex) {
            lastError = "init fallback: " + ex.getMessage();
            System.err.println("[AI Agent] " + lastError);
        }
    }

    @Override
    public AgentReply reply(String message, Long userId) {
        requestCount.incrementAndGet();
        String prompt = normalizePromptForIntent(message == null ? "" : message.trim());
        ConversationMemory memory = memoryFor(userId);
        ensureProductCacheLoaded();
        syncElasticsearchAsync();
        startIndexingAsync();
        String lowered = prompt.toLowerCase(Locale.ROOT);
        boolean eatScenarioPrompt = isEatScenarioPrompt(prompt, lowered, memory);
        memory.lastEatScenario = eatScenarioPrompt;
        maybeForceLlmEveryTurn(prompt);

        if (prompt.isBlank()) {
            return finalizeReply(memory, prompt, new AgentReply("\u6211\u6536\u5230\u4e86\uff0c\u4f60\u53ef\u4ee5\u76f4\u63a5\u8bf4\u9700\u6c42\uff0c\u4f8b\u5982\uff1a\u63a8\u8350\u4fbf\u5b9c\u978b\u5b50 / \u63a8\u8350\u8033\u673a 300 \u5143\u5185\u3002", List.of()));
        }

        if (isTopicEndPrompt(lowered)) {
            memory.clearContext();
            return finalizeReply(memory, prompt, new AgentReply("\u597d\u7684\uff0c\u5df2\u4e3a\u4f60\u7ed3\u675f\u5f53\u524d\u8bdd\u9898\u3002\u9700\u8981\u65f6\u53ef\u4ee5\u76f4\u63a5\u8bf4\uff1a\u63a8\u8350\u8033\u673a / \u63a8\u8350\u978b\u5b50 / \u63a8\u8350\u4fbf\u5b9c\u5546\u54c1\u3002", List.of()));
        }
        if (isTopicResetPrompt(lowered)) {
            memory.clearContext();
            return finalizeReply(memory, prompt, new AgentReply("\u5df2\u4e3a\u4f60\u91cd\u7f6e\u4e0a\u4e0b\u6587\u3002\u8bf7\u76f4\u63a5\u8bf4\u65b0\u9700\u6c42\uff0c\u4f8b\u5982\uff1a\u63a8\u8350 300 \u5143\u5185\u7684\u84dd\u7259\u8033\u673a\u3002", List.of()));
        }
        DialogAct dialogAct = detectDialogAct(prompt, memory);
        SemanticFrame frame = parseSemanticFrame(prompt, memory, dialogAct);
        LlmPrimaryFrame primaryFrame = maybeInferPrimaryFrameByLlm(prompt, memory);
        if (primaryFrame != null) {
            frame = mergePrimaryFrame(frame, primaryFrame, prompt);
        }
        memory.lastFrameAction = frame.actionType();
        memory.lastScopeType = frame.scopeType();

        if (isGreeting(prompt)) {
            memory.stage = ConversationStage.IDLE;
            return finalizeReply(memory, prompt, new AgentReply("\u4f60\u597d\uff0c\u6211\u662f\u4f60\u7684\u8d2d\u7269\u52a9\u624b\u3002\u4f60\u53ef\u4ee5\u8bf4\uff1a\u63a8\u8350\u8033\u673a\u3001\u63a8\u8350\u4fbf\u5b9c\u5546\u54c1\u3001\u52a0\u5165\u8d2d\u7269\u8f66 \u5546\u54c1ID 123 \u6570\u91cf 2\u3002", List.of()));
        }
        AgentReply conversationalReply = handleConversationOnly(prompt);
        if (conversationalReply != null) {
            return finalizeReply(memory, prompt, conversationalReply);
        }
        AgentReply memoryReply = handleMemoryRecall(prompt, memory);
        if (memoryReply != null) {
            return finalizeReply(memory, prompt, memoryReply);
        }
        AgentReply recommendOneReply = handleRecommendOneFromLastShown(prompt, memory);
        if (recommendOneReply != null) {
            memory.stage = ConversationStage.BROWSING;
            return finalizeReply(memory, prompt, recommendOneReply);
        }
        AgentReply refinementFromShown = handlePreferenceRefinementFromLastShown(prompt, memory);
        if (refinementFromShown != null) {
            memory.stage = ConversationStage.BROWSING;
            return finalizeReply(memory, prompt, refinementFromShown);
        }
        AgentReply categoryDropReply = handleCategoryDropWithoutReplacement(prompt, memory);
        if (categoryDropReply != null) {
            memory.stage = ConversationStage.IDLE;
            return finalizeReply(memory, prompt, categoryDropReply);
        }
        AgentReply accessoryFollowUpReply = handleAccessoryFollowUpFromLastIntent(prompt, memory);
        if (accessoryFollowUpReply != null) {
            memory.stage = ConversationStage.BROWSING;
            return finalizeReply(memory, prompt, accessoryFollowUpReply);
        }
        boolean bypassActionHandlers = shouldBypassActionHandlersForIntentSwitch(prompt, memory, frame.topicIntent());
        AgentReply routedActionReply = bypassActionHandlers ? null : dispatchActionHandlers(frame, prompt, userId, memory);
        if (routedActionReply != null) {
            memory.stage = ConversationStage.DECIDING;
            return finalizeReply(memory, prompt, routedActionReply);
        }
        AgentReply handoffReply = handleHumanHandoff(prompt);
        if (handoffReply != null) {
            handoffCount.incrementAndGet();
            maybeLogMetrics();
            return finalizeReply(memory, prompt, handoffReply);
        }

        boolean noShownContext = memory.lastShownProductIds == null || memory.lastShownProductIds.isEmpty();
        boolean explicitSearchNeed = isExplicitNewNeedPrompt(prompt) || detectIntent(prompt) != IntentType.NONE;
        if ((frame.actionType() == AgentActionRouter.FrameAction.COMPARE
            || frame.actionType() == AgentActionRouter.FrameAction.ATTRIBUTE
            || frame.actionType() == AgentActionRouter.FrameAction.INTRO)
            && noShownContext
            && !explicitSearchNeed) {
            if ((frame.actionType() == AgentActionRouter.FrameAction.COMPARE || frame.actionType() == AgentActionRouter.FrameAction.ATTRIBUTE)
                && detectIntent(prompt) == IntentType.NONE) {
                return finalizeReply(
                    memory,
                    prompt,
                    new AgentReply(
                        "\u6211\u8fd8\u4e0d\u77e5\u9053\u4f60\u8981\u6bd4\u8f83\u54ea\u4e00\u7c7b\u5546\u54c1\u3002\u5148\u6709\u4e00\u6279\u5546\u54c1\uff0c\u6bd4\u5982\u5148\u8bf4\uff1a\u63a8\u8350\u8033\u673a / \u63a8\u8350\u88ab\u5b50\uff0c\u7136\u540e\u6211\u518d\u6309\u4ef7\u683c\u3001\u8bc4\u5206\u3001\u9500\u91cf\u5e2e\u4f60\u6bd4\u3002",
                        List.of()
                    )
                );
            }
            return finalizeReply(
                memory,
                prompt,
                new AgentReply(
                    "\u6211\u9700\u8981\u5148\u6709\u4e00\u6279\u5546\u54c1\u624d\u80fd\u5e2e\u4f60\u6bd4\u8f83/\u4ecb\u7ecd\u3002\u4f60\u53ef\u4ee5\u5148\u8bf4\uff1a\u63a8\u8350\u8033\u673a /\u63a8\u8350\u978b\u5b50\u3002",
                    List.of()
                )
            );
        }
        boolean preferPreferenceSearch = shouldTreatAsPreferenceSearch(prompt);
        boolean preferCheaperSearch = shouldTreatAsCheaperSearch(prompt);
        AgentReply actionFallback = preferPreferenceSearch ? null : handleFrameActionFallback(frame, prompt, memory);
        if (actionFallback != null) {
            memory.stage = ConversationStage.DECIDING;
            return finalizeReply(memory, prompt, actionFallback);
        }
        IntentType intent = frame.topicIntent();
        List<String> promptTopicTokens = extractPromptTopicTokens(prompt);
        boolean forceNewSearch = shouldForceNewSearchScope(prompt, promptTopicTokens);
        boolean anchoredFollowUp = frame.scopeType() == ScopeType.LAST_RESULTS && !forceNewSearch;
        boolean strictAnchoredTopic = anchoredFollowUp && hasText(memory.lastTopicHint);
        boolean strictPromptTopic = intent == IntentType.NONE && !promptTopicTokens.isEmpty();
        boolean strictTopicMode = strictAnchoredTopic || strictPromptTopic;
        String strictTopicHint = strictAnchoredTopic ? memory.lastTopicHint : String.join(" ", promptTopicTokens);
        if (dialogAct == DialogAct.CONFIRM && (memory.lastShownProductIds == null || memory.lastShownProductIds.isEmpty())) {
            return finalizeReply(memory, prompt, new AgentReply("\u6211\u8fd8\u6ca1\u6709\u53ef\u786e\u8ba4\u7684\u4e0a\u4e00\u6279\u5546\u54c1\u3002\u4f60\u53ef\u4ee5\u5148\u8bf4\uff1a\u63a8\u8350\u978b\u5b50 / \u63a8\u8350\u8033\u673a\u3002", List.of()));
        }
        String effectivePrompt = (anchoredFollowUp && !memory.lastTopicHint.isBlank())
            ? (prompt + " " + memory.lastTopicHint)
            : prompt;
        QueryConstraints constraints = mergeWithMemoryConstraints(parseConstraints(effectivePrompt), memory, effectivePrompt);
        boolean cheaperFollowUp = frame.cheaperFollowUp() || preferCheaperSearch;
        AgentReply clarifying = maybeAskClarifyingQuestion(prompt, intent, constraints);
        if (clarifying != null) {
            clarifyCount.incrementAndGet();
            maybeLogMetrics();
            return finalizeReply(memory, prompt, clarifying);
        }
        String retrievalNote = "";

        List<ProductVO> candidates = List.of();
        boolean proactiveDbHit = false;
        if (cheaperFollowUp) {
            candidates = cheaperCandidatesForFollowUp(effectivePrompt, constraints, intent, memory);
            if (!candidates.isEmpty()) {
                retrievalNote = "\u5df2\u57fa\u4e8e\u4e0a\u4e00\u6279\u4ef7\u683c\uff0c\u4f18\u5148\u4e3a\u4f60\u6362\u6210\u66f4\u4fbf\u5b9c\u7684\u4e00\u6279\u5019\u9009\u3002";
            }
        }
        if (candidates.isEmpty()) {
            candidates = proactiveDbRetrieve(effectivePrompt, topK * 6);
            proactiveDbHit = !candidates.isEmpty();
        }
        if (candidates.isEmpty() && anchoredFollowUp && intent != IntentType.NONE) {
            candidates = strictIntentRetrieve(intent, new LinkedHashSet<>(memory.lastShownProductIds)).stream().limit(topK).toList();
            if (candidates.isEmpty()) {
                candidates = strictIntentRetrieve(intent).stream().limit(topK).toList();
            }
        }
        if (candidates.isEmpty() && intent != IntentType.NONE) {
            // Prefer intent-first retrieval to avoid ES returning unrelated but high-score noisy items.
            candidates = strictIntentRetrieve(intent).stream().limit(topK).toList();
        }
        if (candidates.isEmpty() && intent == IntentType.NONE) {
            candidates = dbFuzzyRetrieve(effectivePrompt, topK * 3);
        }
        if (candidates.isEmpty()) {
            candidates = esFirstRetrieve(effectivePrompt);
        }
        if (candidates.isEmpty()) {
            candidates = intent != IntentType.NONE ? strictIntentRetrieve(intent).stream().limit(topK).toList() : intentFirstRetrieve(effectivePrompt);
        }
        if (candidates.isEmpty()) {
            candidates = aiEnabled ? fastHybridRetrieve(effectivePrompt) : keywordRetrieve(effectivePrompt);
        }
        if (candidates.isEmpty() && (prompt == null || prompt.isBlank())) {
            candidates = shopProductService.listProducts(null, null).stream().limit(4).toList();
        }
        if (eatScenarioPrompt) {
            candidates = filterEdibleCandidates(candidates);
            if (candidates.isEmpty()) {
                candidates = strictFoodCandidates(topK * 4).stream().limit(topK).toList();
            }
        }
        // Validation is mainly for ambiguous free-text queries.
        // For explicit intents (e.g. "闂傚倸鍊搁崐鎼佀囬鐑嗘晞闁告稑鐡ㄩ崑?/"闂傚倷绀侀崥瀣垔閽樺鏋嶉柡鍥╁亹閺€锕傛煃?), over-strict lexical validation can drop correct items.
        if (intent == IntentType.NONE && !proactiveDbHit) {
            candidates = validateCandidates(effectivePrompt, candidates);
        }
        if (strictTopicMode) {
            candidates = filterByTopicHint(candidates, strictTopicHint);
        }
        candidates = applyConstraints(candidates, constraints);
        candidates = rerankCandidates(effectivePrompt, constraints, candidates, intent).stream().limit(topK).toList();
        if (strictTopicMode) {
            candidates = filterByTopicHint(candidates, strictTopicHint);
        }
        if (candidates.isEmpty() && intent != IntentType.NONE) {
            // Relax validation once for detected intents, so broad asks like "闂傚倸鍊搁崐鎼佸磹閹间礁纾归柟闂寸绾惧湱鈧懓瀚崳纾嬨亹閹烘垹鍊炲銈嗗笒閿曪妇绮欒箛鏃傜瘈闁靛骏绲剧涵鐐亜閹存繃鍠橀柕鍡楁嚇楠炴捇骞戝Δ鈧紞濠囧箖閳轰緡鍟呮い鏃傚帶婢瑰牏绱撻崒娆掝唹闁稿鎸搁…鍧楁嚋闂堟稑顫庨梺鍛婄懄閹瑰洭寮诲☉銏犖ㄩ柨婵嗘噹椤鏌ｈ箛鎾剁闁轰礁顭峰濠氭晸閻樻彃鑰垮銈嗘尪閸ㄦ椽顢撳澶嬧拺闂侇偅绋撻埞鎺楁煕閺傝法鐒搁柍銉︽瀹曟﹢顢旈崨顓犲酱闂傚倸顭崑鎺楀储婵傛潌澶婎潩閼哥鎷婚梺绋挎湰閻燂妇绮婇悧鍫涗簻闁哄洨鍠撴晶鐢碘偓瑙勬磻閸楀啿顕ｉ幘顔藉亹闁圭粯甯炴禍鐗堢節閻㈤潧浠滄俊顐ｎ殘閸掓帡骞嗚濡插牓鏌曡箛鏇炐ユい锔哄姂濮婃椽妫冨☉杈╁姼闂佹悶鍔嶅浠嬫晲閻愬樊鍚嬮柛娑变簼閺傗偓婵＄偑鍊栧褰掑几缂佹鐟规繛鎴欏灪閻撴洘淇婇姘础闁活厽鐟ч埀顒冾潐濞诧箓宕归懜鍨弿闁逞屽墴閺岋絽顫滈崱妞剧凹闂佸搫鍊甸崑鎾绘⒒閸屾瑨鍏岀紒顕呭灦瀹曟繂螣闂傚鍓ㄥ┑鐐叉閹稿摜绮诲ú顏呯厽婵☆垰鍚嬮弳鈺冪棯閹冩倯闁靛洤瀚伴獮鍥煛娴ｈ桨鎮ｉ梻渚€鈧偛鑻晶顕€鏌ｈ箛鏃傜疄闁诡喗鍎抽悾锟犳焽閿旇棄缂撻梻渚€鈧偛鑻晶瀵糕偓瑙勬礃濡炰粙寮幘缁樺亹闁肩⒈鍓ㄧ槐鍙夌節濞堝灝鏋熼柨鏇楁櫊瀹曟粌鈻庨幘? do not drop to empty.
            candidates = applyConstraints(strictIntentRetrieve(intent).stream().limit(topK * 3L).toList(), constraints);
            candidates = rerankCandidates(effectivePrompt, constraints, candidates, intent).stream().limit(topK).toList();
            if (strictTopicMode) {
                candidates = filterByTopicHint(candidates, strictTopicHint);
            }
        }
        if (candidates.isEmpty() && (
            constraints.sortPref() != SortPref.DEFAULT
                || constraints.minPrice() != null
                || constraints.maxPrice() != null
        )) {
            if (intent != IntentType.NONE) {
                candidates = applyConstraints(strictIntentRetrieve(intent).stream().limit(topK * 8L).toList(), constraints);
            } else {
                candidates = fallbackGlobalByConstraints(constraints);
            }
            candidates = rerankCandidates(effectivePrompt, constraints, candidates, intent).stream().limit(topK).toList();
            if (strictTopicMode) {
                candidates = filterByTopicHint(candidates, strictTopicHint);
            }
        }
        if (candidates.isEmpty() && !strictTopicMode) {
            candidates = layeredFallback(effectivePrompt, constraints, intent);
        }
        if (candidates.isEmpty() && !strictTopicMode) {
            RelaxationResult relaxed = recoverByRelaxing(effectivePrompt, constraints, intent);
            if (relaxed != null && relaxed.candidates() != null && !relaxed.candidates().isEmpty()) {
                candidates = relaxed.candidates();
                retrievalNote = nvl(relaxed.note());
            }
        }
        if (shouldDiversifyFollowUp(prompt, anchoredFollowUp, memory)) {
            FreshResult fresh = freshCandidatesForFollowUp(effectivePrompt, constraints, intent, memory, candidates);
            if (fresh != null && fresh.candidates() != null) {
                if (!fresh.candidates().isEmpty() || !nvl(fresh.note()).isBlank()) {
                    candidates = fresh.candidates();
                    if (!nvl(fresh.note()).isBlank()) {
                        retrievalNote = fresh.note();
                    }
                    if (strictTopicMode) {
                        candidates = filterByTopicHint(candidates, strictTopicHint);
                    }
                }
            }
        }
        boolean strictNoRepeatFollowUp = isStrictNoRepeatFollowUpEnabled() && anchoredFollowUp && isFollowUpQuery(prompt);
        if (strictNoRepeatFollowUp && memory != null && memory.lastShownProductIds != null && !memory.lastShownProductIds.isEmpty()) {
            Set<Long> lastBatchSeen = new LinkedHashSet<>(memory.lastShownProductIds);
            List<ProductVO> deduped = removeSeen(candidates, lastBatchSeen);
            if (!deduped.isEmpty()) {
                candidates = deduped;
            }
        }
        if (shouldFailClosedForSpecificQuery(prompt, anchoredFollowUp, candidates)) {
            List<ProductVO> direct = directRetrieveByPrompt(prompt, topK * 3);
            if (!direct.isEmpty()) {
                candidates = applyConstraints(direct, constraints);
                candidates = rerankCandidates(effectivePrompt, constraints, candidates, intent).stream().limit(topK).toList();
                if (strictTopicMode) {
                    candidates = filterByTopicHint(candidates, strictTopicHint);
                }
            } else {
                candidates = List.of();
            }
        }
        candidates = enforceStrictIntentCandidates(intent, candidates);
        candidates = filterCandidatesByPromptSubject(prompt, candidates);
        AgentReply precisionClarify = maybeAskLowConfidenceClarify(prompt, intent, candidates);
        if (precisionClarify != null) {
            clarifyCount.incrementAndGet();
            maybeLogMetrics();
            return finalizeReply(memory, prompt, precisionClarify);
        }
        if (candidates.isEmpty()) {
            emptyResultCount.incrementAndGet();
            maybeLogMetrics();
            if (eatScenarioPrompt) {
                memory.lastShownProductIds = List.of();
                memory.lastTopicHint = "";
                memory.stage = ConversationStage.IDLE;
                return finalizeReply(
                    memory,
                    prompt,
                    new AgentReply("\u5f53\u524d\u5019\u9009\u5546\u54c1\u91cc\u6682\u65f6\u6ca1\u6709\u53ef\u76f4\u63a5\u98df\u7528\u7684\u98df\u54c1\u3002\u4f60\u53ef\u4ee5\u8bf4\uff1a\u96f6\u98df / \u996e\u6599 / \u86cb\u767d\u7c89\uff0c\u6211\u518d\u91cd\u65b0\u5e2e\u4f60\u7b5b\u4e00\u6279\u3002", List.of())
                );
            }
            // Only use "based on previous topic" wording when the query is truly anchored to last round.
            if (strictAnchoredTopic) {
                return finalizeReply(
                    memory,
                    prompt,
                    new AgentReply("\u57fa\u4e8e\u4f60\u4e0a\u4e00\u8f6e\u7684\u4e3b\u9898\uff0c\u6682\u65f6\u6ca1\u627e\u5230\u66f4\u591a\u540c\u7c7b\u5546\u54c1\u3002\u4f60\u53ef\u4ee5\u8bf4\uff1a\u6362\u4e2a\u5173\u952e\u8bcd / \u6362\u4e2a\u9884\u7b97 / \u6362\u4e2a\u7c7b\u578b\u3002", List.of())
                );
            }
            if (cheaperFollowUp) {
                return finalizeReply(
                    memory,
                    prompt,
                    new AgentReply("\u57fa\u4e8e\u4f60\u4e0a\u4e00\u8f6e\u7684\u5019\u9009\u5546\u54c1\uff0c\u6682\u65f6\u6ca1\u627e\u5230\u66f4\u4fbf\u5b9c\u7684\u540c\u7c7b\u9009\u9879\u3002\u4f60\u53ef\u4ee5\u8bf4\uff1a\u9884\u7b97\u518d\u63d0\u9ad8\u4e00\u70b9 / \u6362\u4e2a\u54c1\u724c / \u4e8c\u624b\u6216\u5165\u95e8\u6b3e\u3002", List.of())
                );
            }
            String hint = specificExampleForEmptyResult(prompt, intent, memory);
            return finalizeReply(memory, prompt, new AgentReply("\u6682\u65f6\u6ca1\u627e\u5230\u5339\u914d\u5546\u54c1\u3002\u4f60\u53ef\u4ee5\u8bf4\u5f97\u66f4\u5177\u4f53\u4e00\u4e9b\uff0c\u4f8b\u5982\uff1a" + hint + "\u3002", List.of()));
        }

        String actionResult = handleAddToCartIntent(prompt, userId, candidates);
        List<ProductVO> cards = candidates.stream().limit(constraints.requestedCount()).toList();
        String content = buildReplyText(prompt, cards, actionResult, constraints);
        if (!retrievalNote.isBlank()) {
            content = retrievalNote + "\n" + content;
        }
        rememberTurnContext(memory, intent, cards, constraints);
        memory.stage = (actionResult != null && !actionResult.isBlank()) ? ConversationStage.DECIDING : ConversationStage.BROWSING;
        maybeLogMetrics();
        return finalizeReply(memory, prompt, new AgentReply(content, cards));
    }

    private boolean isTopicEndPrompt(String lowered) {
        if (lowered == null || lowered.isBlank()) {
            return false;
        }
        return containsAny(
            lowered,
            "\u4e0d\u7528\u4e86", "\u4e0d\u9700\u8981\u4e86", "\u7b97\u4e86", "\u5148\u8fd9\u6837", "\u5148\u5230\u8fd9",
            "\u5148\u4e0d\u770b\u4e86", "\u7ed3\u675f", "\u5bf9\u8bdd\u7ed3\u675f", "\u5c31\u8fd9\u6837", "\u597d\u4e86",
            "stop", "that's all", "thats all", "no more", "end chat", "end conversation", "done here"
        );
    }

    private boolean isTopicResetPrompt(String lowered) {
        if (lowered == null || lowered.isBlank()) {
            return false;
        }
        return containsAny(
            lowered,
            "\u91cd\u65b0\u5f00\u59cb", "\u91cd\u7f6e", "\u91cd\u65b0\u6765", "\u6362\u4e2a\u8bdd\u9898", "\u4e0d\u770b\u8fd9\u4e2a\u4e86", "\u770b\u70b9\u522b\u7684",
            "reset", "restart", "new topic", "change topic", "start over"
        );
    }

    private DialogAct detectDialogAct(String prompt, ConversationMemory memory) {
        String lowered = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        if (isAddToCartIntent(lowered)
            || isCheckoutIntent(lowered)
            || isIncrementIntent(lowered)
            || isBatchAddIntent(lowered)
            || isRemoveCartIntent(lowered)
            || isCartQueryIntent(lowered)
            || isClearCartIntent(lowered)
            || isReplaceProductIntent(lowered)) {
            return DialogAct.ACTION;
        }
        if (isConfirmationLike(lowered)) {
            return DialogAct.CONFIRM;
        }
        if (isContextualShortFollowUp(prompt, memory)) {
            return DialogAct.FOLLOW_UP;
        }
        if (memory != null
            && memory.lastShownProductIds != null
            && !memory.lastShownProductIds.isEmpty()
            && isPreferenceOnlyPrompt(lowered)
            && !hasScopeResetCue(lowered)) {
            return DialogAct.FOLLOW_UP;
        }
        if (isExplicitNewNeedPrompt(prompt)) {
            return DialogAct.SEARCH;
        }
        if (isFollowUpQuery(prompt) || isCheaperFollowUp(prompt) || isSwitchBrandFollowUp(prompt) || isSameBrandFollowUp(prompt) || isComparativeFollowUp(prompt)) {
            return DialogAct.FOLLOW_UP;
        }
        if (isGreeting(prompt)) {
            return DialogAct.CHITCHAT;
        }
        if (memory != null && memory.stage == ConversationStage.BROWSING && detectIntent(prompt) == IntentType.NONE) {
            if (hasScopeResetCue(lowered)) {
                return DialogAct.SEARCH;
            }
            if (looksLikeFreshSearch(prompt)) {
                return DialogAct.SEARCH;
            }
            return DialogAct.FOLLOW_UP;
        }
        return DialogAct.SEARCH;
    }

    // Parse user utterance into a compact semantic frame (topic + scope),
    // then downstream retrieval/execution runs against this frame instead of raw text heuristics.
    private SemanticFrame parseSemanticFrame(String prompt, ConversationMemory memory, DialogAct dialogAct) {
        IntentType detectedIntent = detectIntent(prompt);
        LlmSemanticHint llmHint = maybeInferSemanticHintByLlm(prompt, memory, dialogAct, detectedIntent);
        LlmContextDecision contextDecision = maybeInferContextDecisionByLlm(prompt, memory, dialogAct, detectedIntent);
        if (detectedIntent == IntentType.NONE && llmHint.intent() != IntentType.NONE) {
            detectedIntent = llmHint.intent();
        }
        String lowered = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        boolean explicitFreshSearch = isExplicitNewNeedPrompt(prompt) && !isPreferenceOnlyPrompt(lowered);
        boolean followUp = isFollowUpQuery(prompt);
        boolean recommendOneFollowUp = isRecommendOneFollowUp(prompt);
        boolean cheaperFollowUp = isCheaperFollowUp(prompt);
        boolean predicateOnlyFollowUp = isPredicateOnlyFollowUp(prompt);
        boolean refinementFollowUp = isRefinementFollowUp(prompt, memory);
        boolean explicitIntentSwitch = isExplicitIntentSwitchPrompt(prompt, memory, detectedIntent);
        boolean preferenceSearch = shouldTreatAsPreferenceSearch(prompt);
        boolean compareTone = (containsAnyTerm(lowered, COMPARE_TERMS) || AgentSemanticParser.containsCompareTone(lowered))
            && !preferenceSearch;
        boolean attributeTone = AgentSemanticParser.containsAttributeTone(lowered);
        boolean introTone = AgentSemanticParser.containsIntroTone(lowered);
        boolean anchoredFollowUp = AgentSemanticParser.isAnchoredFollowUp(
            followUp,
            recommendOneFollowUp,
            cheaperFollowUp,
            isSameBrandFollowUp(prompt),
            isSwitchBrandFollowUp(prompt),
            isComparativeFollowUp(prompt),
            refinementFollowUp,
            predicateOnlyFollowUp,
            dialogAct == DialogAct.FOLLOW_UP,
            dialogAct == DialogAct.CONFIRM,
            explicitIntentSwitch
        );
        if (explicitFreshSearch) {
            anchoredFollowUp = false;
        }
        if (contextDecision.scope() == ScopeType.LAST_RESULTS && !explicitFreshSearch) {
            anchoredFollowUp = true;
        } else if (contextDecision.scope() == ScopeType.NEW_SEARCH) {
            anchoredFollowUp = false;
        }

        IntentType topicIntent = detectedIntent;
        boolean inheritedIntent = false;
        if (topicIntent == IntentType.NONE
            && memory != null
            && shouldInheritIntentFromContext(prompt, lowered, memory, dialogAct, explicitFreshSearch, explicitIntentSwitch)) {
            topicIntent = inferIntentFromMemory(memory);
            inheritedIntent = topicIntent != IntentType.NONE;
        }
        ScopeType scopeType = anchoredFollowUp ? ScopeType.LAST_RESULTS : ScopeType.NEW_SEARCH;
        if (inheritedIntent
            && scopeType == ScopeType.NEW_SEARCH
            && memory != null
            && memory.lastShownProductIds != null
            && !memory.lastShownProductIds.isEmpty()) {
            scopeType = ScopeType.LAST_RESULTS;
        }
        if (!explicitFreshSearch
            && scopeType == ScopeType.NEW_SEARCH
            && llmHint.scope() == ScopeType.LAST_RESULTS
            && memory != null
            && memory.lastShownProductIds != null
            && !memory.lastShownProductIds.isEmpty()
            && !explicitIntentSwitch) {
            scopeType = ScopeType.LAST_RESULTS;
        }
        AgentActionRouter.FrameAction actionType = AgentSemanticParser.detectAction(
            prompt,
            lowered,
            isGreeting(prompt),
            dialogAct,
            isAddToCartIntent(lowered),
            isCheckoutIntent(lowered),
            isIncrementIntent(lowered),
            isBatchAddIntent(lowered),
            isRemoveCartIntent(lowered) || isReplaceProductIntent(lowered),
            isCartQueryIntent(lowered),
            isClearCartIntent(lowered),
            compareTone,
            attributeTone,
            introTone
        );
        if (explicitIntentSwitch) {
            actionType = AgentActionRouter.FrameAction.SEARCH;
            scopeType = ScopeType.NEW_SEARCH;
        }
        if (explicitFreshSearch && topicIntent != IntentType.NONE) {
            actionType = AgentActionRouter.FrameAction.SEARCH;
            scopeType = ScopeType.NEW_SEARCH;
        }
        if (shouldForceSearchActionForRefinement(prompt, memory, detectedIntent, actionType, contextDecision)) {
            actionType = AgentActionRouter.FrameAction.SEARCH;
        }
        return new SemanticFrame(topicIntent, scopeType, cheaperFollowUp, actionType);
    }

    private boolean shouldBypassActionHandlersForIntentSwitch(String prompt, ConversationMemory memory, IntentType currentIntent) {
        if (memory == null) {
            return false;
        }
        return isExplicitIntentSwitchPrompt(prompt, memory, currentIntent);
    }

    private boolean isEatScenarioPrompt(String prompt, String lowered, ConversationMemory memory) {
        String text = lowered == null ? nvl(prompt).toLowerCase(Locale.ROOT) : lowered;
        if (containsAnyTerm(text, EAT_SCENARIO_TERMS)) {
            return true;
        }
        return memory != null && memory.lastEatScenario && isFollowUpQuery(prompt);
    }

    private boolean shouldForceSearchActionForRefinement(
        String prompt,
        ConversationMemory memory,
        IntentType detectedIntent,
        AgentActionRouter.FrameAction actionType,
        LlmContextDecision contextDecision
    ) {
        if (actionType == AgentActionRouter.FrameAction.SEARCH
            || actionType == AgentActionRouter.FrameAction.TRANSACTION
            || actionType == AgentActionRouter.FrameAction.CHITCHAT) {
            return false;
        }
        String lowered = nvl(prompt).toLowerCase(Locale.ROOT);
        if (isExplicitNewNeedPrompt(prompt) || shouldTreatAsPreferenceSearch(prompt) || isRefinementFollowUp(prompt, memory)) {
            return true;
        }
        if (detectedIntent != IntentType.NONE && !isFollowUpQuery(prompt)) {
            return true;
        }
        if (containsAny(
            lowered,
            "\u53ea\u8981", "\u4e0d\u8981\u5957\u88c5", "\u504f\u539a", "\u504f\u8584", "\u539a\u6696", "\u66f4\u6696", "\u8f7b\u8584", "\u900f\u6c14", "\u66f4\u4fbf\u5b9c",
            "\u5927\u53f7\u5e8a", "\u7279\u5927\u53f7\u5e8a", "\u5927\u53f7", "\u7279\u5927\u53f7", "queen", "king",
            "only", "without set", "no set", "thick", "thin", "warmer", "breathable", "cheaper"
        )) {
            return true;
        }
        return contextDecision != null
            && contextDecision.confidence() >= 0.70
            && "SEARCH".equalsIgnoreCase(nvl(contextDecision.action()));
    }

    private LlmSemanticHint maybeInferSemanticHintByLlm(String prompt, ConversationMemory memory, DialogAct dialogAct, IntentType detectedIntent) {
        if (!isSemanticLlmEnabled()) {
            return LlmSemanticHint.EMPTY;
        }
        if (prompt == null || prompt.isBlank() || chatModel == null || !aiEnabled) {
            return LlmSemanticHint.EMPTY;
        }
        if (detectedIntent != IntentType.NONE) {
            return LlmSemanticHint.EMPTY;
        }
        if (dialogAct == DialogAct.CHITCHAT || dialogAct == DialogAct.ACTION) {
            return LlmSemanticHint.EMPTY;
        }
        List<String> tokens = splitQueryTokens(normalizeQuery(prompt));
        int effective = 0;
        for (String t : tokens) {
            if (t == null || t.isBlank() || QUERY_STOP_WORDS.contains(t)) {
                continue;
            }
            if (!containsCjk(t) && t.length() < 3) {
                continue;
            }
            effective++;
        }
        if (effective <= 0 && !isFollowUpQuery(prompt)) {
            return LlmSemanticHint.EMPTY;
        }
        String memoryIntent = memory == null || memory.lastIntent == null ? "NONE" : memory.lastIntent.name();
        String memoryScope = (memory != null && memory.lastShownProductIds != null && !memory.lastShownProductIds.isEmpty())
            ? "LAST_RESULTS"
            : "NEW_SEARCH";
        String schema = "Allowed INTENT: NONE, MOUSE, KEYBOARD, PET, BABY, BOOK, TOY, MAKEUP, HEADPHONE, SHOE, BAG, LIGHT, BIKE, COMPUTER, ELECTRONICS, BEDDING, DAILY.";
        String promptTpl = "Classify shopping user input into intent and scope.\n"
            + schema + "\n"
            + "Allowed SCOPE: NEW_SEARCH or LAST_RESULTS.\n"
            + "Use history hint cautiously. If unclear, choose NONE and NEW_SEARCH.\n"
            + "Output exactly one line: INTENT=<INTENT>;SCOPE=<SCOPE>;CONF=<0-1>\n"
            + "HistoryIntent=" + memoryIntent + ", HistoryScope=" + memoryScope + "\n"
            + "UserInput=" + prompt;
        try {
            String raw = callChatModel("semantic_hint", promptTpl);
            if (raw == null || raw.isBlank()) {
                return LlmSemanticHint.EMPTY;
            }
            String upper = raw.toUpperCase(Locale.ROOT);
            Matcher intentMatcher = LLM_INTENT_PATTERN.matcher(upper);
            Matcher scopeMatcher = LLM_SCOPE_PATTERN.matcher(upper);
            Matcher confMatcher = LLM_CONF_PATTERN.matcher(upper);
            IntentType intent = IntentType.NONE;
            ScopeType scope = ScopeType.NEW_SEARCH;
            double conf = 0.0;
            if (intentMatcher.find()) {
                intent = parseIntentByName(intentMatcher.group(1));
            }
            if (scopeMatcher.find() && "LAST_RESULTS".equals(scopeMatcher.group(1))) {
                scope = ScopeType.LAST_RESULTS;
            }
            if (confMatcher.find()) {
                try {
                    conf = Double.parseDouble(confMatcher.group(1));
                } catch (Exception ignored) {
                    conf = 0.0;
                }
            }
            if (intent == IntentType.NONE || conf < 0.70) {
                return LlmSemanticHint.EMPTY;
            }
            return new LlmSemanticHint(intent, scope, conf);
        } catch (Exception ignored) {
            return LlmSemanticHint.EMPTY;
        }
    }

    private LlmContextDecision maybeInferContextDecisionByLlm(String prompt, ConversationMemory memory, DialogAct dialogAct, IntentType detectedIntent) {
        if (!contextInheritLlmEnabled || prompt == null || prompt.isBlank() || chatModel == null || !aiEnabled) {
            return LlmContextDecision.EMPTY;
        }
        if (dialogAct == DialogAct.ACTION || dialogAct == DialogAct.CHITCHAT) {
            return LlmContextDecision.EMPTY;
        }
        if (memory == null || memory.lastShownProductIds == null || memory.lastShownProductIds.isEmpty()) {
            return LlmContextDecision.EMPTY;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        if ((isExplicitNewNeedPrompt(prompt) && !isPreferenceOnlyPrompt(lowered)) || hasScopeResetCue(lowered)) {
            return LlmContextDecision.EMPTY;
        }
        List<ProductVO> shown = lastShownProducts(memory);
        if (shown.isEmpty()) {
            return LlmContextDecision.EMPTY;
        }
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < Math.min(4, shown.size()); i++) {
            ProductVO p = shown.get(i);
            context.append(i + 1)
                .append(". ID=").append(p.getId())
                .append(", name=").append(nvl(p.getName()))
                .append(", category=").append(nvl(p.getCategory()))
                .append(", price=").append(nvl(String.valueOf(p.getPrice()))).append(" ").append(nvl(p.getCurrency()))
                .append("\n");
        }
        String promptTpl = "You are a shopping dialog scope classifier.\n"
            + "Given user utterance and last shown product candidates, decide whether user means to continue on last results.\n"
            + "Output exactly one line: SCOPE=<LAST_RESULTS|NEW_SEARCH>;ACTION=<CHOOSE|COMPARE|FILTER|SEARCH>;CONF=<0-1>\n"
            + "If uncertain, choose NEW_SEARCH and low confidence.\n"
            + "DetectedIntent=" + detectedIntent.name() + "\n"
            + "UserInput=" + prompt + "\n"
            + "LastCandidates:\n" + context;
        try {
            String raw = callChatModel("context_inherit", promptTpl);
            if (raw == null || raw.isBlank()) {
                return LlmContextDecision.EMPTY;
            }
            String upper = raw.toUpperCase(Locale.ROOT);
            Matcher scopeMatcher = LLM_SCOPE_PATTERN.matcher(upper);
            Matcher actionMatcher = LLM_ACTION_PATTERN.matcher(upper);
            Matcher confMatcher = LLM_CONF_PATTERN.matcher(upper);
            ScopeType scope = ScopeType.NEW_SEARCH;
            String action = "SEARCH";
            double conf = 0.0;
            if (scopeMatcher.find() && "LAST_RESULTS".equals(scopeMatcher.group(1))) {
                scope = ScopeType.LAST_RESULTS;
            }
            if (actionMatcher.find()) {
                action = actionMatcher.group(1);
            }
            if (confMatcher.find()) {
                try {
                    conf = Double.parseDouble(confMatcher.group(1));
                } catch (Exception ignored) {
                    conf = 0.0;
                }
            }
            if (conf < 0.70) {
                return LlmContextDecision.EMPTY;
            }
            return new LlmContextDecision(scope, action, conf);
        } catch (Exception ignored) {
            return LlmContextDecision.EMPTY;
        }
    }

    private LlmPrimaryFrame maybeInferPrimaryFrameByLlm(String prompt, ConversationMemory memory) {
        if (!llmPrimaryModeEnabled || prompt == null || prompt.isBlank() || chatModel == null || !aiEnabled) {
            return null;
        }
        if (isGreeting(prompt) || isTopicEndPrompt(prompt.toLowerCase(Locale.ROOT)) || isTopicResetPrompt(prompt.toLowerCase(Locale.ROOT))) {
            return null;
        }
        String memoryIntent = memory == null || memory.lastIntent == null ? "NONE" : memory.lastIntent.name();
        StringBuilder lastShown = new StringBuilder();
        if (memory != null) {
            List<ProductVO> shown = lastShownProducts(memory);
            for (int i = 0; i < Math.min(3, shown.size()); i++) {
                ProductVO p = shown.get(i);
                lastShown.append(i + 1).append(". ")
                    .append(nvl(p.getName())).append(" / ").append(nvl(p.getCategory()))
                    .append(" / ").append(nvl(String.valueOf(p.getPrice()))).append(" ").append(nvl(p.getCurrency()))
                    .append("\n");
            }
        }
        String promptTpl = "Classify shopping message into a semantic frame.\n"
            + "Allowed INTENT: NONE,MOUSE,KEYBOARD,PET,BABY,BOOK,TOY,MAKEUP,HEADPHONE,SHOE,BAG,LIGHT,BIKE,COMPUTER,ELECTRONICS,BEDDING,DAILY.\n"
            + "Allowed SCOPE: NEW_SEARCH,LAST_RESULTS.\n"
            + "Allowed ACTION: SEARCH,COMPARE,ATTRIBUTE,INTRO,TRANSACTION,CHITCHAT.\n"
            + "Output exactly one line: INTENT=<INTENT>;SCOPE=<SCOPE>;ACTION=<ACTION>;CONF=<0-1>\n"
            + "HistoryIntent=" + memoryIntent + "\n"
            + "LastShown:\n" + lastShown
            + "UserInput=" + prompt;
        try {
            String raw = callChatModel("primary_frame", promptTpl);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String upper = raw.toUpperCase(Locale.ROOT);
            Matcher intentMatcher = LLM_INTENT_PATTERN.matcher(upper);
            Matcher scopeMatcher = LLM_SCOPE_PATTERN.matcher(upper);
            Matcher actionMatcher = LLM_ACTION_PATTERN.matcher(upper);
            Matcher confMatcher = LLM_CONF_PATTERN.matcher(upper);
            IntentType intent = IntentType.NONE;
            ScopeType scope = ScopeType.NEW_SEARCH;
            AgentActionRouter.FrameAction action = AgentActionRouter.FrameAction.SEARCH;
            double conf = 0.0;
            if (intentMatcher.find()) {
                intent = parseIntentByName(intentMatcher.group(1));
            }
            if (scopeMatcher.find() && "LAST_RESULTS".equals(scopeMatcher.group(1))) {
                scope = ScopeType.LAST_RESULTS;
            }
            if (actionMatcher.find()) {
                action = parseActionByName(actionMatcher.group(1));
            }
            if (confMatcher.find()) {
                try {
                    conf = Double.parseDouble(confMatcher.group(1));
                } catch (Exception ignored) {
                    conf = 0.0;
                }
            }
            if (conf < 0.66) {
                return null;
            }
            return new LlmPrimaryFrame(intent, scope, action, conf);
        } catch (Exception ignored) {
            return null;
        }
    }

    private SemanticFrame mergePrimaryFrame(SemanticFrame base, LlmPrimaryFrame primary, String prompt) {
        if (base == null || primary == null) {
            return base;
        }
        IntentType intent = primary.intent() == null || primary.intent() == IntentType.NONE ? base.topicIntent() : primary.intent();
        ScopeType scope = primary.scope() == null ? base.scopeType() : primary.scope();
        AgentActionRouter.FrameAction action = primary.action() == null ? base.actionType() : primary.action();
        String lowered = nvl(prompt).toLowerCase(Locale.ROOT);
        // Safety hard-override for transactional keywords.
        if (isAddToCartIntent(lowered)
            || isCheckoutIntent(lowered)
            || isIncrementIntent(lowered)
            || isBatchAddIntent(lowered)
            || isRemoveCartIntent(lowered)
            || isCartQueryIntent(lowered)
            || isClearCartIntent(lowered)
            || isReplaceProductIntent(lowered)) {
            action = AgentActionRouter.FrameAction.TRANSACTION;
        }
        // Follow-up filtering phrases should be treated as search/refinement, not attribute QA.
        if (containsAny(
            lowered,
            "\u53ea\u8981", "\u53ea\u770b", "\u53ea\u7559", "\u4ec5\u8981", "\u4ec5\u770b",
            "\u4e0d\u8981\u5957\u88c5", "\u6392\u9664\u5957\u88c5", "\u53ea\u8981\u88ab\u5b50", "\u8981\u88ab\u5b50",
            "only", "just", "exclude", "without set", "no set"
        )) {
            action = AgentActionRouter.FrameAction.SEARCH;
            if (base.scopeType() == ScopeType.LAST_RESULTS) {
                scope = ScopeType.LAST_RESULTS;
            }
        }
        return new SemanticFrame(intent, scope, base.cheaperFollowUp(), action);
    }

    private IntentType parseIntentByName(String name) {
        if (name == null || name.isBlank()) {
            return IntentType.NONE;
        }
        try {
            return IntentType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return IntentType.NONE;
        }
    }

    private AgentActionRouter.FrameAction parseActionByName(String name) {
        if (name == null || name.isBlank()) {
            return AgentActionRouter.FrameAction.SEARCH;
        }
        try {
            return AgentActionRouter.FrameAction.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return AgentActionRouter.FrameAction.SEARCH;
        }
    }

    private boolean isSemanticLlmEnabled() {
        String raw = System.getProperty("agent.semantic.llm.enabled");
        if (raw == null || raw.isBlank()) {
            raw = System.getenv("AGENT_SEMANTIC_LLM_ENABLED");
        }
        return "true".equalsIgnoreCase(nvl(raw).trim());
    }

    private boolean isStrictNoRepeatFollowUpEnabled() {
        String raw = System.getProperty("agent.followup.strict-no-repeat.enabled");
        if (raw == null || raw.isBlank()) {
            raw = System.getenv("AGENT_FOLLOWUP_STRICT_NO_REPEAT_ENABLED");
        }
        if (raw == null || raw.isBlank()) {
            return true;
        }
        return "true".equalsIgnoreCase(nvl(raw).trim());
    }

    private boolean isRelaxedIntentFollowUpEnabled() {
        String raw = System.getProperty("agent.followup.relaxed-intent.enabled");
        if (raw == null || raw.isBlank()) {
            raw = System.getenv("AGENT_FOLLOWUP_RELAXED_INTENT_ENABLED");
        }
        if (raw == null || raw.isBlank()) {
            return true;
        }
        return "true".equalsIgnoreCase(nvl(raw).trim());
    }

    private AgentReply dispatchActionHandlers(SemanticFrame frame, String prompt, Long userId, ConversationMemory memory) {
        AgentActionRouter.FrameAction action = frame == null ? AgentActionRouter.FrameAction.SEARCH : frame.actionType();
        List<AgentActionRouter.ActionHandlerType> pipeline = actionRouter.pipelineFor(action);
        if (pipeline == null || pipeline.isEmpty()) {
            return null;
        }
        for (AgentActionRouter.ActionHandlerType handler : pipeline) {
            AgentReply reply = actionExecutorRegistry.execute(handler, this, prompt, userId, memory);
            if (reply != null) {
                return reply;
            }
        }
        return null;
    }

    AgentReply executeChoose(String prompt, Long userId, ConversationMemory memory) {
        return handleAgentChooseIntent(prompt, userId, memory);
    }

    AgentReply executeWhyNot(String prompt, ConversationMemory memory) {
        return handleWhyNotChoiceFromLastShown(prompt, memory);
    }

    AgentReply executeReject(String prompt, ConversationMemory memory) {
        return handleRejectCurrentChoice(prompt, memory);
    }

    AgentReply executeTransaction(String prompt, Long userId, ConversationMemory memory) {
        return handleTransactionalIntent(prompt, userId, memory);
    }

    AgentReply executeCompare(String prompt, ConversationMemory memory) {
        return handleCompareFromLastShown(prompt, memory);
    }

    AgentReply executeConfirm(String prompt, ConversationMemory memory) {
        return handleConfirmationFromLastShown(prompt, memory);
    }

    AgentReply executeAttribute(String prompt, ConversationMemory memory) {
        return handleAttributeQuestionFromLastShown(prompt, memory);
    }

    AgentReply executeOtherAttribute(String prompt, ConversationMemory memory) {
        return handleOtherItemsAttributeFollowUp(prompt, memory);
    }

    AgentReply executeIntro(String prompt, ConversationMemory memory) {
        return handleProductIntroFromLastShown(prompt, memory);
    }

    private AgentReply handleFrameActionFallback(SemanticFrame frame, String prompt, ConversationMemory memory) {
        if (frame == null || memory == null) {
            return null;
        }
        if (frame.actionType() == AgentActionRouter.FrameAction.SEARCH
            || frame.actionType() == AgentActionRouter.FrameAction.TRANSACTION
            || frame.actionType() == AgentActionRouter.FrameAction.CHITCHAT) {
            return null;
        }
        List<ProductVO> shown = lastShownProducts(memory);
        if (shown.isEmpty()) {
            return null;
        }
        if (frame.actionType() == AgentActionRouter.FrameAction.INTRO) {
            AgentReply intro = handleProductIntroFromLastShown("\u4ecb\u7ecd\u7b2c\u4e00\u4e2a", memory);
            if (intro != null) {
                return intro;
            }
            return new AgentReply("\u6211\u53ef\u4ee5\u5148\u4ecb\u7ecd\u7b2c\u4e00\u4e2a\uff0c\u4f60\u4e5f\u53ef\u4ee5\u8bf4\uff1a\u4ecb\u7ecd\u7b2c\u4e8c\u4e2a / \u4ecb\u7ecd ID 123\u3002", shown.stream().limit(4).toList());
        }
        if (frame.actionType() == AgentActionRouter.FrameAction.COMPARE) {
            return new AgentReply(
                "\u6211\u7406\u89e3\u4f60\u60f3\u5728\u5f53\u524d\u5019\u9009\u91cc\u505a\u5bf9\u6bd4\u3002\u53ef\u4ee5\u76f4\u63a5\u8bf4\uff1a\u54ea\u4e2a\u66f4\u4fbf\u5b9c / \u54ea\u4e2a\u8bc4\u5206\u6700\u9ad8 / \u54ea\u4e2a\u9500\u91cf\u6700\u9ad8\u3002",
                shown.stream().limit(4).toList()
            );
        }
        if (frame.actionType() == AgentActionRouter.FrameAction.ATTRIBUTE) {
            if (isSlotRefinementFollowUp(prompt, memory) || !isPureAttributeMetricQuestion(prompt)) {
                // Let search pipeline handle preference refinement (size/thickness/set) instead of attribute QA fallback.
                return null;
            }
            return new AgentReply(
                "\u6211\u53ef\u4ee5\u67e5\u8fd9\u6279\u5546\u54c1\u7684\u8bc4\u5206/\u9500\u91cf/\u5e93\u5b58/\u4ef7\u683c\u3002\u4f60\u53ef\u4ee5\u8bf4\uff1a\u7b2c\u4e00\u4e2a\u8bc4\u5206\u600e\u4e48\u6837\uff1f \u6216\u8fd9\u51e0\u4e2a\u91cc\u54ea\u4e2a\u9500\u91cf\u6700\u9ad8\uff1f",
                shown.stream().limit(4).toList()
            );
        }
        return null;
    }

    private boolean isConfirmationLike(String lowered) {
        if (lowered == null || lowered.isBlank()) {
            return false;
        }
        return containsAny(
            lowered,
            "\u662f\u5427", "\u5bf9\u5427", "\u662f\u4e0d\u662f", "\u5bf9\u4e0d\u5bf9", "\u6ca1\u9519\u5427", "\u5e94\u8be5\u662f\u8fd9\u6837\u5427",
            "\u6211\u7406\u89e3\u5bf9\u5417", "\u6211\u7406\u89e3\u7684\u5bf9\u5417", "\u53ef\u4ee5\u8fd9\u4e48\u7406\u89e3\u5417",
            "right", "correct", "am i right", "is it"
        );
    }

    @Override
    public AgentHealth health() {
        long total = requestCount.get();
        long empty = emptyResultCount.get();
        long clarify = clarifyCount.get();
        long handoff = handoffCount.get();
        double emptyRate = total <= 0 ? 0.0 : (empty * 1.0 / total);
        double avgReturned = total <= 0 ? 0.0 : (returnedProductSum.get() * 1.0 / total);
        return new AgentHealth(
            aiEnabled,
            chatModel != null,
            embeddingModel != null,
            configuredChatBaseUrl,
            configuredChatModelName,
            configuredChatApiKeyHint,
            embeddingStore == null ? "none" : embeddingStore.getClass().getName(),
            milvusConfigured,
            productCache.size(),
            lastError,
            total,
            empty,
            emptyRate,
            clarify,
            handoff,
            avgReturned,
            llmChatCallsTotal.get(),
            llmChatCallErrors.get(),
            toCounterMap(llmChatCallsByScene),
            toCounterMap(actionCounters),
            toCounterMap(scopeCounters),
            toCounterMap(intentCounters),
            catalogTopicTokens.size(),
            topCatalogTopics(20)
        );
    }

    @Override
    public LlmPingResult llmPing(String message) {
        long beforeCalls = llmChatCallsTotal.get();
        long beforeErrors = llmChatCallErrors.get();
        String reply = null;
        String err = "";
        boolean ok = false;
        if (chatModel == null || !aiEnabled) {
            err = "chat model is disabled";
        } else {
            String prompt = nvl(message).isBlank() ? "Reply exactly: pong" : message;
            try {
                reply = callChatModel("probe", prompt);
                ok = reply != null && !reply.isBlank();
            } catch (Exception ex) {
                err = nvl(ex.getMessage());
                lastError = "llm ping failed: " + err;
            }
        }
        long afterCalls = llmChatCallsTotal.get();
        long afterErrors = llmChatCallErrors.get();
        return new LlmPingResult(
            ok,
            nvl(reply),
            err,
            beforeCalls,
            afterCalls,
            Math.max(0L, afterCalls - beforeCalls),
            beforeErrors,
            afterErrors,
            Math.max(0L, afterErrors - beforeErrors),
            toCounterMap(llmChatCallsByScene)
        );
    }

    @Override
    public void clearContext(Long userId) {
        long key = userId == null ? 0L : userId;
        ConversationMemory memory = memoryStore.get(key);
        if (memory != null) {
            memory.clearContext();
        }
    }

    private boolean isGreeting(String prompt) {
        if (prompt == null) return true;
        String p = prompt.trim().toLowerCase(Locale.ROOT);
        String normalized = p.replaceAll("[!,.?~\\uFF01\\uFF0C\\u3002\\uFF1F\\u301C]", "");
        return p.isBlank()
            || "hi".equals(p)
            || "hello".equals(p)
            || "hey".equals(p)
            || ZH_HELLO.equals(p)
            || ZH_HI.equals(p)
            || "\u5728\u5417".equals(p)
            || "\u5728\u4e48".equals(p)
            || "\u5728\u4e0d\u5728".equals(p)
            || normalized.equals("\u4f60\u597d")
            || normalized.equals("\u60a8\u597d")
            || normalized.equals("\u4f60\u597d\u554a")
            || normalized.equals("\u55e8")
            || normalized.equals("\u5728\u5417");
    }

    private synchronized void ensureProductCacheLoaded() {
        long now = System.currentTimeMillis();
        if (cacheLoaded && (now - productCacheLoadedAt) < PRODUCT_CACHE_REFRESH_MS) {
            return;
        }

        List<ProductVO> allProducts = shopProductService.listProducts(null, null);
        productCache.clear();
        for (ProductVO p : allProducts) {
            if (p.getId() != null) {
                productCache.put(p.getId(), p);
            }
        }
        rebuildCatalogTopicTokens(allProducts);
        if (!loadAliasExpansionLexiconFromDb()) {
            rebuildAliasExpansionLexicon(allProducts);
        }
        cacheLoaded = true;
        productCacheLoadedAt = now;
    }

    private void startIndexingAsync() {
        if (!aiEnabled || embeddingModel == null || embeddingStore == null || indexed || indexingInProgress) {
            return;
        }
        synchronized (this) {
            if (!aiEnabled || embeddingModel == null || embeddingStore == null || indexed || indexingInProgress) {
                return;
            }
            indexingInProgress = true;
        }
        Thread worker = new Thread(() -> {
            try {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                ensureProductCacheLoaded();
                try {
                    embeddingStore.removeAll();
                } catch (Exception ex) {
                    System.err.println("[AI Agent] removeAll not supported: " + ex.getMessage());
                }
                boolean embeddingAvailable = true;
                for (ProductVO p : productCache.values()) {
                    if (!embeddingAvailable) {
                        break;
                    }
                    try {
                        String text = compactForEmbedding(toDocumentText(p), EMBEDDING_DOC_MAX_CHARS);
                        Embedding embedding = embeddingModel.embed(text).content();
                        embeddingStore.add(embedding, TextSegment.from(text));
                    } catch (Exception ex) {
                        lastError = "embedding fallback: " + ex.getMessage();
                        System.err.println("[AI Agent] " + lastError);
                        embeddingAvailable = false;
                    }
                }
                indexed = embeddingAvailable;
            } finally {
                indexingInProgress = false;
            }
        }, "ai-agent-indexer");
        worker.setDaemon(true);
        worker.start();
    }

    private void syncElasticsearchAsync() {
        if (esSyncInProgress || elasticsearchProductSearchService == null || !elasticsearchProductSearchService.isEnabled()) {
            return;
        }
        synchronized (this) {
            if (esSyncInProgress || elasticsearchProductSearchService == null || !elasticsearchProductSearchService.isEnabled()) {
                return;
            }
            esSyncInProgress = true;
        }
        Thread worker = new Thread(() -> {
            try {
                ensureProductCacheLoaded();
                elasticsearchProductSearchService.reindexAll(new ArrayList<>(productCache.values()));
            } catch (Exception ignored) {
            } finally {
                esSyncInProgress = false;
            }
        }, "ai-agent-es-sync");
        worker.setDaemon(true);
        worker.start();
    }

    private List<ProductVO> esFirstRetrieve(String query) {
        return esFirstRetrieve(query, topK);
    }

    private List<ProductVO> esFirstRetrieve(String query, int limit) {
        int capped = Math.max(1, limit);
        if (query == null || query.isBlank() || elasticsearchProductSearchService == null || !elasticsearchProductSearchService.isEnabled()) {
            return List.of();
        }
        List<Long> ids = elasticsearchProductSearchService.searchProductIds(query, capped * 2);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<ProductVO> out = new ArrayList<>();
        for (Long id : ids) {
            ProductVO p = productCache.get(id);
            if (p != null) {
                out.add(p);
            }
            if (out.size() >= capped) {
                break;
            }
        }
        return applyIntentFilter(query, out);
    }

    private List<ProductVO> ragRetrieve(String query) {
        if (query == null || query.isBlank() || embeddingModel == null || embeddingStore == null || !indexed) {
            return keywordRetrieve(query);
        }

        EmbeddingSearchResult<TextSegment> result;
        try {
            Embedding queryEmbedding = embeddingModel.embed(compactForEmbedding(query, EMBEDDING_QUERY_MAX_CHARS)).content();
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .build();
            result = embeddingStore.search(request);
        } catch (Exception ex) {
            lastError = "rag fallback: " + ex.getMessage();
            System.err.println("[AI Agent] " + lastError);
            return keywordRetrieve(query);
        }

        List<ProductVO> products = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            if (match.score() < ragMinScore) {
                continue;
            }
            Long id = extractProductId(match.embedded().text());
            if (id == null) {
                continue;
            }
            ProductVO p = productCache.get(id);
            if (p != null) {
                products.add(p);
            }
        }
        if (products.isEmpty()) {
            return keywordRetrieve(query);
        }
        return products;
    }

    private List<ProductVO> hybridRetrieve(String query) {
        List<ProductVO> lexical = keywordRetrieve(query);
        List<ProductVO> vector = ragRetrieve(query);
        if (lexical.isEmpty()) {
            return vector;
        }
        if (vector.isEmpty()) {
            return lexical;
        }
        Set<Long> seen = new LinkedHashSet<>();
        List<ProductVO> merged = new ArrayList<>();
        for (ProductVO p : lexical) {
            if (p.getId() != null && seen.add(p.getId())) {
                merged.add(p);
            }
            if (merged.size() >= topK) return merged;
        }
        for (ProductVO p : vector) {
            if (p.getId() != null && seen.add(p.getId())) {
                merged.add(p);
            }
            if (merged.size() >= topK) return merged;
        }
        return merged;
    }

    private List<ProductVO> fastHybridRetrieve(String query) {
        List<ProductVO> lexical = applyIntentFilter(query, keywordRetrieve(query));
        if (lexical.size() >= Math.min(LEXICAL_GOOD_ENOUGH, topK)) {
            return lexical;
        }
        if (indexingInProgress || !indexed || embeddingModel == null || embeddingStore == null) {
            return lexical;
        }

        List<ProductVO> vector = applyIntentFilter(query, ragRetrieveWithTimeout(query));
        if (lexical.isEmpty()) {
            return vector;
        }
        if (vector.isEmpty()) {
            return lexical;
        }

        Set<Long> seen = new LinkedHashSet<>();
        List<ProductVO> merged = new ArrayList<>();
        for (ProductVO p : lexical) {
            if (p.getId() != null && seen.add(p.getId())) {
                merged.add(p);
            }
            if (merged.size() >= topK) return merged;
        }
        for (ProductVO p : vector) {
            if (p.getId() != null && seen.add(p.getId())) {
                merged.add(p);
            }
            if (merged.size() >= topK) return merged;
        }
        return merged;
    }

    private List<ProductVO> intentFirstRetrieve(String query) {
        IntentType intent = detectIntent(query);
        if (intent == IntentType.NONE) {
            return List.of();
        }
        List<ProductVO> strict = strictIntentRetrieve(intent);
        return strict.stream().limit(topK).toList();
    }

    private List<ProductVO> strictIntentRetrieve(IntentType intent) {
        return strictIntentRetrieve(intent, Set.of());
    }

    private List<ProductVO> strictIntentRetrieve(IntentType intent, Set<Long> excludeIds) {
        return strictIntentRetrieve(intent, excludeIds, topK);
    }

    private List<ProductVO> strictIntentRetrieve(IntentType intent, Set<Long> excludeIds, int limit) {
        ensureProductCacheLoaded();
        int effectiveLimit = Math.max(1, limit);
        List<String> terms;
        String preferCategory;
        if (intent == IntentType.MOUSE) {
            terms = MOUSE_TERMS;
            preferCategory = "\u7535\u8111\u5916\u8bbe";
        } else if (intent == IntentType.KEYBOARD) {
            terms = KEYBOARD_TERMS;
            preferCategory = "\u7535\u8111\u5916\u8bbe";
        } else if (intent == IntentType.PET) {
            terms = PET_TERMS;
            preferCategory = "\u5ba0\u7269\u7528\u54c1";
        } else if (intent == IntentType.BABY) {
            terms = BABY_TERMS;
            preferCategory = "\u6bcd\u5a74";
        } else if (intent == IntentType.BOOK) {
            terms = BOOK_STRONG_TERMS;
            preferCategory = "\u56fe\u4e66";
        } else if (intent == IntentType.TOY) {
            terms = TOY_TERMS;
            preferCategory = "\u73a9\u5177";
        } else if (intent == IntentType.MAKEUP) {
            terms = MAKEUP_TERMS;
            preferCategory = "\u7f8e\u5986";
        } else if (intent == IntentType.HEADPHONE) {
            terms = HEADPHONE_TERMS;
            preferCategory = "\u624b\u673a\u6570\u7801";
        } else if (intent == IntentType.SHOE) {
            terms = SHOE_TERMS;
            preferCategory = "\u8fd0\u52a8\u670d\u9970";
        } else if (intent == IntentType.BAG) {
            terms = BAG_STRONG_TERMS;
            preferCategory = "";
        } else if (intent == IntentType.LIGHT) {
            terms = LIGHT_TERMS;
            preferCategory = "";
        } else if (intent == IntentType.BIKE) {
            terms = BIKE_TERMS;
            preferCategory = "";
        } else if (intent == IntentType.OUTDOOR) {
            terms = OUTDOOR_TERMS;
            preferCategory = "\u8fd0\u52a8\u6237\u5916";
        } else if (intent == IntentType.BEDDING) {
            terms = BEDDING_TERMS;
            preferCategory = "";
        } else if (intent == IntentType.DAILY) {
            List<String> dailyTerms = new ArrayList<>(DAILY_STRONG_TERMS);
            dailyTerms.addAll(FOOD_TERMS);
            terms = dailyTerms;
            preferCategory = "";
        } else if (intent == IntentType.COMPUTER) {
            terms = COMPUTER_TERMS;
            preferCategory = "\u624b\u673a\u6570\u7801";
        } else {
            terms = ELECTRONICS_TERMS;
            preferCategory = "\u624b\u673a\u6570\u7801";
        }
        List<ScoredProduct> scored = new ArrayList<>();
        for (ProductVO p : productCache.values()) {
            if (p.getId() != null && excludeIds.contains(p.getId())) {
                continue;
            }
            String name = nvl(p.getName()).toLowerCase(Locale.ROOT);
            String category = nvl(p.getCategory()).toLowerCase(Locale.ROOT);
            String desc = nvl(p.getDescription()).toLowerCase(Locale.ROOT);
            boolean nameDescStrictOnly = intent == IntentType.MOUSE || intent == IntentType.KEYBOARD || intent == IntentType.HEADPHONE;

            int score = 0;
            boolean matched = false;
            for (String term : terms) {
                String t = term.toLowerCase(Locale.ROOT);
                if (name.contains(t)) {
                    score += 6;
                    matched = true;
                } else if (!nameDescStrictOnly && category.contains(t)) {
                    score += 4;
                    matched = true;
                } else if (desc.contains(t)) {
                    score += 2;
                    matched = true;
                }
            }
            if (!matched) {
                continue;
            }
            if (intent == IntentType.MOUSE && !isStrictMouseProduct(p)) {
                continue;
            }
            if (intent == IntentType.KEYBOARD && !isStrictKeyboardProduct(p)) {
                continue;
            }
            if (intent == IntentType.PET && !isPetLikeProduct(p)) {
                continue;
            }
            if (intent == IntentType.BABY && !isBabyLikeProduct(p)) {
                continue;
            }
            if (intent == IntentType.BOOK && !isBookLikeProduct(p)) {
                continue;
            }
            if (intent == IntentType.TOY && !isToyLikeProduct(p)) {
                continue;
            }
            if (intent == IntentType.MAKEUP && !isMakeupLikeProduct(p)) {
                continue;
            }
            if (intent == IntentType.HEADPHONE && !isStrictHeadphoneProduct(p)) {
                continue;
            }
            if (intent == IntentType.BAG && !likelyBagProduct(p)) {
                continue;
            }
            if (intent == IntentType.LIGHT && !isLightLikeProduct(p)) {
                continue;
            }
            if (intent == IntentType.BIKE && !isBikeLikeProduct(p)) {
                continue;
            }
            if (intent == IntentType.OUTDOOR && !isOutdoorLikeProduct(p)) {
                continue;
            }
            if (intent == IntentType.BEDDING && !isBeddingLikeProduct(p)) {
                continue;
            }
            if (intent == IntentType.DAILY && !isDailyLikeProduct(p)) {
                continue;
            }
            if (intent == IntentType.COMPUTER && isHeadphoneLikeProduct(p)) {
                continue;
            }
            if (!preferCategory.isBlank() && category.contains(preferCategory.toLowerCase(Locale.ROOT))) {
                score += 3;
            }
            if (p.getSales() != null) {
                score += Math.min(3, p.getSales() / 5000);
            }
            if (p.getRating() != null && p.getRating().compareTo(java.math.BigDecimal.valueOf(4.3)) >= 0) {
                score += 2;
            }
            scored.add(new ScoredProduct(p, score));
        }
        scored.sort((a, b) -> Integer.compare(b.score(), a.score()));
        List<ProductVO> result = new ArrayList<>();
        for (ScoredProduct sp : scored) {
            result.add(sp.product());
            if (result.size() >= effectiveLimit) {
                break;
            }
        }
        return result;
    }

    private List<ProductVO> ragRetrieveWithTimeout(String query) {
        try {
            return CompletableFuture
                .supplyAsync(() -> ragRetrieve(query))
                .orTimeout(RAG_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> List.of())
                .join();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<ProductVO> keywordRetrieve(String query) {
        List<ProductVO> fuzzy = fuzzyLocalRetrieve(query);
        if (!fuzzy.isEmpty()) {
            return fuzzy;
        }

        String keyword = query == null ? "" : query.trim();
        Set<Long> seen = new LinkedHashSet<>();
        List<ProductVO> merged = new ArrayList<>();

        for (String term : expandSearchTerms(keyword)) {
            List<ProductVO> list = shopProductService.listProducts(null, term);
            for (ProductVO p : list) {
                if (p.getId() == null || !seen.add(p.getId())) {
                    continue;
                }
                merged.add(p);
                if (merged.size() >= topK) {
                    return merged;
                }
            }
        }

        if (merged.isEmpty() && keyword.isBlank()) {
            return shopProductService.listProducts(null, null).stream().limit(topK).toList();
        }
        return merged;
    }

    private List<ProductVO> fuzzyLocalRetrieve(String query) {
        ensureProductCacheLoaded();
        String normalized = normalizeQuery(query);
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> tokens = splitQueryTokens(normalized);
        if (tokens.isEmpty()) {
            return List.of();
        }

        List<ScoredProduct> scored = new ArrayList<>();
        for (ProductVO p : productCache.values()) {
            String name = nvl(p.getName()).toLowerCase(Locale.ROOT);
            String category = nvl(p.getCategory()).toLowerCase(Locale.ROOT);
            String brand = nvl(p.getBrand()).toLowerCase(Locale.ROOT);
            String desc = nvl(p.getDescription()).toLowerCase(Locale.ROOT);
            String all = name + " " + category + " " + brand + " " + desc;

            int score = 0;
            for (String t : tokens) {
                if (t.length() < 2 && !containsCjk(t)) continue;
                if (name.contains(t)) score += 8;
                else if (category.contains(t)) score += 6;
                else if (brand.contains(t)) score += 4;
                else if (desc.contains(t)) score += 2;
            }
            if (name.contains(normalized)) score += 10;
            else if (all.contains(normalized)) score += 5;

            if (score <= 0) continue;
            if (p.getRating() != null && p.getRating().compareTo(java.math.BigDecimal.valueOf(4.3)) >= 0) {
                score += 1;
            }
            scored.add(new ScoredProduct(p, score));
        }

        scored.sort((a, b) -> Integer.compare(b.score(), a.score()));
        List<ProductVO> out = new ArrayList<>();
        for (ScoredProduct s : scored) {
            out.add(s.product());
            if (out.size() >= topK) break;
        }
        return out;
    }

    private String normalizeQuery(String query) {
        if (query == null) return "";
        return query.toLowerCase(Locale.ROOT)
            .replace("recommend", " ")
            .replace("show me", " ")
            .replace("find", " ")
            .replace("search", " ")
            .replace("please", " ")
            .replace("\u63a8\u8350", " ")
            .replace("\u6c42\u63a8\u8350", " ")
            .replace("\u9ebb\u70e6\u63a8\u8350", " ")
            .replace("\u6709\u6ca1\u6709\u63a8\u8350", " ")
            .replace("\u5e2e\u6211\u627e", " ")
            .replace("\u6211\u60f3\u4e70", " ")
            .replace("\u6211\u8981\u4e70", " ")
            .replace("\u6211\u60f3\u8981", " ")
            .replace("\u6211\u8981", " ")
            .replace("\u60f3\u8981", " ")
            .replace("\u9700\u8981", " ")
            .replace("\u8981\u4e70", " ")
            .replace("\u6709\u6ca1\u6709", " ")
            .replace("\u6709\u6ca1", " ")
            .replace("\u6709\u6728\u6709", " ")
            .replace("\u6765\u4e2a", " ")
            .replace("\u6765\u70b9", " ")
            .replace("\u6574\u70b9", " ")
            .replace("\u641e\u70b9", " ")
            .replace("\u6765\u4e9b", " ")
            .replace("\u7ed9\u6211", " ")
            .replace("\u6709\u4ec0\u4e48", " ")
            .replace("\u6709\u5565", " ")
            .replace("\u4ec0\u4e48\u53ef\u4ee5\u4e70", " ")
            .replace("\u770b\u770b", " ")
            .replace("\u597d\u7528", " ")
            .replace("\u80fd\u4e0d\u80fd", " ")
            .replace("\u53ef\u4e0d\u53ef\u4ee5", " ")
            .replace("\u96be\u9053", " ")
            .replace("\u67e5\u627e", " ")
            .replace("\u641c\u7d22", " ")
            .replace("\u4e00\u4e0b", " ")
            .replace("\u5417", " ")
            .replace("\u5462", " ")
            .replace("\u5440", " ")
            .replace("\u5427", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private List<String> splitQueryTokens(String normalized) {
        List<String> tokens = new ArrayList<>();
        for (String t : normalized.split("[^\\p{L}\\p{N}]+")) {
            String token = t.trim();
            if (token.length() < 2 && !containsCjk(token)) continue;
            if (!tokens.contains(token)) tokens.add(token);
        }
        if (containsCjk(normalized) && !tokens.contains(normalized) && normalized.length() >= 2) {
            tokens.add(normalized);
        }
        return tokens;
    }

    private void rebuildCatalogTopicTokens(List<ProductVO> allProducts) {
        Map<String, Integer> freq = new LinkedHashMap<>();
        for (ProductVO p : allProducts) {
            if (p == null) {
                continue;
            }
            String corpus = String.join(" ",
                nvl(p.getCategory()),
                nvl(p.getCategoryLevel1()),
                nvl(p.getCategoryLevel2()),
                nvl(p.getCategoryLevel3()),
                nvl(p.getCategoryPath()),
                nvl(p.getName())
            ).toLowerCase(Locale.ROOT);
            for (String token : splitQueryTokens(normalizeQuery(corpus))) {
                if (!isCatalogTopicToken(token)) {
                    continue;
                }
                freq.merge(token, 1, Integer::sum);
            }
        }
        catalogTopicTokens.clear();
        catalogTopicFreq.clear();
        for (Map.Entry<String, Integer> e : freq.entrySet()) {
            if (e.getValue() >= 2) {
                catalogTopicTokens.add(e.getKey());
                catalogTopicFreq.put(e.getKey(), e.getValue());
            }
        }
        catalogTopicTokens.addAll(List.of(
            "\u706f", "\u706f\u5177", "\u7167\u660e", "lamp", "light", "lighting",
            "\u978b", "\u978b\u5b50", "shoe", "sneaker",
            "\u5305", "bag", "backpack",
            "\u8033\u673a", "headphone", "earbud",
            "\u624b\u673a", "phone",
            "\u7535\u8111", "laptop", "computer",
            "\u81ea\u884c\u8f66", "\u9a91\u884c", "bicycle", "bike", "cycling"
        ));
        for (String bootstrap : catalogTopicTokens) {
            catalogTopicFreq.putIfAbsent(bootstrap, 1);
        }
    }

    private void rebuildAliasExpansionLexicon(List<ProductVO> allProducts) {
        aliasExpansionMap.clear();
        if (allProducts == null || allProducts.isEmpty()) {
            return;
        }

        Set<String> generic = Set.of(
            "\u901a\u7528\u5546\u54c1", "\u5546\u54c1", "\u5957\u88c5", "\u4ef6\u5957", "\u914d\u4ef6", "\u6b3e", "\u578b\u53f7", "\u7cfb\u5217", "\u7ecf\u5178", "\u5347\u7ea7",
            "set", "piece", "pieces", "pack", "kit", "model", "classic", "upgrade", "new"
        );
        Map<String, Map<String, Integer>> clusterTokenFreq = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> clusterSeeds = new LinkedHashMap<>();

        for (ProductVO p : allProducts) {
            if (p == null) {
                continue;
            }
            String cluster = nvl(p.getCategoryLevel3());
            if (cluster.isBlank()) {
                cluster = nvl(p.getCategoryPath());
            }
            if (cluster.isBlank()) {
                cluster = nvl(p.getCategory());
            }
            cluster = cluster.toLowerCase(Locale.ROOT).trim();
            if (cluster.isBlank()) {
                continue;
            }

            Map<String, Integer> freq = clusterTokenFreq.computeIfAbsent(cluster, k -> new LinkedHashMap<>());
            LinkedHashSet<String> seeds = clusterSeeds.computeIfAbsent(cluster, k -> new LinkedHashSet<>());

            for (String t : splitQueryTokens(normalizeQuery(cluster))) {
                if (isAliasTokenEffective(t, generic)) {
                    seeds.add(t.toLowerCase(Locale.ROOT));
                    freq.merge(t.toLowerCase(Locale.ROOT), 3, Integer::sum);
                }
            }

            String corpus = nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getCategoryPath()) + " " + nvl(p.getDescription());
            for (String t : splitQueryTokens(normalizeQuery(corpus))) {
                String token = t == null ? "" : t.toLowerCase(Locale.ROOT).trim();
                if (!isAliasTokenEffective(token, generic)) {
                    continue;
                }
                freq.merge(token, 1, Integer::sum);
            }
        }

        Map<String, List<String>> clusterAliasesSnapshot = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Integer>> e : clusterTokenFreq.entrySet()) {
            String cluster = e.getKey();
            Map<String, Integer> freq = e.getValue();
            LinkedHashSet<String> aliases = new LinkedHashSet<>(clusterSeeds.getOrDefault(cluster, new LinkedHashSet<>()));

            List<Map.Entry<String, Integer>> ranked = new ArrayList<>(freq.entrySet());
            ranked.sort((a, b) -> {
                int byCnt = Integer.compare(b.getValue(), a.getValue());
                if (byCnt != 0) {
                    return byCnt;
                }
                return Integer.compare(a.getKey().length(), b.getKey().length());
            });
            for (Map.Entry<String, Integer> token : ranked) {
                if (token.getValue() < 2) {
                    continue;
                }
                aliases.add(token.getKey());
                if (aliases.size() >= 24) {
                    break;
                }
            }
            if (aliases.size() < 2) {
                continue;
            }
            List<String> shared = new ArrayList<>(aliases);
            clusterAliasesSnapshot.put(cluster, shared);
            for (String alias : aliases) {
                aliasExpansionMap.put(alias, shared);
            }
        }
        persistAliasExpansionLexiconToDb(clusterAliasesSnapshot);
    }

    private boolean isAliasTokenEffective(String token, Set<String> generic) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String t = token.toLowerCase(Locale.ROOT).trim();
        if (QUERY_STOP_WORDS.contains(t) || ALIAS_NOISE_TERMS.contains(t) || generic.contains(t)) {
            return false;
        }
        if (t.matches("\\d+")) {
            return false;
        }
        if (containsCjk(t) && t.length() < 2) {
            return false;
        }
        if (!containsCjk(t) && t.length() < 3) {
            return false;
        }
        if (containsCjk(t) && t.length() > 8) {
            return false;
        }
        if (!containsCjk(t) && t.length() > 20) {
            return false;
        }
        return true;
    }

    private boolean loadAliasExpansionLexiconFromDb() {
        if (searchAliasLexiconMapper == null) {
            return false;
        }
        try {
            List<SearchAliasLexicon> rows = searchAliasLexiconMapper.listEnabled();
            if (rows == null || rows.isEmpty()) {
                return false;
            }
            aliasExpansionMap.clear();
            for (SearchAliasLexicon row : rows) {
                if (row == null || row.getAlias() == null || row.getAlias().isBlank()) {
                    continue;
                }
                List<String> shared = parseAliases(row.getAliases());
                if (shared.isEmpty()) {
                    continue;
                }
                aliasExpansionMap.put(row.getAlias().toLowerCase(Locale.ROOT).trim(), shared);
                for (String alias : shared) {
                    if (alias != null && !alias.isBlank()) {
                        aliasExpansionMap.putIfAbsent(alias.toLowerCase(Locale.ROOT).trim(), shared);
                    }
                }
            }
            return !aliasExpansionMap.isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void persistAliasExpansionLexiconToDb(Map<String, List<String>> clusterAliases) {
        if (searchAliasLexiconMapper == null || clusterAliases == null || clusterAliases.isEmpty()) {
            return;
        }
        try {
            searchAliasLexiconMapper.deleteBySource("AUTO");
            for (Map.Entry<String, List<String>> e : clusterAliases.entrySet()) {
                String cluster = e.getKey();
                List<String> aliases = e.getValue();
                if (cluster == null || cluster.isBlank() || aliases == null || aliases.isEmpty()) {
                    continue;
                }
                String packed = packAliases(aliases);
                for (String alias : aliases) {
                    if (alias == null || alias.isBlank()) {
                        continue;
                    }
                    searchAliasLexiconMapper.upsert(
                        alias.toLowerCase(Locale.ROOT).trim(),
                        cluster,
                        packed,
                        "AUTO"
                    );
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String packAliases(List<String> aliases) {
        LinkedHashSet<String> dedup = new LinkedHashSet<>();
        for (String alias : aliases) {
            String t = alias == null ? "" : alias.toLowerCase(Locale.ROOT).trim();
            if (!t.isBlank()) {
                dedup.add(t);
            }
        }
        return String.join("|", dedup);
    }

    private List<String> parseAliases(String packed) {
        if (packed == null || packed.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String part : packed.split("\\|")) {
            String t = part == null ? "" : part.toLowerCase(Locale.ROOT).trim();
            if (!t.isBlank()) {
                out.add(t);
            }
        }
        return new ArrayList<>(out);
    }

    private List<String> extractPromptTopicTokens(String prompt) {
        if (prompt == null || prompt.isBlank() || catalogTopicTokens.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String normalized = normalizeQuery(prompt);
        for (String token : splitQueryTokens(normalized)) {
            String t = token.toLowerCase(Locale.ROOT);
            if (catalogTopicTokens.contains(t)) {
                out.add(t);
            }
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        for (String t : catalogTopicTokens) {
            if (t.length() >= 2 && lowered.contains(t)) {
                out.add(t);
            }
            if (out.size() >= 6) {
                break;
            }
        }
        return new ArrayList<>(out);
    }

    private boolean shouldForceNewSearchScope(String prompt, List<String> promptTopicTokens) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        if (isExplicitNewNeedPrompt(prompt)) {
            return true;
        }
        if (promptTopicTokens != null && !promptTopicTokens.isEmpty()) {
            return true;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        boolean explicitNewTopicCue = containsAny(
            lowered,
            "\u6211\u60f3\u4e70", "\u6211\u60f3\u8981", "\u6211\u8981\u4e70", "\u60f3\u8981", "\u627e\u4e2a",
            "\u63a8\u8350", "\u7ed9\u6211\u627e", "\u5e2e\u6211\u627e", "\u60f3\u8981\u4e00\u4e2a",
            "i want", "i need", "looking for", "find me"
        );
        if (!explicitNewTopicCue) {
            return false;
        }
        boolean followUpCue = isFollowUpQuery(prompt)
            || isComparativeFollowUp(prompt)
            || containsAny(
            lowered,
            "\u8fd8\u6709", "\u66f4", "\u518d\u6765", "\u90a3\u4f60", "\u90a3\u5c31", "\u8fd9\u4e2a", "\u90a3\u4e2a", "\u7b2c\u4e00\u4e2a", "\u6765\u4e00\u4e2a",
            "more", "another", "this one", "that one", "the first", "one more"
        );
        return !followUpCue;
    }

    private boolean isCatalogTopicToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String t = token.toLowerCase(Locale.ROOT).trim();
        if (QUERY_STOP_WORDS.contains(t)) {
            return false;
        }
        if (t.matches("\\d+")) {
            return false;
        }
        if (containsCjk(t)) {
            return t.length() >= 2 && t.length() <= 8;
        }
        return t.length() >= 3 && t.length() <= 20;
    }

    private List<ProductVO> directRetrieveByPrompt(String prompt, int limit) {
        if (prompt == null || prompt.isBlank()) {
            return List.of();
        }
        List<String> tokens = splitQueryTokens(normalizeQuery(prompt));
        List<String> effective = new ArrayList<>();
        for (String t : tokens) {
            if (t == null || t.isBlank() || QUERY_STOP_WORDS.contains(t)) {
                continue;
            }
            if (!containsCjk(t) && t.length() < 3) {
                continue;
            }
            effective.add(t);
        }
        List<String> queries = new ArrayList<>();
        if (!effective.isEmpty()) {
            String best = effective.get(0);
            for (String t : effective) {
                if (t.length() > best.length()) {
                    best = t;
                }
            }
            queries.add(best);
            for (String t : effective) {
                if (!queries.contains(t)) {
                    queries.add(t);
                }
                if (queries.size() >= 3) {
                    break;
                }
            }
        } else {
            queries.add(prompt.trim());
        }
        List<String> lexicalKeys = new ArrayList<>(effective);
        if (lexicalKeys.isEmpty()) {
            for (String q : queries) {
                if (q != null && !q.isBlank()) {
                    lexicalKeys.add(q.toLowerCase(Locale.ROOT).trim());
                }
            }
        }
        LinkedHashMap<Long, ProductVO> merged = new LinkedHashMap<>();
        for (String q : queries) {
            try {
                List<ProductVO> batch = shopProductService.listProducts(null, q);
                for (ProductVO p : batch) {
                    if (p == null || p.getId() == null) {
                        continue;
                    }
                    String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getBrand()) + " " + nvl(p.getDescription()))
                        .toLowerCase(Locale.ROOT);
                    boolean matched = lexicalKeys.isEmpty();
                    for (String key : lexicalKeys) {
                        if (key != null && !key.isBlank() && text.contains(key)) {
                            matched = true;
                            break;
                        }
                    }
                    if (!matched) {
                        continue;
                    }
                    if (p != null && p.getId() != null) {
                        merged.putIfAbsent(p.getId(), p);
                    }
                    if (merged.size() >= limit) {
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
            if (merged.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(merged.values()).stream().limit(Math.max(1, limit)).toList();
    }

    private List<String> topCatalogTopics(int limit) {
        if (catalogTopicFreq.isEmpty()) {
            return List.of();
        }
        return catalogTopicFreq.entrySet().stream()
            .sorted((a, b) -> {
                int byCount = Integer.compare(b.getValue(), a.getValue());
                if (byCount != 0) {
                    return byCount;
                }
                return a.getKey().compareTo(b.getKey());
            })
            .limit(Math.max(1, limit))
            .map(e -> e.getKey() + ":" + e.getValue())
            .toList();
    }

    private List<String> expandSearchTerms(String keyword) {
        List<String> terms = new ArrayList<>();
        if (keyword == null || keyword.isBlank()) {
            terms.add("");
            return terms;
        }

        String lowered = keyword.toLowerCase(Locale.ROOT).trim();
        terms.add(lowered);

        String normalized = lowered
            .replace("recommend", " ")
            .replace("show me", " ")
            .replace("find", " ")
            .replace("search", " ")
            .replace("please", " ")
            .replace("\u63a8\u8350", " ")
            .replace("\u5e2e\u6211\u627e", " ")
            .replace("\u6211\u60f3\u4e70", " ")
            .replace("\u67e5\u627e", " ")
            .replace("\u641c\u7d22", " ")
            .replace("\u4e00\u4e0b", " ")
            .trim();

        if (!normalized.isBlank() && !terms.contains(normalized)) {
            terms.add(normalized);
        }

        for (String token : normalized.split("[^\\p{L}\\p{N}]+")) {
            if ((token.length() >= 2 || containsCjk(token)) && !terms.contains(token)) {
                terms.add(token);
            }
        }

        if (containsCjk(lowered) && !indexingInProgress) {
            addTerms(terms, rewriteToEnglishTerms(lowered));
        }

        boolean phoneIntent = lowered.contains(ZH_PHONE)
            || lowered.contains("phone")
            || lowered.contains("iphone")
            || lowered.contains("smartphone")
            || lowered.contains("mobile");
        if (phoneIntent) {
            addTerms(terms, PHONE_TERMS);
        }

        boolean headphoneIntent = lowered.contains(ZH_HEADPHONE)
            || lowered.contains("earphone")
            || lowered.contains("earbud")
            || lowered.contains("headphone")
            || lowered.contains("headset");
        if (headphoneIntent) {
            addTerms(terms, HEADPHONE_TERMS);
        }
        boolean mouseIntent = lowered.contains("\u9f20\u6807")
            || lowered.contains("mouse")
            || lowered.contains("wireless mouse")
            || lowered.contains("gaming mouse");
        if (mouseIntent) {
            addTerms(terms, MOUSE_TERMS);
        }
        boolean keyboardIntent = lowered.contains("\u952e\u76d8")
            || lowered.contains("\u673a\u68b0\u952e\u76d8")
            || lowered.contains("keyboard")
            || lowered.contains("mechanical keyboard")
            || lowered.contains("gaming keyboard");
        if (keyboardIntent) {
            addTerms(terms, KEYBOARD_TERMS);
        }

        boolean computerIntent = lowered.contains("\u7535\u8111")
            || lowered.contains("\u7b14\u8bb0\u672c")
            || lowered.contains("computer")
            || lowered.contains("pc")
            || lowered.contains("laptop")
            || lowered.contains("notebook")
            || lowered.contains("desktop")
            || lowered.contains("macbook");
        if (computerIntent) {
            addTerms(terms, COMPUTER_TERMS);
            addTerms(terms, ELECTRONICS_TERMS);
        }

        boolean bagIntent = lowered.contains("\u5305")
            || lowered.contains("bag")
            || lowered.contains("backpack")
            || lowered.contains("handbag")
            || lowered.contains("tote")
            || lowered.contains("luggage");
        if (bagIntent) {
            addTerms(terms, BAG_TERMS);
        }
        boolean lightIntent = lowered.contains("\u706f")
            || lowered.contains("\u7167\u660e")
            || lowered.contains("lamp")
            || lowered.contains("light")
            || lowered.contains("lighting")
            || lowered.contains("bulb")
            || lowered.contains("led");
        if (lightIntent) {
            addTerms(terms, LIGHT_TERMS);
        }
        boolean bikeIntent = lowered.contains("\u81ea\u884c\u8f66")
            || lowered.contains("\u5355\u8f66")
            || lowered.contains("\u9a91\u884c")
            || lowered.contains("\u5c71\u5730\u8f66")
            || lowered.contains("\u516c\u8def\u8f66")
            || lowered.contains("bicycle")
            || lowered.contains(" bike")
            || lowered.contains("bike ")
            || lowered.contains("cycling")
            || lowered.contains("mtb")
            || lowered.contains("bmx")
            || lowered.contains("tricycle")
            || lowered.contains("ebike")
            || lowered.contains("e-bike");
        if (bikeIntent) {
            addTerms(terms, BIKE_TERMS);
        }
        if (isPetBowlQuery(lowered)) {
            addTerms(terms, PET_BOWL_TERMS);
        }

        return terms;
    }

    private void addTerms(List<String> terms, List<String> extra) {
        for (String t : extra) {
            if (!terms.contains(t)) {
                terms.add(t);
            }
        }
    }

    private List<ProductVO> applyIntentFilter(String prompt, List<ProductVO> candidates) {
        if (prompt == null || prompt.isBlank() || candidates.isEmpty()) {
            return candidates;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        boolean headphoneIntent = lowered.contains(ZH_HEADPHONE)
            || lowered.contains("headphone")
            || lowered.contains("earphone")
            || lowered.contains("earbud")
            || lowered.contains("headset");
        boolean mouseIntent = lowered.contains("\u9f20\u6807")
            || lowered.contains("mouse")
            || lowered.contains("wireless mouse")
            || lowered.contains("gaming mouse");
        boolean keyboardIntent = lowered.contains("\u952e\u76d8")
            || lowered.contains("\u673a\u68b0\u952e\u76d8")
            || lowered.contains("keyboard")
            || lowered.contains("mechanical keyboard")
            || lowered.contains("gaming keyboard");
        boolean petIntent = containsAnyTerm(lowered, PET_TERMS);
        boolean babyIntent = containsAnyTerm(lowered, BABY_TERMS);
        boolean bookIntent = containsAnyTerm(lowered, BOOK_TERMS) || containsAnyTerm(lowered, BOOK_STRONG_TERMS);
        boolean toyIntent = containsAnyTerm(lowered, TOY_TERMS);
        boolean makeupIntent = containsAnyTerm(lowered, MAKEUP_TERMS);
        boolean phoneIntent = lowered.contains(ZH_PHONE)
            || lowered.contains("smartphone")
            || lowered.contains("iphone")
            || lowered.contains("android")
            || lowered.contains("mobile phone")
            || lowered.contains("cell phone")
            || lowered.contains(" phone");
        boolean shoeIntent = lowered.contains("\u978b")
            || lowered.contains("shoe")
            || lowered.contains("sneaker")
            || lowered.contains("boots")
            || lowered.contains("sandals");
        boolean bagIntent = containsAnyTerm(lowered, BAG_TERMS) || containsAnyTerm(lowered, BAG_STRONG_TERMS);
        boolean computerIntent = lowered.contains("\u7535\u8111")
            || lowered.contains("\u7b14\u8bb0\u672c")
            || lowered.contains("computer")
            || lowered.contains("pc")
            || lowered.contains("laptop")
            || lowered.contains("notebook")
            || lowered.contains("desktop")
            || lowered.contains("macbook");
        boolean electronicsIntent = lowered.contains("\u7535\u5b50")
            || lowered.contains("\u6570\u7801")
            || computerIntent
            || lowered.contains("electronics")
            || lowered.contains("digital")
            || lowered.contains("gadget")
            || lowered.contains("tech");
        boolean lightIntent = lowered.contains("\u706f")
            || lowered.contains("\u7167\u660e")
            || lowered.contains("lamp")
            || lowered.contains("light")
            || lowered.contains("lighting")
            || lowered.contains("bulb")
            || lowered.contains("led");
        boolean bikeIntent = lowered.contains("\u81ea\u884c\u8f66")
            || lowered.contains("\u5355\u8f66")
            || lowered.contains("\u9a91\u884c")
            || lowered.contains("\u5c71\u5730\u8f66")
            || lowered.contains("\u516c\u8def\u8f66")
            || lowered.contains("bicycle")
            || lowered.contains(" bike")
            || lowered.contains("bike ")
            || lowered.contains("cycling")
            || lowered.contains("mtb")
            || lowered.contains("bmx")
            || lowered.contains("tricycle")
            || lowered.contains("ebike")
            || lowered.contains("e-bike");
        boolean outdoorIntent = containsAnyTerm(lowered, OUTDOOR_TERMS);
        boolean dailyIntent = containsAnyTerm(lowered, DAILY_TERMS) || containsAnyTerm(lowered, DAILY_STRONG_TERMS);
        boolean beddingIntent = containsAnyTerm(lowered, BEDDING_TERMS);
        boolean foodIntent = containsAnyTerm(lowered, FOOD_TERMS);
        boolean childToyQuery = toyIntent && containsAnyTerm(lowered, TOY_CHILD_TERMS);
        boolean petBowlIntent = isPetBowlQuery(lowered);
        if (!mouseIntent && !keyboardIntent && !headphoneIntent && !phoneIntent && !shoeIntent && !bagIntent && !electronicsIntent && !lightIntent && !bikeIntent && !outdoorIntent && !dailyIntent && !beddingIntent && !foodIntent && !petBowlIntent && !petIntent && !babyIntent && !bookIntent && !toyIntent && !makeupIntent) {
            return candidates;
        }

        List<String> terms;
        if (petBowlIntent) {
            terms = PET_BOWL_TERMS;
        } else if (beddingIntent) {
            terms = BEDDING_TERMS;
        } else if (foodIntent) {
            terms = FOOD_TERMS;
        } else if (mouseIntent) {
            terms = MOUSE_TERMS;
        } else if (keyboardIntent) {
            terms = KEYBOARD_TERMS;
        } else if (phoneIntent) {
            terms = PHONE_TERMS;
        } else if (headphoneIntent) {
            terms = HEADPHONE_TERMS;
        } else if (shoeIntent) {
            terms = SHOE_TERMS;
        } else if (bagIntent) {
            terms = BAG_STRONG_TERMS;
        } else if (petIntent) {
            terms = PET_TERMS;
        } else if (babyIntent) {
            terms = BABY_TERMS;
        } else if (bookIntent) {
            terms = BOOK_STRONG_TERMS;
        } else if (toyIntent) {
            terms = TOY_TERMS;
        } else if (makeupIntent) {
            terms = MAKEUP_TERMS;
        } else if (computerIntent) {
            terms = COMPUTER_TERMS;
        } else if (electronicsIntent) {
            terms = ELECTRONICS_TERMS;
        } else if (lightIntent) {
            terms = LIGHT_TERMS;
        } else if (bikeIntent) {
            terms = BIKE_TERMS;
        } else if (outdoorIntent) {
            terms = OUTDOOR_TERMS;
        } else {
            terms = DAILY_STRONG_TERMS;
        }
        List<ProductVO> filtered = new ArrayList<>();
        for (ProductVO p : candidates) {
            boolean strictPeripheralIntent = mouseIntent || keyboardIntent || headphoneIntent;
            String text = strictPeripheralIntent
                ? (nvl(p.getName()) + " " + nvl(p.getDescription()))
                : (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getDescription()));
            text = text
                .toLowerCase(Locale.ROOT);
            if (mouseIntent && !isStrictMouseProduct(p)) {
                continue;
            }
            if (keyboardIntent && !isStrictKeyboardProduct(p)) {
                continue;
            }
            if (computerIntent && isHeadphoneLikeProduct(p)) {
                continue;
            }
            if (phoneIntent && !isPhoneLikeProduct(p)) {
                continue;
            }
            if (headphoneIntent && !isStrictHeadphoneProduct(p)) {
                continue;
            }
            if (bagIntent && !likelyBagProduct(p)) {
                continue;
            }
            if (lightIntent && !isLightLikeProduct(p)) {
                continue;
            }
            if (bikeIntent && !isBikeLikeProduct(p)) {
                continue;
            }
            if (outdoorIntent && !isOutdoorLikeProduct(p)) {
                continue;
            }
            if (petBowlIntent && !likelyPetBowlProduct(p)) {
                continue;
            }
            if (petIntent && !isPetLikeProduct(p)) {
                continue;
            }
            if (babyIntent && !isBabyLikeProduct(p)) {
                continue;
            }
            if (bookIntent && !isBookLikeProduct(p)) {
                continue;
            }
            if (toyIntent && !isToyLikeProduct(p)) {
                continue;
            }
            if (childToyQuery && !isChildToyLikeProduct(p)) {
                continue;
            }
            if (makeupIntent && !isMakeupLikeProduct(p)) {
                continue;
            }
            if (dailyIntent && !isDailyLikeProduct(p)) {
                continue;
            }
            if (beddingIntent && !isBeddingLikeProduct(p)) {
                continue;
            }
            if (foodIntent && !isFoodLikeProduct(p)) {
                continue;
            }
            boolean matched = false;
            for (String t : terms) {
                if (text.contains(t.toLowerCase(Locale.ROOT))) {
                    matched = true;
                    break;
                }
            }
            if (matched) {
                filtered.add(p);
            }
        }
        return filtered;
    }

    private List<ProductVO> validateCandidates(String prompt, List<ProductVO> candidates) {
        if (prompt == null || prompt.isBlank() || candidates.isEmpty()) {
            return candidates;
        }
        String normalized = normalizeQuery(prompt);
        List<String> tokens = splitQueryTokens(normalized);
        if (tokens.isEmpty()) {
            return candidates;
        }

        Set<String> aliases = new LinkedHashSet<>();
        for (String t : tokens) {
            if (QUERY_STOP_WORDS.contains(t)) {
                continue;
            }
            aliases.add(t);
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        boolean dailyIntent = lowered.contains("\u751f\u6d3b\u7528\u54c1")
            || lowered.contains("\u65e5\u7528\u54c1")
            || lowered.contains("\u5bb6\u5c45")
            || lowered.contains("household")
            || lowered.contains("home")
            || lowered.contains("daily");
        boolean beddingIntent = containsAnyTerm(lowered, BEDDING_TERMS);
        boolean foodIntent = containsAnyTerm(lowered, FOOD_TERMS);
        if (lowered.contains("\u7535\u5b50") || lowered.contains("\u6570\u7801") || lowered.contains("electronics") || lowered.contains("digital")) {
            aliases.add("\u624b\u673a\u6570\u7801");
            aliases.add("electronics");
            aliases.add("digital");
            aliases.add("phone");
            aliases.add("headphone");
            aliases.add("bluetooth");
        }
        if (dailyIntent) {
            aliases.addAll(DAILY_TERMS);
        }
        if (beddingIntent) {
            aliases.addAll(BEDDING_TERMS);
        }
        if (foodIntent) {
            aliases.addAll(FOOD_TERMS);
        }
        if (lowered.contains("\u8033\u673a")) {
            aliases.addAll(HEADPHONE_TERMS);
        }
        if (lowered.contains("\u978b")) {
            aliases.addAll(SHOE_TERMS);
        }
        if (lowered.contains("\u5305") || lowered.contains("bag") || lowered.contains("backpack") || lowered.contains("handbag")) {
            aliases.addAll(BAG_TERMS);
        }
        if (lowered.contains("\u81ea\u884c\u8f66")
            || lowered.contains("\u5355\u8f66")
            || lowered.contains("\u9a91\u884c")
            || lowered.contains("bicycle")
            || lowered.contains("bike")
            || lowered.contains("cycling")) {
            aliases.addAll(BIKE_TERMS);
        }
        if (containsAnyTerm(lowered, OUTDOOR_TERMS)) {
            aliases.addAll(OUTDOOR_TERMS);
        }
        if (isPetBowlQuery(lowered)) {
            aliases.addAll(PET_BOWL_TERMS);
        }
        if (aliases.isEmpty()) {
            return candidates;
        }

        List<ProductVO> filtered = new ArrayList<>();
        for (ProductVO p : candidates) {
            String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getBrand()) + " " + nvl(p.getDescription()))
                .toLowerCase(Locale.ROOT);
            boolean ok = false;
            for (String a : aliases) {
                String key = a.toLowerCase(Locale.ROOT).trim();
                if (key.length() < 2 && !containsCjk(key)) {
                    continue;
                }
                if (text.contains(key)) {
                    ok = true;
                    break;
                }
            }
            if (ok) {
                filtered.add(p);
            }
        }
        if (filtered.isEmpty() && (dailyIntent || beddingIntent || foodIntent || detectIntent(lowered) != IntentType.NONE)) {
            // Do not over-filter broad intents like "daily goods".
            return candidates;
        }
        return filtered;
    }

    private List<ProductVO> fallbackGlobalByConstraints(QueryConstraints constraints) {
        ensureProductCacheLoaded();
        List<ProductVO> all = new ArrayList<>(productCache.values());
        List<ProductVO> constrained = applyConstraints(all, constraints);
        if (constrained.isEmpty()) {
            return List.of();
        }
        return constrained.stream().limit(topK).toList();
    }

    private List<ProductVO> layeredFallback(String prompt, QueryConstraints constraints, IntentType intent) {
        ensureProductCacheLoaded();
        List<ProductVO> all = intent != IntentType.NONE
            ? new ArrayList<>(strictIntentRetrieve(intent))
            : new ArrayList<>(productCache.values());
        List<ProductVO> stage1 = rerankCandidates(prompt, constraints, applyConstraints(all, constraints), intent);
        if (!stage1.isEmpty()) {
            return stage1.stream().limit(topK).toList();
        }

        QueryConstraints relaxedBrand = new QueryConstraints(
            constraints.minPrice(), constraints.maxPrice(), constraints.requestedCount(), constraints.sortPref(),
            List.of(), List.of(), constraints.attributeTerms()
        );
        List<ProductVO> stage2 = rerankCandidates(prompt, relaxedBrand, applyConstraints(all, relaxedBrand), intent);
        if (!stage2.isEmpty()) {
            return stage2.stream().limit(topK).toList();
        }

        if (constraints.maxPrice() != null) {
            BigDecimal widenedMax = constraints.maxPrice().multiply(BigDecimal.valueOf(1.3));
            QueryConstraints widenedBudget = new QueryConstraints(
                constraints.minPrice(), widenedMax, constraints.requestedCount(), constraints.sortPref(),
                List.of(), List.of(), List.of()
            );
            List<ProductVO> stage3 = rerankCandidates(prompt, widenedBudget, applyConstraints(all, widenedBudget), intent);
            if (!stage3.isEmpty()) {
                return stage3.stream().limit(topK).toList();
            }
        }

        List<ProductVO> hot = new ArrayList<>(all);
        hot.removeIf(p -> p.getStock() != null && p.getStock() <= 0);
        hot.sort(Comparator.comparing((ProductVO p) -> p.getSales() == null ? 0 : p.getSales()).reversed());
        return hot.stream().limit(topK).toList();
    }

    private List<ProductVO> rerankCandidates(String prompt, QueryConstraints constraints, List<ProductVO> candidates, IntentType intent) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<ProductVO> hard = applyIntentFilter(prompt, candidates);
        if (hard.isEmpty()) {
            if (intent == IntentType.MOUSE || intent == IntentType.KEYBOARD || intent == IntentType.HEADPHONE || intent == IntentType.OUTDOOR) {
                return List.of();
            }
            hard = candidates;
        }
        List<String> tokens = splitQueryTokens(normalizeQuery(prompt));
        String loweredPrompt = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        boolean textbookQuery = containsAny(loweredPrompt, "textbook", "\u6559\u6750");
        boolean cosmeticQuery = containsAny(loweredPrompt, "cosmetic", "\u7f8e\u5986", "\u5f69\u5986");
        boolean kitchenStorageQuery = containsAnyTerm(loweredPrompt, KITCHEN_CORE_TERMS) && containsAnyTerm(loweredPrompt, STORAGE_CORE_TERMS);
        boolean explicitBikeQuery = containsAny(
            loweredPrompt,
            "\u81ea\u884c\u8f66", "\u9a91\u884c", "\u5c71\u5730\u8f66", "\u516c\u8def\u8f66",
            "bicycle", "mountain bike", "road bike", "mtb", "bmx", "cycling"
        );
        boolean explicitOutdoorQuery = containsAnyTerm(loweredPrompt, OUTDOOR_TERMS);
        List<ScoredProduct> scored = new ArrayList<>();
        for (ProductVO p : hard) {
            String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getBrand()) + " " + nvl(p.getDescription())).toLowerCase(Locale.ROOT);
            if (explicitBikeQuery && !containsAnyTerm(text, BIKE_PRODUCT_TERMS)) {
                continue;
            }
            if ((intent == IntentType.OUTDOOR || explicitOutdoorQuery) && !isOutdoorLikeProduct(p)) {
                continue;
            }
            if (intent == IntentType.BOOK && textbookQuery && !containsAnyTerm(text, BOOK_TEXTBOOK_CORE_TERMS)) {
                continue;
            }
            if (intent == IntentType.MAKEUP && cosmeticQuery && !containsAnyTerm(text, MAKEUP_QUERY_STRICT_TERMS)) {
                continue;
            }
            if (intent == IntentType.DAILY && kitchenStorageQuery && !hasKitchenStorageSignals(p)) {
                continue;
            }
            int score = 0;
            for (String t : tokens) {
                if (t.length() < 2 && !containsCjk(t)) continue;
                if (text.contains(t.toLowerCase(Locale.ROOT))) {
                    score += 6;
                }
            }
            if (intent != IntentType.NONE) {
                score += 8;
            }
            if (intent == IntentType.BIKE && containsAnyTerm(text, BIKE_PRODUCT_TERMS)) {
                score += 8;
            }
            if (intent == IntentType.BIKE && containsAnyTerm(text, BIKE_NON_PRODUCT_TERMS) && !containsAnyTerm(text, BIKE_PRODUCT_TERMS)) {
                score -= 12;
            }
            if (intent == IntentType.OUTDOOR && containsAnyTerm(text, OUTDOOR_TERMS)) {
                score += 8;
            }
            if (intent == IntentType.BOOK && textbookQuery && containsAnyTerm(text, BOOK_TEXTBOOK_CORE_TERMS)) {
                score += 8;
            }
            if (intent == IntentType.MAKEUP && cosmeticQuery && containsAnyTerm(text, MAKEUP_CORE_TERMS)) {
                score += 6;
            }
            if (intent == IntentType.DAILY && kitchenStorageQuery && hasKitchenStorageSignals(p)) {
                score += 8;
            }
            if (constraints.sortPref() == SortPref.SALES_DESC && p.getSales() != null) {
                score += Math.min(20, p.getSales() / 1000);
            }
            if (constraints.sortPref() == SortPref.RATING_DESC && p.getRating() != null) {
                score += p.getRating().multiply(BigDecimal.TEN).intValue();
            }
            if (p.getStock() != null && p.getStock() > 0) {
                score += 3;
            }
            scored.add(new ScoredProduct(p, score));
        }
        scored.sort((a, b) -> Integer.compare(b.score(), a.score()));
        List<ProductVO> out = new ArrayList<>();
        for (ScoredProduct s : scored) {
            out.add(s.product());
        }
        return applyGeneralTopicConsistencyFilter(prompt, out, intent);
    }

    private List<ProductVO> applyGeneralTopicConsistencyFilter(String prompt, List<ProductVO> candidates, IntentType intent) {
        if (prompt == null || prompt.isBlank() || candidates == null || candidates.isEmpty()) {
            return candidates == null ? List.of() : candidates;
        }
        List<String> anchors = buildGeneralAnchorTerms(prompt, intent);
        if (anchors.isEmpty()) {
            return candidates;
        }
        int minScore = minTopicAnchorScore(intent, anchors);
        List<ProductVO> filtered = new ArrayList<>();
        for (ProductVO p : candidates) {
            if (topicAnchorScore(p, anchors) >= minScore) {
                filtered.add(p);
            }
        }
        return filtered;
    }

    private int minTopicAnchorScore(IntentType intent, List<String> anchors) {
        if (intent == IntentType.BAG || intent == IntentType.DAILY || intent == IntentType.BOOK || intent == IntentType.TOY || intent == IntentType.BIKE || intent == IntentType.OUTDOOR) {
            return anchors.size() >= 2 ? 2 : 1;
        }
        return 1;
    }

    private List<String> buildGeneralAnchorTerms(String prompt, IntentType intent) {
        LinkedHashSet<String> anchors = new LinkedHashSet<>();
        String normalized = normalizeQuery(prompt);
        for (String token : splitQueryTokens(normalized)) {
            String t = token == null ? "" : token.toLowerCase(Locale.ROOT).trim();
            if (t.isBlank() || QUERY_STOP_WORDS.contains(t)) {
                continue;
            }
            if (!containsCjk(t) && t.length() < 3) {
                continue;
            }
            anchors.add(t);
        }
        for (String token : extractPromptTopicTokens(prompt)) {
            String t = token == null ? "" : token.toLowerCase(Locale.ROOT).trim();
            if (!t.isBlank()) {
                anchors.add(t);
            }
        }
        if (intent != null && intent != IntentType.NONE) {
            addIntentTerms(anchors, intent);
        }
        // Keep generic anchor terms precision-first. Alias expansion can introduce noisy cross-category terms.
        return new ArrayList<>(anchors);
    }

    private int topicAnchorScore(ProductVO p, List<String> anchors) {
        String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getBrand()) + " " + nvl(p.getCategoryLevel3()))
            .toLowerCase(Locale.ROOT);
        int score = 0;
        for (String a : anchors) {
            if (a == null || a.isBlank()) {
                continue;
            }
            if (text.contains(a)) {
                score += containsCjk(a) ? 2 : 1;
            }
        }
        return score;
    }

    private void addIntentTerms(Set<String> anchors, IntentType intent) {
        List<String> terms = List.of();
        if (intent == IntentType.MOUSE) {
            terms = MOUSE_STRICT_TERMS;
        } else if (intent == IntentType.KEYBOARD) {
            terms = KEYBOARD_STRICT_TERMS;
        } else if (intent == IntentType.HEADPHONE) {
            terms = HEADPHONE_STRICT_TERMS;
        } else if (intent == IntentType.SHOE) {
            terms = SHOE_TERMS;
        } else if (intent == IntentType.BAG) {
            terms = BAG_STRONG_TERMS;
        } else if (intent == IntentType.LIGHT) {
            terms = LIGHT_TERMS;
        } else if (intent == IntentType.BIKE) {
            terms = BIKE_TERMS;
        } else if (intent == IntentType.OUTDOOR) {
            terms = OUTDOOR_TERMS;
        } else if (intent == IntentType.COMPUTER) {
            terms = COMPUTER_TERMS;
        } else if (intent == IntentType.ELECTRONICS) {
            terms = ELECTRONICS_TERMS;
        } else if (intent == IntentType.DAILY) {
            terms = DAILY_STRONG_TERMS;
        } else if (intent == IntentType.PET) {
            terms = PET_TERMS;
        } else if (intent == IntentType.BABY) {
            terms = BABY_TERMS;
        } else if (intent == IntentType.BOOK) {
            terms = BOOK_STRONG_TERMS;
        } else if (intent == IntentType.TOY) {
            terms = TOY_TERMS;
        } else if (intent == IntentType.MAKEUP) {
            terms = MAKEUP_TERMS;
        }
        for (String t : terms) {
            String norm = t == null ? "" : t.toLowerCase(Locale.ROOT).trim();
            if (norm.isBlank()) {
                continue;
            }
            if (!containsCjk(norm) && norm.length() < 3) {
                continue;
            }
            anchors.add(norm);
        }
    }

    private AgentReply maybeAskClarifyingQuestion(String prompt, IntentType intent, QueryConstraints constraints) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        boolean hasPrice = constraints.minPrice() != null || constraints.maxPrice() != null;
        boolean hasSort = constraints.sortPref() != SortPref.DEFAULT;
        boolean broadAsk = containsAny(lowered,
            "\u63a8\u8350\u4e00\u4e0b", "\u968f\u4fbf\u63a8\u8350", "\u6709\u4ec0\u4e48\u597d\u7684", "\u6709\u54ea\u4e9b",
            "recommend", "suggest", "anything");
        if (intent == IntentType.NONE && !hasPrice && !hasSort && broadAsk) {
            return new AgentReply("\u4f60\u66f4\u60f3\u4e70\u54ea\u7c7b\u5546\u54c1\uff1f\u53ef\u4ee5\u76f4\u63a5\u8bf4\u201c\u54c1\u7c7b + \u9884\u7b97 + \u7528\u9014\u201d\uff0c\u4f8b\u5982\uff1a\u84dd\u7259\u8033\u673a 300 \u5143\u5185 \u901a\u52e4\u7528\u3002", List.of());
        }
        if (intent == IntentType.NONE && hasSort) {
            List<String> topicTokens = extractPromptTopicTokens(prompt);
            if (topicTokens == null || topicTokens.isEmpty()) {
                return new AgentReply("\u4f60\u60f3\u6bd4\u7684\u662f\u54ea\u4e00\u7c7b\u5546\u54c1\uff1f\u4f8b\u5982\uff1a\u201c\u8bc4\u5206\u6700\u9ad8\u7684\u8033\u673a\u201d\u6216\u201c\u6700\u4fbf\u5b9c\u7684\u88ab\u5b50\u201d\u3002", List.of());
            }
        }
        return null;
    }

    private AgentReply handleHumanHandoff(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        if (containsAnyTerm(lowered, HANDOFF_TERMS)) {
            return new AgentReply("\u8fd9\u7c7b\u95ee\u9898\u6d89\u53ca\u552e\u540e/\u98ce\u9669\u5904\u7406\uff0c\u6211\u5df2\u4e3a\u4f60\u5207\u6362\u4eba\u5de5\u5ba2\u670d\u5904\u7406\u6d41\u7a0b\u3002\u8bf7\u63d0\u4f9b\u8ba2\u5355\u53f7\u548c\u95ee\u9898\u622a\u56fe\u3002", List.of());
        }
        return null;
    }
    private QueryConstraints parseConstraints(String prompt) {
        if (prompt == null) {
            return new QueryConstraints(null, null, 4, SortPref.DEFAULT, List.of(), List.of(), List.of());
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        BigDecimal min = null;
        BigDecimal max = null;
        Set<String> includeBrands = new LinkedHashSet<>();
        Set<String> excludeBrands = new LinkedHashSet<>();
        Set<String> attributeTerms = new LinkedHashSet<>();

        Matcher range = RANGE_PATTERN.matcher(lowered);
        if (range.find()) {
            int a = Integer.parseInt(range.group(1));
            int b = Integer.parseInt(range.group(2));
            min = BigDecimal.valueOf(Math.min(a, b));
            max = BigDecimal.valueOf(Math.max(a, b));
        } else {
            Matcher maxMatcher = MAX_PRICE_PATTERN.matcher(lowered);
            if (maxMatcher.find()) {
                max = BigDecimal.valueOf(Integer.parseInt(maxMatcher.group(1)));
            }
            Matcher minMatcher = MIN_PRICE_PATTERN.matcher(lowered);
            if (minMatcher.find()) {
                min = BigDecimal.valueOf(Integer.parseInt(minMatcher.group(1)));
            }
        }

        int count = 4;
        Matcher countMatcher = COUNT_PATTERN.matcher(lowered);
        if (countMatcher.find()) {
            try {
                count = Integer.parseInt(countMatcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        } else if (lowered.contains("top 10") || lowered.contains("top10")) {
            count = 10;
        } else if (containsAny(lowered, "\u591a\u6765\u51e0\u4e2a", "\u518d\u6765\u51e0\u4e2a", "more")) {
            count = 6;
        }
        count = Math.max(1, Math.min(10, count));

        SortPref sortPref = SortPref.DEFAULT;
        if (containsAnyTerm(lowered, VALUE_TERMS)) {
            sortPref = SortPref.PRICE_ASC;
        } else if (containsAnyTerm(lowered, PREMIUM_TERMS)) {
            sortPref = SortPref.PRICE_DESC;
        } else if (containsAny(lowered, "\u9500\u91cf") || containsAnyTerm(lowered, HOT_TERMS)) {
            sortPref = SortPref.SALES_DESC;
        } else if (containsAny(lowered, "\u8bc4\u5206") || containsAnyTerm(lowered, RATING_TERMS)) {
            sortPref = SortPref.RATING_DESC;
        }

        Matcher includeBrandMatcher = BRAND_ONLY_PATTERN.matcher(prompt);
        while (includeBrandMatcher.find()) {
            String b = includeBrandMatcher.group(1);
            if (b != null && !b.isBlank()) {
                includeBrands.add(b.trim().toLowerCase(Locale.ROOT));
            }
        }
        Matcher excludeBrandMatcher = BRAND_EXCLUDE_PATTERN.matcher(prompt);
        while (excludeBrandMatcher.find()) {
            String b = excludeBrandMatcher.group(1);
            if (b != null && !b.isBlank()) {
                excludeBrands.add(b.trim().toLowerCase(Locale.ROOT));
            }
        }
        for (String t : ATTRIBUTE_TERMS) {
            if (lowered.contains(t.toLowerCase(Locale.ROOT))) {
                attributeTerms.add(t.toLowerCase(Locale.ROOT));
            }
        }
        Matcher specMatcher = SPEC_TOKEN_PATTERN.matcher(lowered);
        while (specMatcher.find()) {
            String token = specMatcher.group(1);
            if (token != null && !token.isBlank()) {
                attributeTerms.add(token.replaceAll("\\s+", ""));
            }
        }

        return new QueryConstraints(
            min,
            max,
            count,
            sortPref,
            new ArrayList<>(includeBrands),
            new ArrayList<>(excludeBrands),
            new ArrayList<>(attributeTerms)
        );
    }
    private List<ProductVO> applyConstraints(List<ProductVO> candidates, QueryConstraints constraints) {
        if (candidates == null || candidates.isEmpty() || constraints == null) {
            return candidates == null ? List.of() : candidates;
        }
        List<ProductVO> filtered = new ArrayList<>();
        for (ProductVO p : candidates) {
            if (p.getStock() != null && p.getStock() <= 0) {
                continue;
            }
            BigDecimal price = p.getPrice();
            if (constraints.minPrice() != null && (price == null || price.compareTo(constraints.minPrice()) < 0)) {
                continue;
            }
            if (constraints.maxPrice() != null && (price == null || price.compareTo(constraints.maxPrice()) > 0)) {
                continue;
            }
            String brand = nvl(p.getBrand()).toLowerCase(Locale.ROOT);
            String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getDescription())).toLowerCase(Locale.ROOT);
            if (!constraints.includeBrands().isEmpty()) {
                boolean anyIncluded = false;
                for (String b : constraints.includeBrands()) {
                    if (brand.contains(b) || text.contains(b)) {
                        anyIncluded = true;
                        break;
                    }
                }
                if (!anyIncluded) {
                    continue;
                }
            }
            if (!constraints.excludeBrands().isEmpty()) {
                boolean anyExcluded = false;
                for (String b : constraints.excludeBrands()) {
                    if (brand.contains(b) || text.contains(b)) {
                        anyExcluded = true;
                        break;
                    }
                }
                if (anyExcluded) {
                    continue;
                }
            }
            if (!constraints.attributeTerms().isEmpty()) {
                int matched = 0;
                for (String t : constraints.attributeTerms()) {
                    if (text.contains(t)) {
                        matched++;
                    }
                }
                int need = Math.max(1, (constraints.attributeTerms().size() + 1) / 2);
                if (matched < need) {
                    continue;
                }
            }
            filtered.add(p);
        }
        if (filtered.isEmpty()) {
            return filtered;
        }

        Comparator<ProductVO> comparator = switch (constraints.sortPref()) {
            case PRICE_ASC -> Comparator.comparing(p -> p.getPrice() == null ? BigDecimal.valueOf(Double.MAX_VALUE) : p.getPrice());
            case PRICE_DESC -> Comparator.comparing((ProductVO p) -> p.getPrice() == null ? BigDecimal.ZERO : p.getPrice()).reversed();
            case SALES_DESC -> Comparator.comparing((ProductVO p) -> p.getSales() == null ? 0 : p.getSales()).reversed();
            case RATING_DESC -> Comparator.comparing((ProductVO p) -> p.getRating() == null ? BigDecimal.ZERO : p.getRating()).reversed();
            default -> Comparator.comparing((ProductVO p) -> p.getSales() == null ? 0 : p.getSales()).reversed()
                .thenComparing((ProductVO p) -> p.getRating() == null ? BigDecimal.ZERO : p.getRating(), Comparator.reverseOrder());
        };
        filtered.sort(comparator);
        return filtered;
    }

    private IntentType detectIntent(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return IntentType.NONE;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        Map<IntentType, Integer> pos = new LinkedHashMap<>();
        pos.put(IntentType.MOUSE, lastIndexAny(lowered, MOUSE_TERMS));
        pos.put(IntentType.KEYBOARD, lastIndexAny(lowered, KEYBOARD_TERMS));
        pos.put(IntentType.PET, lastIndexAny(lowered, PET_TERMS));
        pos.put(IntentType.BABY, lastIndexAny(lowered, BABY_TERMS));
        pos.put(IntentType.BOOK, lastIndexAny(lowered, BOOK_TERMS));
        pos.put(IntentType.TOY, lastIndexAny(lowered, TOY_TERMS));
        pos.put(IntentType.MAKEUP, lastIndexAny(lowered, MAKEUP_TERMS));
        pos.put(IntentType.HEADPHONE, lastIndexAny(lowered, HEADPHONE_TERMS));
        pos.put(IntentType.SHOE, lastIndexAny(lowered, SHOE_TERMS));
        pos.put(IntentType.BAG, lastIndexAny(lowered, BAG_TERMS));
        pos.put(IntentType.LIGHT, lastIndexAny(lowered, LIGHT_TERMS));
        pos.put(IntentType.BIKE, lastIndexAny(lowered, BIKE_TERMS));
        pos.put(IntentType.OUTDOOR, lastIndexAny(lowered, OUTDOOR_TERMS));
        pos.put(IntentType.COMPUTER, lastIndexAny(lowered, COMPUTER_TERMS));
        List<String> electronicsTerms = new ArrayList<>();
        for (String t : ELECTRONICS_TERMS) {
            String key = t == null ? "" : t.toLowerCase(Locale.ROOT).trim();
            if (!key.isBlank() && !ELECTRONICS_INTENT_EXCLUDE_TERMS.contains(key)) {
                electronicsTerms.add(key);
            }
        }
        int electronicsPos = lastIndexAny(lowered, electronicsTerms);
        int phonePos = lastPhoneIntentIndex(lowered);
        pos.put(IntentType.ELECTRONICS, Math.max(electronicsPos, phonePos));
        int dailyPos = lastIndexAny(lowered, DAILY_TERMS);
        int beddingPos = lastIndexAny(lowered, BEDDING_TERMS);
        int foodPos = lastIndexAny(lowered, FOOD_TERMS);
        pos.put(IntentType.BEDDING, beddingPos);
        pos.put(IntentType.DAILY, Math.max(dailyPos, foodPos));

        IntentType best = IntentType.NONE;
        int bestPos = -1;
        int bestPriority = -1;
        for (Map.Entry<IntentType, Integer> e : pos.entrySet()) {
            if (e.getValue() < 0) {
                continue;
            }
            int p = intentPriority(e.getKey());
            if (e.getValue() > bestPos || (e.getValue() == bestPos && p > bestPriority)) {
                best = e.getKey();
                bestPos = e.getValue();
                bestPriority = p;
            }
        }
        if (best == IntentType.NONE && containsAnyTerm(lowered, OUTDOOR_SCENARIO_TERMS)) {
            return IntentType.OUTDOOR;
        }
        if (best == IntentType.NONE && containsAnyTerm(lowered, TRAVEL_SCENARIO_TERMS)) {
            if (containsAnyTerm(lowered, OUTDOOR_SCENARIO_TERMS) || containsAnyTerm(lowered, OUTDOOR_TERMS)) {
                return IntentType.OUTDOOR;
            }
            return IntentType.BAG;
        }
        if (best == IntentType.NONE && containsAnyTerm(lowered, STAY_SCENARIO_TERMS)) {
            return IntentType.BEDDING;
        }
        if (best == IntentType.NONE && containsAnyTerm(lowered, EAT_SCENARIO_TERMS)) {
            return IntentType.DAILY;
        }
        return best;
    }

    private int lastPhoneIntentIndex(String lowered) {
        if (lowered == null || lowered.isBlank()) {
            return -1;
        }
        int best = -1;
        best = Math.max(best, lowered.lastIndexOf(ZH_PHONE));
        best = Math.max(best, lowered.lastIndexOf("smartphone"));
        best = Math.max(best, lowered.lastIndexOf("iphone"));
        best = Math.max(best, lowered.lastIndexOf("android"));
        best = Math.max(best, lastMatchStart(lowered, MOBILE_PHONE_PATTERN));
        best = Math.max(best, lastMatchStart(lowered, CELL_PHONE_PATTERN));
        best = Math.max(best, lastMatchStart(lowered, STANDALONE_PHONE_PATTERN));
        best = Math.max(best, lastMatchStart(lowered, MOBILE_TERM_PATTERN));
        return best;
    }

    private int lastMatchStart(String text, Pattern pattern) {
        if (text == null || text.isBlank() || pattern == null) {
            return -1;
        }
        Matcher matcher = pattern.matcher(text);
        int best = -1;
        while (matcher.find()) {
            best = matcher.start();
        }
        return best;
    }

    private int intentPriority(IntentType intent) {
        return switch (intent) {
            case MOUSE, KEYBOARD, HEADPHONE, SHOE, BAG, LIGHT, BIKE, OUTDOOR, PET, BABY, BOOK, TOY, MAKEUP, BEDDING -> 3;
            case COMPUTER -> 2;
            case ELECTRONICS, DAILY -> 1;
            default -> 0;
        };
    }
    private boolean isFollowUpQuery(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        if (FOLLOW_UP_PATTERN.matcher(lowered).matches()) {
            return true;
        }
        return lowered.contains("\u8fd8\u6709")
            || lowered.contains("\u8fd8\u6709\u5417")
            || lowered.contains("\u8fd8\u6709\u5417\uff1f")
            || lowered.contains("\u5176\u4ed6")
            || lowered.contains("\u4f60\u63a8\u8350\u6211\u4e70\u54ea\u4e2a")
            || lowered.contains("\u4f60\u63a8\u8350\u4e70\u54ea\u4e2a")
            || lowered.contains("\u63a8\u8350\u6211\u4e70\u54ea\u4e2a")
            || lowered.contains("\u8fd9\u51e0\u4e2a\u91cc\u54ea\u4e2a")
            || lowered.contains("\u8fd9\u4e9b\u91cc\u54ea\u4e2a")
            || lowered.contains("\u9009\u54ea\u4e2a")
            || lowered.contains("\u4e70\u54ea\u4e2a")
            || lowered.contains("\u8fd9\u4e2a")
            || lowered.contains("\u5b83")
            || lowered.contains("\u518d\u6765")
            || lowered.contains("\u591a\u6765\u51e0\u4e2a")
            || lowered.contains("\u591a\u6765\u70b9")
            || lowered.contains("\u518d\u6765\u51e0\u4e2a")
            || lowered.contains("\u518d\u7ed9\u51e0\u4e2a")
            || lowered.contains("\u518d\u6765\u4e00\u6279")
            || lowered.contains("\u518d\u63a8\u8350")
            || lowered.contains("\u6362\u4e00\u6279")
            || lowered.contains("\u6765\u70b9\u522b\u7684")
            || lowered.contains("\u96be\u9053\u5c31\u8fd9\u4e9b\u5417")
            || lowered.contains("\u5c31\u8fd9\u4e9b\u5417")
            || lowered.contains("\u5c31\u8fd9\u4e48\u591a")
            || lowered.contains("\u6ca1\u522b\u7684\u4e86\u5417")
            || lowered.contains("\u8fd8\u6709\u522b\u7684\u5417")
            || lowered.contains("\u8fd8\u6709\u522b\u7684\u4e48")
            || lowered.contains("\u53ea\u6709\u8fd9\u4e9b\u5417")
            || lowered.contains("\u53ea\u6709\u8fd9\u4e48\u70b9")
            || lowered.contains("\u8fd8\u80fd\u63a8\u8350\u5417")
            || lowered.contains("\u8fd8\u80fd\u627e\u5230\u5417")
            || lowered.contains("\u8fd8\u6709\u4ec0\u4e48")
            || lowered.contains("\u518d\u63a8\u8350\u4e00\u4e2a")
            || lowered.contains("\u518d\u6765\u4e00\u4e2a")
            || lowered.contains("\u518d\u6765\u4e00\u4e2a\u5427")
            || lowered.contains("\u90a3\u4f60\u7ed9\u6211\u63a8\u8350\u4e00\u4e2a")
            || lowered.contains("\u90a3\u4f60\u518d\u7ed9\u6211\u63a8\u8350\u4e00\u4e2a")
            || lowered.contains("\u7ed9\u6211\u63a8\u8350\u4e00\u4e2a")
            || lowered.contains("\u7ed9\u6211\u6765\u4e00\u4e2a")
            || lowered.contains("\u63a8\u8350\u4e00\u4e2a")
            || lowered.contains("\u6765\u4e00\u4e2a")
            || lowered.contains("\u8fd9\u4e9b\u4e4b\u5916\u5462")
            || lowered.contains("\u9664\u4e86\u8fd9\u4e9b\u8fd8\u6709")
            || lowered.contains("\u5c31\u8fd9\u4e9b\u4e86\u5417")
            || lowered.contains("\u8fd9\u5c31\u5b8c\u4e86\u5417")
            || lowered.contains("\u8fd8\u6709\u66f4\u597d\u7684\u5417")
            || lowered.contains("\u8fd8\u6709\u66f4\u4fbf\u5b9c\u7684\u5417")
            || lowered.contains("\u6709\u6ca1\u6709\u4fbf\u5b9c\u4e00\u70b9\u7684")
            || lowered.contains("\u6709\u6ca1\u6709\u66f4\u4fbf\u5b9c\u7684")
            || lowered.contains("\u4fbf\u5b9c\u4e00\u70b9")
            || lowered.contains("\u4fbf\u5b9c\u70b9")
            || lowered.contains("\u518d\u4fbf\u5b9c\u4e00\u70b9")
            || lowered.contains("\u80fd\u4e0d\u80fd\u518d\u4fbf\u5b9c\u70b9")
            || lowered.contains("more")
            || lowered.contains("another")
            || lowered.contains("recommend one")
            || lowered.contains("give me one")
            || lowered.contains("one more")
            || lowered.contains("more please")
            || lowered.contains("show more")
            || lowered.contains("is that all")
            || lowered.contains("only these")
            || lowered.contains("else");
    }

    private boolean isComparativeFollowUp(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        return containsAny(
            lowered,
            "\u4fbf\u5b9c\u4e00\u70b9", "\u4fbf\u5b9c\u70b9", "\u66f4\u4fbf\u5b9c", "\u4ef7\u683c\u4f4e\u4e00\u70b9",
            "\u8d35\u4e00\u70b9", "\u66f4\u9ad8\u7aef", "\u518d\u4f18\u60e0\u4e00\u70b9",
            "\u8fd9\u4e2a\u6863\u4f4d", "\u540c\u7c7b", "cheaper", "lower", "better", "similar"
        );
    }

    private boolean isRecommendOneFollowUp(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        return containsAny(
            lowered,
            "\u63a8\u8350\u4e00\u4e2a", "\u7ed9\u6211\u63a8\u8350\u4e00\u4e2a", "\u90a3\u4f60\u7ed9\u6211\u63a8\u8350\u4e00\u4e2a", "\u518d\u63a8\u8350\u4e00\u4e2a",
            "\u6765\u4e00\u4e2a", "\u518d\u6765\u4e00\u4e2a", "\u7ed9\u6211\u6765\u4e00\u4e2a",
            "recommend one", "give me one", "one more"
        );
    }

    private boolean isRefinementFollowUp(String prompt, ConversationMemory memory) {
        if (prompt == null || prompt.isBlank() || memory == null) {
            return false;
        }
        IntentType memoryIntent = inferIntentFromMemory(memory);
        if (memoryIntent == IntentType.NONE) {
            return false;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        boolean refinementCue = hasRefinementPreferenceCue(lowered);
        if (hasScopeResetCue(lowered) || (looksLikeFreshSearch(prompt) && !isPreferenceOnlyPrompt(lowered) && !refinementCue)) {
            return false;
        }
        IntentType promptIntent = detectIntent(prompt);
        if (promptIntent != IntentType.NONE
            && promptIntent != memoryIntent
            && !(refinementCue && intentPriority(promptIntent) < intentPriority(memoryIntent))) {
            return false;
        }
        return refinementCue;
    }

    private boolean hasRefinementPreferenceCue(String lowered) {
        return containsAny(
            lowered,
            "\u65e5\u5e38\u7a7f", "\u65e5\u5e38", "\u901a\u52e4", "\u4f11\u95f2", "\u8212\u9002", "\u8f7b\u4fbf", "\u767e\u642d", "\u4e0a\u73ed\u7a7f", "\u5b66\u751f\u7a7f",
            "\u504f\u539a", "\u52a0\u539a", "\u4fdd\u6696", "\u504f\u8584", "\u8f7b\u8584", "\u900f\u6c14",
            "\u5927\u53f7", "\u7279\u5927\u53f7", "\u5927\u53f7\u5e8a", "\u7279\u5927\u53f7\u5e8a",
            "\u539a\u4e00\u70b9", "\u6696\u4e00\u70b9", "\u539a\u6696\u4e00\u70b9", "\u539a\u6696", "\u66f4\u6696", "\u6696\u548c\u70b9",
            "daily", "casual", "commute", "comfortable", "lightweight", "everyday wear", "thick", "thin", "warm", "warmer", "breathable", "queen", "king"
        );
    }

    private boolean isSlotRefinementFollowUp(String prompt, ConversationMemory memory) {
        if (prompt == null || prompt.isBlank() || memory == null) {
            return false;
        }
        if (lastShownProducts(memory).isEmpty()) {
            return false;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        if (isAddToCartIntent(lowered)
            || isCheckoutIntent(lowered)
            || isIncrementIntent(lowered)
            || isBatchAddIntent(lowered)
            || isRemoveCartIntent(lowered)
            || isCartQueryIntent(lowered)
            || isClearCartIntent(lowered)
            || isReplaceProductIntent(lowered)) {
            return false;
        }
        IntentType memoryIntent = inferIntentFromMemory(memory);
        IntentType promptIntent = detectIntent(prompt);
        if (promptIntent != IntentType.NONE && promptIntent != memoryIntent) {
            return false;
        }
        return containsAny(
            lowered,
            "\u5927\u53f7", "\u7279\u5927\u53f7", "\u5927\u53f7\u5e8a", "\u7279\u5927\u53f7\u5e8a", "queen", "king",
            "\u504f\u539a", "\u504f\u8584", "\u8f7b\u8584", "\u900f\u6c14", "\u4fdd\u6696", "\u539a\u6696",
            "\u53ea\u8981", "\u4e0d\u8981\u5957\u88c5", "only", "without set", "no set"
        );
    }

    private boolean isPureAttributeMetricQuestion(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        return containsAny(
            lowered,
            "\u8bc4\u5206", "\u53e3\u7891", "\u9500\u91cf", "\u4ef7\u683c", "\u5e93\u5b58", "\u54c1\u724c", "\u7c7b\u76ee",
            "rating", "sales", "sold", "price", "stock", "inventory", "brand", "category"
        );
    }

    private boolean isContextualShortFollowUp(String prompt, ConversationMemory memory) {
        if (prompt == null || prompt.isBlank() || memory == null) {
            return false;
        }
        if (isExplicitNewNeedPrompt(prompt)) {
            return false;
        }
        IntentType memoryIntent = inferIntentFromMemory(memory);
        if (memoryIntent == IntentType.NONE) {
            return false;
        }
        IntentType promptIntent = detectIntent(prompt);
        if (promptIntent != IntentType.NONE && promptIntent != memoryIntent) {
            return false;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        if (isAddToCartIntent(lowered)
            || isCheckoutIntent(lowered)
            || isIncrementIntent(lowered)
            || isBatchAddIntent(lowered)
            || isRemoveCartIntent(lowered)
            || isCartQueryIntent(lowered)
            || isClearCartIntent(lowered)
            || isReplaceProductIntent(lowered)) {
            return false;
        }
        List<String> tokens = splitQueryTokens(normalizeQuery(prompt));
        int effective = 0;
        for (String t : tokens) {
            if (t == null || t.isBlank() || QUERY_STOP_WORDS.contains(t)) {
                continue;
            }
            if (!containsCjk(t) && t.length() < 3) {
                continue;
            }
            effective++;
        }
        boolean shortUtterance = effective <= 3;
        boolean contextualCue = containsAny(
            lowered,
            "\u504f\u539a", "\u504f\u8584", "\u66f4\u4fdd\u6696", "\u66f4\u8f7b\u8584", "\u9009\u4e00\u4e2a", "\u9009\u54ea\u4e2a", "\u4e70\u54ea\u4e2a",
            "\u53ea\u8981", "\u53ea\u8981\u88ab\u5b50", "\u4e0d\u8981\u5957\u88c5",
            "\u5927\u53f7\u5e8a", "\u7279\u5927\u53f7\u5e8a", "\u5927\u53f7", "\u7279\u5927\u53f7", "queen", "king",
            "\u539a\u4e00\u70b9", "\u6696\u4e00\u70b9", "\u539a\u6696\u4e00\u70b9", "\u539a\u6696", "\u66f4\u6696", "\u6696\u548c\u70b9",
            "\u8fd9\u4e2a", "\u90a3\u4e2a", "\u7b2c\u4e00\u4e2a", "\u7b2c\u4e8c\u4e2a", "\u54ea\u4e2a\u66f4\u9002\u5408",
            "thick", "thin", "warmer", "lighter", "which one", "pick one", "this one", "that one", "the first", "the second"
        );
        return shortUtterance && contextualCue;
    }

    private AgentReply handlePreferenceRefinementFromLastShown(String prompt, ConversationMemory memory) {
        if (prompt == null || prompt.isBlank() || memory == null) {
            return null;
        }
        List<ProductVO> shown = lastShownProducts(memory);
        List<ProductVO> sourcePool = new ArrayList<>();
        if (memory.lastAttributeSourceIds != null && !memory.lastAttributeSourceIds.isEmpty()) {
            for (Long id : memory.lastAttributeSourceIds) {
                ProductVO p = id == null ? null : productCache.get(id);
                if (p != null) {
                    sourcePool.add(p);
                }
                if (sourcePool.size() >= 8) {
                    break;
                }
            }
        }
        if (sourcePool.isEmpty() && shown != null) {
            sourcePool.addAll(shown);
        }
        if (sourcePool.isEmpty()) {
            return null;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        if ((isExplicitNewNeedPrompt(prompt) && !isPreferenceOnlyPrompt(lowered))
            || hasScopeResetCue(lowered)
            || isAddToCartIntent(lowered)
            || isCheckoutIntent(lowered)
            || isExplicitCompareQuestion(prompt)) {
            return null;
        }
        IntentType promptIntent = detectIntent(prompt);
        IntentType memoryIntent = inferIntentFromMemory(memory);
        boolean refinementCue = hasRefinementPreferenceCue(lowered);
        if (promptIntent != IntentType.NONE
            && promptIntent != memoryIntent
            && !(refinementCue && intentPriority(promptIntent) < intentPriority(memoryIntent))) {
            return null;
        }

        PreferenceSlots slots = extractPreferenceSlots(lowered);
        if (!slots.hasAny()) {
            if (isRefinementFollowUp(prompt, memory)) {
                List<ProductVO> cards = sourcePool.stream().limit(Math.min(3, sourcePool.size())).toList();
                if (!cards.isEmpty()) {
                    ProductVO best = cards.get(0);
                    memory.lastShownProductIds = cards.stream().map(ProductVO::getId).filter(Objects::nonNull).toList();
                    memory.lastAttributeSourceIds = sourcePool.stream().map(ProductVO::getId).filter(Objects::nonNull).toList();
                    memory.lastFocusedProductId = best.getId();
                    return new AgentReply(
                        "\u6211\u5df2\u7ecf\u6309\u4f60\u8fd9\u8f6e\u7684\u7ec6\u5316\u6761\u4ef6\uff0c\u4f18\u5148\u6cbf\u7528\u5f53\u524d\u540c\u7c7b\u5019\u9009\u7ed9\u4f60\u7b5b\u4e86\u4e00\u4e0b\uff1a",
                        cards
                    );
                }
            }
            return null;
        }

        List<ScoredProduct> scored = new ArrayList<>();
        for (ProductVO p : sourcePool) {
            if (p == null) {
                continue;
            }
            int score = scoreByPreferenceSlots(p, slots);
            scored.add(new ScoredProduct(p, score));
        }
        if (scored.isEmpty()) {
            return null;
        }
        scored.sort((a, b) -> Integer.compare(b.score(), a.score()));
        List<ProductVO> cards = scored.stream().filter(s -> s.score() > -5).map(ScoredProduct::product).limit(Math.min(3, scored.size())).toList();
        if (cards.isEmpty()) {
            cards = scored.stream().map(ScoredProduct::product).limit(Math.min(3, scored.size())).toList();
        }
        ProductVO best = cards.get(0);
        String pref = slots.summaryLabel();
        String content = "\u6211\u5df2\u7ecf\u6309\u4f60\u8fd9\u8f6e\u7684\u504f\u597d\u300c" + pref + "\u300d\uff0c\u5728\u4e0a\u4e00\u6279\u5019\u9009\u91cc\u7ed9\u4f60\u4f18\u5148\u63a8\u8350\uff1a\n"
            + "1. " + nvl(best.getName()) + " (ID: " + best.getId() + ", \u4ef7\u683c: " + nvl(String.valueOf(best.getPrice())) + " " + nvl(best.getCurrency()) + ")\n"
            + "\u5982\u679c\u4f60\u613f\u610f\uff0c\u6211\u53ef\u4ee5\u76f4\u63a5\u5e2e\u4f60\u52a0\u5165\u8d2d\u7269\u8f66\u3002";
        memory.lastShownProductIds = cards.stream().map(ProductVO::getId).filter(Objects::nonNull).toList();
        memory.lastAttributeSourceIds = sourcePool.stream().map(ProductVO::getId).filter(Objects::nonNull).toList();
        memory.lastFocusedProductId = best.getId();
        return new AgentReply(content, cards);
    }

    private AgentReply handleRecommendOneFromLastShown(String prompt, ConversationMemory memory) {
        if (prompt == null || prompt.isBlank() || memory == null || !isRecommendOneFollowUp(prompt)) {
            return null;
        }
        List<ProductVO> shown = lastShownProducts(memory);
        if (shown.isEmpty()) {
            return null;
        }
        IntentType memoryIntent = inferIntentFromMemory(memory);
        List<ProductVO> scoped = new ArrayList<>(shown);
        if (memoryIntent == IntentType.HEADPHONE) {
            List<ProductVO> filtered = scoped.stream().filter(this::isStrictHeadphoneProduct).toList();
            if (!filtered.isEmpty()) {
                scoped = filtered;
            }
        } else if (memoryIntent == IntentType.ELECTRONICS) {
            List<ProductVO> filtered = scoped.stream().filter(this::isPhoneLikeProduct).toList();
            if (!filtered.isEmpty()) {
                scoped = filtered;
            }
        }
        ProductVO pick = scoped.get(0);
        memory.lastShownProductIds = List.of(pick.getId());
        memory.lastFocusedProductId = pick.getId();
        return new AgentReply(
            "\u6211\u4ece\u5f53\u524d\u8fd9\u6279\u5019\u9009\u91cc\u5148\u7ed9\u4f60\u63a8\u8350\u8fd9\u4e00\u4e2a\uff1a\n"
                + "1. " + nvl(pick.getName()) + " (ID: " + pick.getId() + ", \u4ef7\u683c: " + nvl(String.valueOf(pick.getPrice())) + " " + nvl(pick.getCurrency()) + ")\n"
                + "\u5982\u679c\u4f60\u60f3\uff0c\u6211\u4e5f\u53ef\u4ee5\u518d\u4ece\u540c\u7c7b\u91cc\u6362\u4e00\u4e2a\u7ed9\u4f60\u6bd4\u6bd4\u3002",
            List.of(pick)
        );
    }

    private PreferenceSlots extractPreferenceSlots(String lowered) {
        if (lowered == null) {
            lowered = "";
        }
        boolean preferWarm = containsAny(lowered, "\u504f\u539a", "\u539a\u4e00\u70b9", "\u52a0\u539a", "\u4fdd\u6696", "\u66f4\u4fdd\u6696", "\u6696\u4e00\u70b9", "\u539a\u6696", "\u66f4\u6696", "warm", "warmer", "thick", "heavyweight", "winter");
        boolean preferLight = containsAny(lowered, "\u504f\u8584", "\u8584\u4e00\u70b9", "\u8f7b\u8584", "\u900f\u6c14", "\u66f4\u8f7b", "lightweight", "breathable", "thin", "thinner", "cool", "summer");
        boolean preferQueen = containsAny(lowered, "\u5927\u53f7", "\u5927\u53f7\u5e8a", "queen");
        boolean preferKing = containsAny(lowered, "\u7279\u5927\u53f7", "\u7279\u5927\u53f7\u5e8a", "king");
        boolean excludeSet = containsAny(lowered, "\u4e0d\u8981\u5957\u88c5", "\u6392\u9664\u5957\u88c5", "\u4e0d\u8981\u5168\u5957", "\u53ea\u8981\u5355\u4ef6", "no set", "without set", "exclude set");
        boolean onlyMainItem = containsAny(lowered, "\u53ea\u8981", "\u53ea\u8981\u88ab\u5b50", "\u53ea\u8981\u4e3b\u4ef6", "only", "main item only", "duvet only", "comforter only");
        boolean cheaper = containsAny(lowered, "\u4fbf\u5b9c", "\u66f4\u4fbf\u5b9c", "\u5b9e\u60e0", "\u5212\u7b97", "\u9884\u7b97", "cheaper", "affordable", "budget", "lower price");
        boolean premium = containsAny(lowered, "\u9ad8\u7aef", "\u66f4\u597d", "\u8d28\u91cf\u66f4\u597d", "\u9ad8\u54c1\u8d28", "premium", "better quality", "high end");
        boolean topRated = containsAny(lowered, "\u9ad8\u8bc4\u5206", "\u8bc4\u5206\u9ad8", "\u53e3\u7891\u597d", "top rated", "higher rating", "best rated");
        return new PreferenceSlots(preferWarm, preferLight, preferQueen, preferKing, excludeSet, onlyMainItem, cheaper, premium, topRated);
    }

    private int scoreByPreferenceSlots(ProductVO p, PreferenceSlots slots) {
        String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getDescription())).toLowerCase(Locale.ROOT);
        int score = 0;
        if (slots.preferWarm()) {
            if (containsAny(text, "\u539a", "\u52a0\u539a", "\u4fdd\u6696", "\u51ac", "thick", "heavyweight", "warm", "winter")) score += 6;
            if (containsAny(text, "\u8584", "\u8f7b\u8584", "\u900f\u6c14", "thin", "lightweight", "breathable", "cooling", "summer")) score -= 4;
        }
        if (slots.preferLight()) {
            if (containsAny(text, "\u8584", "\u8f7b\u8584", "\u900f\u6c14", "thin", "lightweight", "breathable", "cooling", "summer")) score += 6;
            if (containsAny(text, "\u539a", "\u52a0\u539a", "\u4fdd\u6696", "thick", "heavyweight", "warm", "winter")) score -= 4;
        }
        if (slots.excludeSet() || slots.onlyMainItem()) {
            if (containsAny(text, "\u5957\u88c5", "\u5168\u5957", "\u4ef6\u5957", "\u7ec4\u5408", "set", "piece set", "7-piece", "5-piece")) score -= 7;
            if (containsAny(text, "\u5355\u4ef6", "\u4e3b\u4ef6", "single", "standalone")) score += 3;
        }
        if (slots.preferQueen()) {
            if (containsAny(text, "\u5927\u53f7", "queen")) score += 7;
            if (containsAny(text, "\u7279\u5927\u53f7", "king")) score -= 4;
        }
        if (slots.preferKing()) {
            if (containsAny(text, "\u7279\u5927\u53f7", "king")) score += 7;
            if (containsAny(text, "\u5927\u53f7", "queen")) score -= 4;
        }
        if (slots.cheaper() && p.getPrice() != null) {
            double price = p.getPrice().doubleValue();
            score += Math.max(0, 6 - (int) Math.min(5, price / 40.0));
        }
        if (slots.premium() && p.getPrice() != null) {
            double price = p.getPrice().doubleValue();
            score += Math.min(4, (int) (price / 60.0));
        }
        if (slots.topRated() && p.getRating() != null) {
            score += p.getRating().multiply(BigDecimal.valueOf(1.5)).intValue();
        }
        if (p.getRating() != null && p.getRating().compareTo(BigDecimal.valueOf(4.3)) >= 0) {
            score += 1;
        }
        if (p.getSales() != null) {
            score += Math.min(2, p.getSales() / 5000);
        }
        return score;
    }

    private boolean isPredicateOnlyFollowUp(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        boolean predicateHint = containsAny(
            lowered,
            "\u8bc4\u5206\u600e\u4e48\u6837", "\u8bc4\u5206\u600e\u4e48", "\u8bc4\u5206\u5462",
            "\u9500\u91cf\u600e\u4e48\u6837", "\u9500\u91cf\u5462",
            "\u4ef7\u683c\u5462", "\u4ef7\u683c\u600e\u4e48\u6837",
            "\u5e93\u5b58\u5462", "\u5e93\u5b58\u8fd8\u6709\u5417",
            "\u54c1\u724c\u5462", "\u7c7b\u76ee\u5462",
            "\u80fd\u4e0d\u80fd\u518d\u4fbf\u5b9c\u70b9", "\u518d\u4fbf\u5b9c\u70b9", "\u66f4\u4fbf\u5b9c\u70b9",
            "what about rating", "how is the rating", "how about sales", "what about stock", "and price"
        );
        if (!predicateHint) {
            return false;
        }
        return !hasExplicitSubject(prompt);
    }

    private boolean hasExplicitSubject(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        if (detectIntent(prompt) != IntentType.NONE) {
            return true;
        }
        if (extractOrdinalIndex(lowered) > 0) {
            return true;
        }
        if (PRODUCT_ID_PATTERN.matcher(prompt).find()) {
            return true;
        }
        return containsAny(
            lowered,
            "\u8fd9\u4e2a", "\u8fd9\u6b3e", "\u8fd9\u4ef6", "\u8fd9\u4e2a\u5546\u54c1",
            "this one", "that one", "it"
        );
    }

    private IntentType inferIntentFromMemory(ConversationMemory memory) {
        if (memory == null) {
            return IntentType.NONE;
        }
        if (memory.lastIntent != null && memory.lastIntent != IntentType.NONE) {
            return memory.lastIntent;
        }
        List<ProductVO> sampled = new ArrayList<>();
        if (memory.lastShownProductIds != null) {
            for (Long id : memory.lastShownProductIds) {
                ProductVO p = id == null ? null : productCache.get(id);
                if (p != null) {
                    sampled.add(p);
                }
                if (sampled.size() >= 6) {
                    break;
                }
            }
        }
        if (sampled.size() < 2 && memory.lastAttributeSourceIds != null) {
            for (Long id : memory.lastAttributeSourceIds) {
                ProductVO p = id == null ? null : productCache.get(id);
                if (p != null) {
                    sampled.add(p);
                }
                if (sampled.size() >= 6) {
                    break;
                }
            }
        }
        IntentType shownIntent = inferIntentFromShownProducts(sampled);
        if (shownIntent != IntentType.NONE) {
            return shownIntent;
        }
        StringBuilder sample = new StringBuilder();
        for (ProductVO p : sampled) {
            sample.append(nvl(p.getName())).append(" ").append(nvl(p.getCategory())).append(" ");
        }
        if (sample.length() == 0) {
            return IntentType.NONE;
        }
        IntentType inferred = detectIntent(sample.toString());
        return inferred == null ? IntentType.NONE : inferred;
    }

    private void rememberTurnContext(ConversationMemory memory, IntentType intentFromPrompt, List<ProductVO> shown, QueryConstraints constraints) {
        IntentType toRemember = intentFromPrompt;
        if (shown != null && !shown.isEmpty()) {
            IntentType shownIntent = inferIntentFromShownProducts(shown);
            if (shownIntent != IntentType.NONE
                && (toRemember == IntentType.NONE || intentPriority(shownIntent) >= intentPriority(toRemember))) {
                toRemember = shownIntent;
            }
        }
        if (toRemember != IntentType.NONE) {
            memory.lastIntent = toRemember;
            memory.lastTopicHint = topicHintByIntent(toRemember);
        } else if (shown != null && !shown.isEmpty()) {
            String hinted = topicHintByShown(shown);
            if (!hinted.isBlank()) {
                memory.lastTopicHint = hinted;
            }
        }

        List<Long> ids = new ArrayList<>();
        if (shown != null) {
            for (ProductVO p : shown) {
                if (p != null && p.getId() != null) {
                    ids.add(p.getId());
                }
            }
        }
        memory.previousShownProductIds = memory.lastShownProductIds == null ? List.of() : new ArrayList<>(memory.lastShownProductIds);
        memory.lastShownProductIds = ids;
        memory.pushShown(ids);
        if (!ids.isEmpty()) {
            memory.lastFocusedProductId = ids.get(0);
        }
        memory.lastConstraints = constraints;
    }

    private IntentType inferIntentFromShownProducts(List<ProductVO> shown) {
        if (shown == null || shown.isEmpty()) {
            return IntentType.NONE;
        }
        Map<IntentType, Integer> counts = new EnumMap<>(IntentType.class);
        for (ProductVO p : shown) {
            if (p == null) {
                continue;
            }
            String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getDescription())).trim();
            IntentType detected = detectIntent(text);
            if (detected == null || detected == IntentType.NONE) {
                continue;
            }
            counts.merge(detected, 1, Integer::sum);
        }
        IntentType best = IntentType.NONE;
        int bestCount = 0;
        int bestPriority = -1;
        for (Map.Entry<IntentType, Integer> entry : counts.entrySet()) {
            int count = entry.getValue();
            int priority = intentPriority(entry.getKey());
            if (count > bestCount || (count == bestCount && priority > bestPriority)) {
                best = entry.getKey();
                bestCount = count;
                bestPriority = priority;
            }
        }
        return best;
    }

    private AgentReply handleCompareFromLastShown(String prompt, ConversationMemory memory) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        boolean salesCompareTone = containsAny(
            lowered,
            "\u9500\u91cf\u6700\u9ad8", "\u54ea\u4e2a\u9500\u91cf\u6700\u9ad8", "\u66f4\u706b\u7206", "\u6700\u706b\u7206",
            "\u66f4\u70ed\u95e8", "\u6700\u70ed\u95e8", "\u7206\u6b3e", "best seller", "most popular", "most sold"
        );
        boolean ratingCompareTone = containsAny(lowered, "\u8bc4\u5206\u9ad8", "\u6700\u9ad8\u8bc4\u5206", "top rated", "highest rating");
        boolean priceCompareTone = containsAny(
            lowered,
            "\u6700\u4fbf\u5b9c", "\u4ef7\u683c\u6700\u4f4e", "\u54ea\u4e2a\u66f4\u4fbf\u5b9c", "\u66f4\u4fbf\u5b9c", "\u4fbf\u5b9c\u4e00\u70b9", "\u518d\u4fbf\u5b9c\u70b9",
            "cheapest", "lowest price", "which is cheaper", "cheaper"
        );
        boolean stockCompareTone = containsAny(lowered, "\u5e93\u5b58\u6700\u591a", "\u5e93\u5b58\u6700\u9ad8", "\u54ea\u4e2a\u5e93\u5b58\u591a", "most stock", "highest stock");
        boolean stockLowCompareTone = containsAny(lowered, "\u5e93\u5b58\u6700\u5c11", "\u5e93\u5b58\u6700\u4f4e", "\u54ea\u4e2a\u5e93\u5b58\u5c11", "least stock", "lowest stock");
        if (!containsAnyTerm(lowered, COMPARE_TERMS) && !salesCompareTone && !ratingCompareTone && !priceCompareTone && !stockCompareTone && !stockLowCompareTone) {
            return null;
        }
        boolean forceCurrentScope = hasCurrentScopeCue(lowered) || (memory.lastShownProductIds != null && !memory.lastShownProductIds.isEmpty() && !hasBatchScopeCue(lowered));
        List<Long> sourceIds;
        if (hasBatchScopeCue(lowered) && memory.lastAttributeSourceIds != null && memory.lastAttributeSourceIds.size() >= 2) {
            sourceIds = memory.lastAttributeSourceIds;
        } else if (forceCurrentScope && memory.lastShownProductIds != null) {
            sourceIds = memory.lastShownProductIds;
        } else {
            sourceIds = (memory.lastShownProductIds != null && memory.lastShownProductIds.size() >= 2)
                ? memory.lastShownProductIds
                : memory.lastAttributeSourceIds;
        }
        if (sourceIds == null || sourceIds.size() < 2) {
            if (forceCurrentScope) {
                return new AgentReply("\u5f53\u524d\u53ef\u6bd4\u8f83\u7684\u5546\u54c1\u53ea\u6709\u4e00\u4e2a\u3002\u4f60\u53ef\u4ee5\u8bf4\uff1a\u6362\u4e00\u4e2a / \u591a\u7ed9\u4e24\u4e2a\u5019\u9009\u518d\u6bd4\u3002", List.of());
            }
            return null;
        }
        List<ProductVO> items = new ArrayList<>();
        for (Long id : sourceIds) {
            ProductVO p = productCache.get(id);
            if (p != null) {
                items.add(p);
            }
        }
        if (items.size() < 2) {
            return null;
        }
        String key;
        if (salesCompareTone || containsAnyTerm(lowered, HOT_TERMS)) {
            key = "sales";
            items.sort(Comparator.comparing((ProductVO p) -> p.getSales() == null ? 0 : p.getSales()).reversed());
        } else if (stockCompareTone) {
            key = "stock";
            items.sort(Comparator.comparing((ProductVO p) -> p.getStock() == null ? 0 : p.getStock()).reversed());
        } else if (stockLowCompareTone) {
            key = "stock";
            items.sort(Comparator.comparing((ProductVO p) -> p.getStock() == null ? Integer.MAX_VALUE : p.getStock()));
        } else if (priceCompareTone) {
            key = "price";
            items.sort(Comparator.comparing((ProductVO p) -> p.getPrice() == null ? BigDecimal.valueOf(Double.MAX_VALUE) : p.getPrice()));
        } else {
            key = "rating";
            items.sort(Comparator.comparing((ProductVO p) -> p.getRating() == null ? BigDecimal.ZERO : p.getRating()).reversed());
        }
        ProductVO best = items.get(0);
        String bestLabel = "sales".equals(key)
            ? "\u9500\u91cf\u6700\u9ad8"
            : ("price".equals(key)
            ? "\u4ef7\u683c\u6700\u4f4e"
            : ("stock".equals(key)
            ? (stockLowCompareTone ? "\u5e93\u5b58\u6700\u4f4e" : "\u5e93\u5b58\u6700\u9ad8")
            : "\u8bc4\u5206\u6700\u9ad8"));
        List<ProductVO> top = items.stream().limit(4).toList();
        int bestGroupSize = 0;
        for (ProductVO p : top) {
            if (sameMetricValue(p, best, key)) {
                bestGroupSize++;
            }
        }
        if (bestGroupSize <= 0) {
            bestGroupSize = 1;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\u7ed3\u8bba\uff08\u6700\u4f18\uff09\uff1a").append(bestLabel).append("\n");
        for (int i = 0; i < bestGroupSize && i < top.size(); i++) {
            ProductVO p = top.get(i);
            sb.append(i + 1).append(". ")
                .append(nvl(p.getName()))
                .append(" (ID: ").append(p.getId())
                .append("\uff0c").append(formatAttributeValue(p, key))
                .append(")");
            if (i < bestGroupSize - 1) {
                sb.append("\n");
            }
        }
        if (bestGroupSize < top.size()) {
            sb.append("\n\u5907\u9009\uff08\u540c\u6279\u5019\u9009\uff09\uff1a");
            for (int i = bestGroupSize; i < top.size(); i++) {
                ProductVO p = top.get(i);
                sb.append("\n").append(i + 1).append(". ")
                    .append(nvl(p.getName()))
                    .append(" (ID: ").append(p.getId())
                    .append("\uff0c").append(formatAttributeValue(p, key)).append(")");
            }
        }
        if (bestGroupSize > 1) {
            sb.append("\n\u8bf4\u660e\uff1a\u5b58\u5728\u5e76\u5217\u7b2c\u4e00\uff0c\u6211\u5df2\u6309\u7efc\u5408\u76f8\u5173\u6027\u5c55\u793a\u987a\u5e8f\u3002");
        }
        memory.lastFocusedProductId = best.getId();
        memory.lastAttributeKey = key;
        memory.lastAttributeSourceIds = items.stream().map(ProductVO::getId).filter(Objects::nonNull).toList();
        return new AgentReply(sb.toString(), top);
    }

    private AgentReply handleWhyNotChoiceFromLastShown(String prompt, ConversationMemory memory) {
        if (prompt == null || prompt.isBlank() || memory == null) {
            return null;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        boolean whyTone = containsAny(lowered, "\u4e3a\u4ec0\u4e48", "\u600e\u4e48\u4e0d", "\u4e3a\u5565\u4e0d", "why not", "why don't");
        if (!whyTone) {
            return null;
        }
        boolean askWhyChosen = containsAny(
            lowered,
            "\u4e3a\u4ec0\u4e48\u9009", "\u4e3a\u4ec0\u4e48\u662f\u8fd9\u4e2a", "\u4e3a\u4ec0\u4e48\u9009\u8fd9\u4e2a", "\u4e3a\u4ec0\u4e48\u6362\u6210\u8fd9\u4e2a", "\u4e3a\u4ec0\u4e48\u6362\u6210\u5b83",
            "why this", "why choose", "why switch", "why changed to this"
        );
        if (askWhyChosen && memory.lastFocusedProductId != null) {
            ProductVO chosen = productCache.get(memory.lastFocusedProductId);
            ProductVO second = null;
            if (memory.lastAttributeSourceIds != null && memory.lastAttributeSourceIds.size() >= 2) {
                Long sid = memory.lastAttributeSourceIds.get(1);
                second = sid == null ? null : productCache.get(sid);
            }
            if (chosen != null) {
                String content = "\u6211\u9009\u300c" + nvl(chosen.getName()) + "\u300d\u7684\u7406\u7531\uff1a" + buildAgentChoiceReason(chosen, second);
                List<ProductVO> cards = new ArrayList<>();
                cards.add(chosen);
                if (second != null && second.getId() != null && !Objects.equals(second.getId(), chosen.getId())) {
                    cards.add(second);
                }
                memory.lastShownProductIds = cards.stream().map(ProductVO::getId).filter(Objects::nonNull).toList();
                memory.lastAttributeSourceIds = memory.lastShownProductIds;
                return new AgentReply(content, cards);
            }
        }

        List<ProductVO> shown = new ArrayList<>();
        if (memory.lastAttributeSourceIds != null && !memory.lastAttributeSourceIds.isEmpty()) {
            for (Long id : memory.lastAttributeSourceIds) {
                ProductVO p = id == null ? null : productCache.get(id);
                if (p != null) {
                    shown.add(p);
                }
                if (shown.size() >= 4) {
                    break;
                }
            }
        }
        if (shown.isEmpty()) {
            shown = lastShownProducts(memory);
        }
        if (shown.size() < 2) {
            return null;
        }
        int idx = extractOrdinalIndex(lowered);
        if (idx <= 0) {
            if (containsAny(lowered, "\u53e6\u4e00\u4e2a", "\u7b2c\u4e8c\u4e2a", "\u7b2c\u4e8c\u6b3e", "the second", "second one")) {
                idx = 2;
            } else if (containsAny(lowered, "\u7b2c\u4e00\u4e2a", "\u7b2c\u4e00\u6b3e", "the first", "first one")) {
                idx = 1;
            }
        }
        if (idx <= 0 || idx > shown.size()) {
            idx = 2;
        }
        ProductVO selected = shown.get(0);
        ProductVO asked = shown.get(idx - 1);
        String reason = "\u6211\u4f18\u5148\u7ed9\u7684\u662f\u300c" + nvl(selected.getName()) + "\u300d\uff0c"
            + "\u56e0\u4e3a" + buildSelectionReason(selected, asked, memory)
            + "\n\u4f60\u63d0\u5230\u7684\u300c" + nvl(asked.getName()) + "\u300d\u4e5f\u53ef\u4ee5\uff0c\u6211\u53ef\u4ee5\u76f4\u63a5\u6362\u6210\u5b83\u52a0\u5165\u8d2d\u7269\u8f66\u3002"
            + "\n\u53ef\u4ee5\u76f4\u63a5\u8bf4\uff1a\u628a\u7b2c" + idx + "\u4e2a\u52a0\u5165\u8d2d\u7269\u8f66\u3002";
        memory.lastFocusedProductId = asked.getId();
        List<ProductVO> cards = new ArrayList<>();
        cards.add(selected);
        if (asked.getId() != null && !Objects.equals(asked.getId(), selected.getId())) {
            cards.add(asked);
        }
        memory.lastShownProductIds = cards.stream().map(ProductVO::getId).filter(Objects::nonNull).toList();
        memory.lastAttributeSourceIds = memory.lastShownProductIds;
        return new AgentReply(reason, cards);
    }

    private AgentReply handleAgentChooseIntent(String prompt, Long userId, ConversationMemory memory) {
        if (prompt == null || prompt.isBlank() || memory == null) {
            return null;
        }
        if (isExplicitIntentSwitchPrompt(prompt, memory, detectIntent(prompt))) {
            return null;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        boolean chooseTone = containsAny(
            lowered,
            "\u5e2e\u6211\u9009\u4e00\u4e2a", "\u4f60\u5e2e\u6211\u9009\u4e00\u4e2a", "\u4f60\u5e2e\u6211\u9009", "\u5e2e\u6211\u6311\u4e00\u4e2a",
            "\u4f60\u6765\u9009", "\u4f60\u6765\u5b9a", "\u4f60\u66ff\u6211\u9009", "\u7ed9\u6211\u9009\u4e00\u4e2a",
            "\u4f60\u63a8\u8350\u6211\u4e70\u54ea\u4e2a", "\u4f60\u63a8\u8350\u4e70\u54ea\u4e2a", "\u63a8\u8350\u6211\u4e70\u54ea\u4e2a",
            "\u8fd9\u51e0\u4e2a\u91cc\u4f60\u63a8\u8350\u54ea\u4e2a", "\u8fd9\u4e9b\u91cc\u4f60\u63a8\u8350\u54ea\u4e2a",
            "\u8fd9\u51e0\u4e2a\u91cc\u54ea\u4e2a\u66f4\u9002\u5408\u6211", "\u9009\u54ea\u4e2a", "\u4e70\u54ea\u4e2a",
            "you choose", "pick one for me", "help me choose", "choose one for me"
        );
        if (!chooseTone) {
            return null;
        }

        List<ProductVO> scope = lastShownProducts(memory);
        if (scope.isEmpty() && memory.lastAttributeSourceIds != null && !memory.lastAttributeSourceIds.isEmpty()) {
            scope = new ArrayList<>();
            for (Long id : memory.lastAttributeSourceIds) {
                ProductVO p = productCache.get(id);
                if (p != null) {
                    scope.add(p);
                }
                if (scope.size() >= 6) {
                    break;
                }
            }
        }
        if (scope.isEmpty()) {
            return new AgentReply("\u53ef\u4ee5\u3002\u4f60\u5148\u7ed9\u6211\u4e00\u6279\u5019\u9009\uff0c\u6bd4\u5982\u8bf4\uff1a\u63a8\u8350\u8033\u673a\uff0c\u6211\u5c31\u80fd\u76f4\u63a5\u5e2e\u4f60\u9009\u4e00\u4e2a\u6700\u5408\u9002\u7684\u3002", List.of());
        }

        List<ProductVO> ranked = rankForAgentChoice(scope);
        ProductVO best = ranked.get(0);
        ProductVO second = ranked.size() > 1 ? ranked.get(1) : null;
        memory.lastFocusedProductId = best.getId();
        memory.lastAttributeSourceIds = ranked.stream().map(ProductVO::getId).filter(Objects::nonNull).toList();
        memory.lastShownProductIds = best.getId() == null ? List.of() : List.of(best.getId());

        String reason = buildAgentChoiceReason(best, second);
        boolean addDirectly = isAddToCartIntent(lowered) || containsAny(lowered, "\u76f4\u63a5\u52a0\u5165", "\u52a0\u8d2d", "add to cart");
        if (!addDirectly) {
            String content = "\u6211\u5e2e\u4f60\u9009\u597d\u4e86\uff1a\u300c" + nvl(best.getName()) + "\u300d\u3002"
                + "\n\u9009\u62e9\u7406\u7531\uff1a" + reason
                + "\n\u5982\u679c\u4f60\u540c\u610f\uff0c\u53ef\u4ee5\u8bf4\uff1a\u628a\u8fd9\u4e2a\u52a0\u5165\u8d2d\u7269\u8f66\u3002";
            return new AgentReply(content, List.of(best));
        }

        if (userId == null) {
            return new AgentReply("\u6211\u5e2e\u4f60\u9009\u7684\u662f\u300c" + nvl(best.getName()) + "\u300d\uff0c\u9009\u62e9\u7406\u7531\uff1a" + reason + "\n\u4f46\u4f60\u8fd8\u6ca1\u767b\u5f55\uff0c\u767b\u5f55\u540e\u6211\u5c31\u80fd\u5e2e\u4f60\u76f4\u63a5\u52a0\u5165\u8d2d\u7269\u8f66\u3002", List.of(best));
        }
        int qty = Math.max(1, extractQuantity(prompt));
        CartItemVO item = shopCartService.addToCart(userId, best.getId(), qty);
        int finalQty = item == null || item.getQuantity() == null ? qty : item.getQuantity();
        memory.lastCartAddProductId = best.getId();
        memory.lastCartAddQty = qty;
        String content = "\u5df2\u6309\u6211\u7684\u5efa\u8bae\u5e2e\u4f60\u52a0\u5165\u8d2d\u7269\u8f66\uff1a" + nvl(best.getName()) + " x " + qty
            + "\uff0c\u5f53\u524d\u8d2d\u7269\u8f66\u6570\u91cf\uff1a" + finalQty
            + "\n\u9009\u62e9\u7406\u7531\uff1a" + reason;
        return new AgentReply(content, List.of(best));
    }
    private AgentReply handleRejectCurrentChoice(String prompt, ConversationMemory memory) {
        if (prompt == null || prompt.isBlank() || memory == null) {
            return null;
        }
        if (isExplicitIntentSwitchPrompt(prompt, memory, detectIntent(prompt))) {
            return null;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        if (isCategoryDropWithoutReplacement(prompt, memory)) {
            IntentType droppedIntent = detectIntent(prompt);
            if (droppedIntent == IntentType.NONE) {
                droppedIntent = inferIntentFromMemory(memory);
            }
            String dropped = droppedIntent == IntentType.NONE ? "\u8fd9\u4e2a\u54c1\u7c7b" : topicHintByIntent(droppedIntent).split(" ")[0];
            memory.clearContext();
            return new AgentReply(
                "\u660e\u767d\u4e86\uff0c\u4e0d\u770b" + dropped + "\u4e86\u3002\u4f60\u73b0\u5728\u60f3\u770b\u4ec0\u4e48\uff1f\u53ef\u4ee5\u76f4\u63a5\u8bf4\uff1a\u63a8\u8350\u8033\u673a / \u63a8\u8350\u9f20\u6807 / \u63a8\u8350\u706f\u3002",
                List.of()
            );
        }
        boolean rejectTone = containsAny(
            lowered,
            "\u4e0d\u60f3\u8981", "\u4e0d\u8981\u8fd9\u4e2a", "\u8fd9\u4e2a\u4e0d\u8981", "\u4e0d\u8981\u4e86", "\u6362\u4e00\u4e2a", "\u6362\u4e2a\u522b\u7684",
            "\u6765\u4e2a\u522b\u7684", "\u8fd8\u662f\u4e0d\u8981", "\u8fd9\u4e2a\u4e0d\u884c",
            "i don't want this", "not this one", "skip this", "change one", "another one"
        );
        if (!rejectTone && hasOtherScopeCue(lowered)) {
            rejectTone = true;
        }
        if (!rejectTone) {
            return null;
        }

        List<ProductVO> pool = new ArrayList<>();
        if (memory.lastAttributeSourceIds != null && !memory.lastAttributeSourceIds.isEmpty()) {
            for (Long id : memory.lastAttributeSourceIds) {
                ProductVO p = id == null ? null : productCache.get(id);
                if (p != null) {
                    pool.add(p);
                }
                if (pool.size() >= 6) {
                    break;
                }
            }
        }
        if (pool.isEmpty()) {
            pool = lastShownProducts(memory);
        }
        if (pool.isEmpty()) {
            return new AgentReply("\u53ef\u4ee5\uff0c\u6211\u80fd\u7ed9\u4f60\u6362\u4e00\u4e2a\u3002\u5148\u7ed9\u6211\u4e00\u6279\u5019\u9009\uff0c\u6bd4\u5982\u8bf4\uff1a\u63a8\u8350\u8dd1\u6b65\u978b\u3002", List.of());
        }

        ProductVO current = memory.lastFocusedProductId == null ? null : productCache.get(memory.lastFocusedProductId);
        ProductVO alt = null;
        for (ProductVO p : pool) {
            if (p == null || p.getId() == null) {
                continue;
            }
            if (current == null || !Objects.equals(p.getId(), current.getId())) {
                alt = p;
                break;
            }
        }
        if (alt == null) {
            return new AgentReply("\u8fd9\u6279\u5019\u9009\u91cc\u6682\u65f6\u6ca1\u6709\u66f4\u5408\u9002\u7684\u66ff\u4ee3\u6b3e\u4e86\u3002\u4f60\u53ef\u4ee5\u8bf4\uff1a\u6362\u4e00\u6279\u8bd5\u8bd5\u3002", pool.stream().limit(4).toList());
        }

        memory.lastFocusedProductId = alt.getId();
        memory.lastShownProductIds = List.of(alt.getId());
        if (current != null && current.getId() != null && !Objects.equals(current.getId(), alt.getId())) {
            memory.lastAttributeSourceIds = List.of(alt.getId(), current.getId());
        } else {
            memory.lastAttributeSourceIds = List.of(alt.getId());
        }
        String reason = current == null
            ? "\u5df2\u7ed9\u4f60\u6362\u6210\u53e6\u4e00\u4e2a\u9009\u9879\u3002"
            : "\u597d\u7684\uff0c\u6211\u628a\u300c" + nvl(current.getName()) + "\u300d\u6362\u6210\u4e86\u300c" + nvl(alt.getName()) + "\u300d\u3002";
        String content = reason + "\n\u5982\u679c\u4f60\u786e\u5b9a\u8fd9\u4e2a\uff0c\u53ef\u4ee5\u8bf4\uff1a\u628a\u8fd9\u4e2a\u52a0\u5165\u8d2d\u7269\u8f66\u3002";
        if (current != null && current.getId() != null && !Objects.equals(current.getId(), alt.getId())) {
            return new AgentReply(content, List.of(alt, current));
        }
        return new AgentReply(content, List.of(alt));
    }

    private boolean isCategoryDropWithoutReplacement(String prompt, ConversationMemory memory) {
        if (prompt == null || prompt.isBlank() || memory == null) {
            return false;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        boolean cancelCue = containsAny(
            lowered,
            "\u4e0d\u60f3\u8981", "\u4e0d\u8981", "\u4e0d\u770b", "\u4e0d\u4e70", "\u5148\u4e0d\u8981\u4e86", "\u73b0\u5728\u4e0d\u8981\u4e86", "\u73b0\u5728\u6211\u4e0d\u60f3\u8981\u4e86",
            "\u6211\u4e0d\u8981\u4e86", "\u5148\u7b97\u4e86", "\u7b97\u4e86", "\u53d6\u6d88\u8fd9\u4e2a", "\u5148\u53d6\u6d88",
            "don't want", "not want", "don't need", "no longer want", "cancel this", "never mind"
        );
        if (!cancelCue) {
            return false;
        }
        if (containsAny(lowered, "\u4e0d\u8981\u592a", "\u4e0d\u8981\u90a3\u4e48", "\u4e0d\u8981\u8fd9\u4e48", "not too")) {
            return false;
        }
        IntentType rawIntent = detectIntent(prompt);
        IntentType memoryIntent = inferIntentFromMemory(memory);
        boolean onlyDroppingCurrent = rawIntent == IntentType.NONE || memoryIntent == IntentType.NONE || rawIntent == memoryIntent;
        boolean hasReplacementCue = hasReplacementIntentCue(lowered, rawIntent, memoryIntent);
        boolean hasCategoryOrCurrentRef = rawIntent != IntentType.NONE
            || containsAny(lowered, "\u8fd9\u4e2a", "\u8fd9\u7c7b", "\u8fd9\u4e00\u7c7b", "\u5f53\u524d\u8fd9\u6279", "this one", "this category");
        if (!hasCategoryOrCurrentRef
            && containsAny(lowered, "\u5148\u4e0d\u8981\u4e86", "\u73b0\u5728\u4e0d\u8981\u4e86", "\u73b0\u5728\u6211\u4e0d\u60f3\u8981\u4e86", "\u6211\u4e0d\u8981\u4e86", "\u5148\u7b97\u4e86", "\u7b97\u4e86", "\u53d6\u6d88\u8fd9\u4e2a", "\u5148\u53d6\u6d88", "cancel this", "never mind")) {
            hasCategoryOrCurrentRef = true;
        }
        if (!hasCategoryOrCurrentRef) {
            return false;
        }
        return onlyDroppingCurrent && !hasReplacementCue;
    }

    private boolean hasReplacementIntentCue(String lowered, IntentType rawIntent, IntentType memoryIntent) {
        if (lowered == null || lowered.isBlank()) {
            return false;
        }
        boolean hasVerb = containsAny(
            lowered,
            "\u6211\u60f3\u8981", "\u6211\u8981", "\u6539\u6210", "\u6362\u6210", "\u6362\u4e2a", "\u6362\u5546\u54c1", "\u6362\u4e00\u6b3e", "\u66ff\u6362\u6210", "\u63a8\u8350", "\u627e",
            "i want", "want", "switch to", "change to", "recommend", "find"
        );
        if (!hasVerb) {
            return false;
        }
        return rawIntent != IntentType.NONE && rawIntent != memoryIntent;
    }

    private AgentReply handleCategoryDropWithoutReplacement(String prompt, ConversationMemory memory) {
        if (!isCategoryDropWithoutReplacement(prompt, memory)) {
            return null;
        }
        IntentType droppedIntent = detectIntent(prompt);
        if (droppedIntent == IntentType.NONE) {
            droppedIntent = inferIntentFromMemory(memory);
        }
        String dropped = droppedIntent == IntentType.NONE ? "\u8fd9\u4e2a\u54c1\u7c7b" : topicHintByIntent(droppedIntent).split(" ")[0];
        memory.clearContext();
        return new AgentReply(
            "\u660e\u767d\u4e86\uff0c\u4e0d\u770b" + dropped + "\u4e86\u3002\u4f60\u53ef\u4ee5\u76f4\u63a5\u8bf4\u65b0\u9700\u6c42\uff0c\u6bd4\u5982\uff1a\u63a8\u8350\u8033\u673a / \u63a8\u8350\u9f20\u6807 / \u63a8\u8350\u706f\u3002",
            List.of()
        );
    }

    private AgentReply handleAccessoryFollowUpFromLastIntent(String prompt, ConversationMemory memory) {
        if (prompt == null || prompt.isBlank() || memory == null) {
            return null;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        if (isAddToCartIntent(lowered)
            || isCheckoutIntent(lowered)
            || isIncrementIntent(lowered)
            || isBatchAddIntent(lowered)
            || isRemoveCartIntent(lowered)
            || isCartQueryIntent(lowered)
            || isClearCartIntent(lowered)
            || isReplaceProductIntent(lowered)) {
            return null;
        }
        if (!containsAny(
            lowered,
            "\u914d\u4ef6", "\u9644\u4ef6", "\u8033\u585e\u5957", "\u8033\u5957", "\u8033\u57ab", "\u66ff\u6362", "\u4fdd\u62a4\u58f3", "\u6536\u7eb3\u5305",
            "accessory", "accessories", "ear tips", "foam tips", "earpad", "replacement", "case", "cover"
        )) {
            return null;
        }
        IntentType currentIntent = detectIntent(prompt);
        if (currentIntent == IntentType.NONE) {
            currentIntent = inferIntentFromMemory(memory);
        }
        if (currentIntent != IntentType.HEADPHONE) {
            return null;
        }

        LinkedHashMap<Long, ProductVO> pool = new LinkedHashMap<>();
        if (memory.lastAttributeSourceIds != null) {
            for (Long id : memory.lastAttributeSourceIds) {
                ProductVO p = id == null ? null : productCache.get(id);
                if (p != null && p.getId() != null && isHeadphoneAccessoryOnlyProduct(p)) {
                    pool.putIfAbsent(p.getId(), p);
                }
            }
        }
        for (ProductVO p : strictIntentRetrieve(IntentType.HEADPHONE, Set.of(), topK * 12)) {
            if (p != null && p.getId() != null && isHeadphoneAccessoryOnlyProduct(p)) {
                pool.putIfAbsent(p.getId(), p);
            }
            if (pool.size() >= topK * 3) {
                break;
            }
        }
        if (pool.isEmpty()) {
            return new AgentReply(
                "\u6211\u7406\u89e3\u4f60\u60f3\u770b\u8033\u673a\u914d\u4ef6\uff0c\u4f46\u8fd9\u6279\u6682\u65f6\u6ca1\u627e\u5230\u66f4\u5408\u9002\u7684\u3002\u4f60\u53ef\u4ee5\u8bf4\uff1a\u8033\u585e\u5957 / \u8033\u673a\u5305 / \u66ff\u6362\u8033\u57ab\u3002",
                List.of()
            );
        }
        List<ProductVO> ranked = new ArrayList<>(pool.values());
        ranked.sort((a, b) -> Integer.compare(accessoryPromptScore(lowered, b), accessoryPromptScore(lowered, a)));
        List<ProductVO> cards = ranked.stream().limit(topK).toList();
        ProductVO first = cards.get(0);
        memory.lastShownProductIds = cards.stream().map(ProductVO::getId).filter(Objects::nonNull).toList();
        memory.lastAttributeSourceIds = memory.lastShownProductIds;
        memory.lastFocusedProductId = first.getId();
        memory.lastIntent = IntentType.HEADPHONE;
        return new AgentReply(
            "\u4f60\u8981\u7684\u662f\u8033\u673a\u914d\u4ef6\uff0c\u6211\u5148\u7ed9\u4f60\u8fd9\u51e0\u6b3e\uff1a",
            cards
        );
    }

    private int accessoryPromptScore(String loweredPrompt, ProductVO p) {
        if (p == null) {
            return Integer.MIN_VALUE;
        }
        String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getDescription())).toLowerCase(Locale.ROOT);
        int score = 0;
        if (containsAnyTerm(text, HEADPHONE_ACCESSORY_ONLY_TERMS)) {
            score += 8;
        }
        if (containsAnyTerm(text, HEADPHONE_ACCESSORY_TERMS)) {
            score += 6;
        }
        if (containsAnyTerm(text, HEADPHONE_STRICT_TERMS)) {
            score += 2;
        }
        if (containsAny(loweredPrompt, "\u8033\u585e", "ear tips", "tips") && containsAny(text, "\u8033\u585e", "tips")) {
            score += 5;
        }
        if (containsAny(loweredPrompt, "\u8033\u57ab", "earpad") && containsAny(text, "\u8033\u57ab", "earpad")) {
            score += 5;
        }
        if (containsAny(loweredPrompt, "\u4fdd\u62a4\u58f3", "\u5305", "case", "cover") && containsAny(text, "\u4fdd\u62a4\u58f3", "\u5305", "case", "cover")) {
            score += 4;
        }
        if (p.getSales() != null) {
            score += Math.min(3, p.getSales() / 5000);
        }
        if (p.getRating() != null && p.getRating().compareTo(BigDecimal.valueOf(4.2)) >= 0) {
            score += 1;
        }
        return score;
    }

    private ProductVO pickBestByRating(List<ProductVO> items) {
        return items.stream()
            .max(
                Comparator
                    .comparing((ProductVO p) -> p.getRating() == null ? BigDecimal.ZERO : p.getRating())
                    .thenComparing(p -> p.getSales() == null ? 0 : p.getSales())
                    .thenComparing(p -> p.getPrice() == null ? BigDecimal.ZERO : p.getPrice(), Comparator.reverseOrder())
            )
            .orElse(items.get(0));
    }

    private AgentReply handleConfirmationFromLastShown(String prompt, ConversationMemory memory) {
        if (prompt == null || prompt.isBlank() || memory == null) {
            return null;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        boolean confirmTone = containsAny(lowered, "\u662f\u5427", "\u5bf9\u5427", "\u662f\u4e0d\u662f", "\u770b\u6765", "\u5bf9\u4e0d\u5bf9", "right?", "is it");
        boolean askCheapest = containsAny(lowered, "\u6700\u4fbf\u5b9c", "\u5df2\u7ecf\u6700\u4fbf\u5b9c", "cheapest", "lowest price");
        if (!(confirmTone && askCheapest)) {
            return null;
        }

        List<ProductVO> shown = lastShownProducts(memory);
        if (shown.isEmpty()) {
            return null;
        }
        ProductVO cheapest = shown.stream()
            .filter(p -> p != null && p.getPrice() != null)
            .min(Comparator.comparing(ProductVO::getPrice))
            .orElse(shown.get(0));
        if (cheapest == null) {
            return null;
        }

        String reply = "\u6309\u4f60\u521a\u624d\u8fd9\u6279\u5019\u9009\u6765\u770b\uff0c\u76ee\u524d\u6700\u4fbf\u5b9c\u7684\u662f\uff1a"
            + nvl(cheapest.getName()) + " (ID: " + cheapest.getId() + ", \u4ef7\u683c: " + cheapest.getPrice() + " " + nvl(cheapest.getCurrency()) + ")\u3002"
            + "\n\u5982\u679c\u4f60\u8981\u627e\u66f4\u4fbf\u5b9c\u7684\u540c\u7c7b\uff0c\u53ef\u4ee5\u8bf4\uff1a\u8fd8\u6709\u66f4\u4fbf\u5b9c\u7684\u5417\uff1f";
        memory.lastFocusedProductId = cheapest.getId();
        return new AgentReply(reply, List.of(cheapest));
    }

    private AgentReply handleProductIntroFromLastShown(String prompt, ConversationMemory memory) {
        if (prompt == null || prompt.isBlank() || memory == null) {
            return null;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        int index = extractOrdinalIndex(lowered);
        boolean ordinalDetailAsk = index > 0 && containsAny(
            lowered,
            "\u600e\u4e48\u6837", "\u600e\u4e48", "\u4ecb\u7ecd", "\u8be6\u60c5", "\u53c2\u6570", "\u914d\u7f6e", "\u8bf4\u8bf4",
            "how about", "details", "detail", "introduce"
        );
        boolean askIntro = containsAny(
            lowered,
            "\u4ecb\u7ecd", "\u8be6\u60c5", "\u53c2\u6570", "\u914d\u7f6e", "\u8bf4\u8bf4",
            "\u8fd9\u4e2a\u5546\u54c1", "\u8fd9\u6b3e\u5546\u54c1", "\u8fd9\u4e2a\u600e\u4e48\u6837", "\u8fd9\u6b3e\u600e\u4e48\u6837",
            "tell me more", "detail", "details", "introduce", "spec"
        );
        if (!askIntro && !ordinalDetailAsk) {
            return null;
        }

        List<ProductVO> shown = lastShownProducts(memory);
        if (shown.isEmpty() && memory.lastAttributeSourceIds != null && !memory.lastAttributeSourceIds.isEmpty()) {
            shown = new ArrayList<>();
            for (Long id : memory.lastAttributeSourceIds) {
                ProductVO p = productCache.get(id);
                if (p != null) {
                    shown.add(p);
                }
                if (shown.size() >= 4) {
                    break;
                }
            }
        }
        if (shown.isEmpty()) {
            return new AgentReply(
                "\u6211\u73b0\u5728\u8fd8\u6ca1\u6709\u4f60\u4e0a\u4e00\u8f6e\u7684\u5019\u9009\u5546\u54c1\u3002\u53ef\u4ee5\u5148\u8bf4\uff1a\u63a8\u8350\u8033\u673a\uff0c\u7136\u540e\u518d\u8bf4\u201c\u4ecb\u7ecd\u7b2c\u4e00\u4e2a\u201d\u3002",
                List.of()
            );
        }

        ProductVO target;
        if (index >= 1 && index <= shown.size()) {
            target = shown.get(index - 1);
        } else {
            target = shown.get(0);
        }
        if (target == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\u7ed9\u4f60\u4ecb\u7ecd\u4e00\u4e0b\uff1a").append(nvl(target.getName()));
        sb.append("\nID: ").append(nvl(String.valueOf(target.getId())));
        sb.append("\n\u7c7b\u76ee: ").append(nvl(target.getCategory()));
        sb.append("\n\u54c1\u724c: ").append(nvl(target.getBrand()));
        sb.append("\n\u4ef7\u683c: ").append(nvl(String.valueOf(target.getPrice()))).append(" ").append(nvl(target.getCurrency()));
        sb.append("\n\u8bc4\u5206: ").append(nvl(String.valueOf(target.getRating()))).append("  \u9500\u91cf: ").append(nvl(String.valueOf(target.getSales())));
        sb.append("\n\u5e93\u5b58: ").append(nvl(String.valueOf(target.getStock())));
        if (target.getDescription() != null && !target.getDescription().isBlank()) {
            String desc = target.getDescription().trim();
            if (desc.length() > 220) {
                desc = desc.substring(0, 220) + "...";
            }
            sb.append("\n\u4eae\u70b9: ").append(desc);
        }
        sb.append("\n\n\u4f60\u53ef\u4ee5\u7ee7\u7eed\u8bf4\uff1a\u201c\u52a0\u5165\u8d2d\u7269\u8f66 \u5546\u54c1ID ")
            .append(nvl(String.valueOf(target.getId())))
            .append(" \u6570\u91cf 1\u201d\u3002");
        memory.lastFocusedProductId = target.getId();
        memory.lastAttributeSourceIds = shown.stream().map(ProductVO::getId).filter(Objects::nonNull).toList();
        return new AgentReply(sb.toString(), List.of(target));
    }

    private AgentReply handleAttributeQuestionFromLastShown(String prompt, ConversationMemory memory) {
        if (prompt == null || prompt.isBlank() || memory == null) {
            return null;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        boolean asksAttribute = containsAny(
            lowered,
            "\u9500\u91cf", "\u6708\u9500", "\u5356\u4e86\u591a\u5c11", "\u70ed\u5ea6", "\u706b\u7206", "\u70ed\u95e8", "\u7206\u6b3e",
            "\u4ef7\u683c", "\u591a\u5c11\u94b1", "\u591a\u5c11\u7c73", "\u8d35\u4e0d\u8d35",
            "\u8bc4\u5206", "\u53e3\u7891", "\u8bc4\u4ef7\u600e\u4e48\u6837",
            "\u5e93\u5b58", "\u6709\u8d27\u5417", "\u6709\u73b0\u8d27\u5417",
            "\u54c1\u724c", "\u7c7b\u76ee", "\u54ea\u4e2a\u724c\u5b50",
            "sales", "sold", "popular", "hot", "best seller", "rating", "price", "stock", "inventory", "brand", "category"
        );
        boolean referenceTone = containsAny(
            lowered,
            "\u8fd9\u4e2a", "\u8fd9\u6b3e", "\u8fd9\u4ef6", "\u8fd9\u53f0", "\u8fd9\u53cc", "\u5b83", "this one", "it"
        ) || extractOrdinalIndex(lowered) > 0;
        if (!asksAttribute) {
            return null;
        }

        List<ProductVO> shown = lastShownProducts(memory);
        if (shown.isEmpty()) {
            return new AgentReply("\u6211\u8fd8\u6ca1\u6709\u53ef\u53c2\u8003\u7684\u4e0a\u4e00\u6279\u5546\u54c1\u3002\u53ef\u4ee5\u5148\u8bf4\uff1a\u63a8\u8350\u978b\u5b50\u3002", List.of());
        }

        boolean askCompare = containsAny(lowered, "\u54ea\u4e2a", "\u54ea\u4e00\u4e2a", "\u66f4\u9ad8", "\u6700\u9ad8", "\u66f4\u4f4e", "\u6700\u4f4e", "\u66f4\u4fbf\u5b9c", "\u6700\u4fbf\u5b9c");
        boolean askSalesForCompare = containsAny(lowered, "\u9500\u91cf", "\u6708\u9500", "\u5356\u4e86\u591a\u5c11", "\u70ed\u5ea6", "\u706b\u7206", "\u70ed\u95e8", "\u7206\u6b3e", "sales", "sold", "popular", "hot");
        boolean askPriceForCompare = containsAny(
            lowered,
            "\u4ef7\u683c", "\u591a\u5c11\u94b1", "\u591a\u5c11\u7c73", "\u8d35\u4e0d\u8d35", "\u66f4\u4fbf\u5b9c", "\u4fbf\u5b9c\u4e00\u70b9", "\u54ea\u4e2a\u4fbf\u5b9c",
            "price", "cheaper"
        );
        boolean askRatingForCompare = containsAny(lowered, "\u8bc4\u5206", "\u53e3\u7891", "\u8bc4\u4ef7\u600e\u4e48\u6837", "rating");
        boolean askStockForCompare = containsAny(lowered, "\u5e93\u5b58", "stock", "inventory");
        boolean askStockLowForCompare = askStockForCompare && containsAny(lowered, "\u6700\u5c11", "\u66f4\u5c11", "\u6700\u4f4e", "least", "lowest");
        if (askCompare && shown.size() >= 2 && (askSalesForCompare || askRatingForCompare || askPriceForCompare || askStockForCompare)) {
            String key = askSalesForCompare ? "sales" : (askRatingForCompare ? "rating" : (askPriceForCompare ? "price" : "stock"));
            List<ProductVO> ranked = new ArrayList<>(shown);
            if ("sales".equals(key)) {
                ranked.sort(Comparator.comparing((ProductVO p) -> p.getSales() == null ? 0 : p.getSales()).reversed());
            } else if ("rating".equals(key)) {
                ranked.sort(Comparator.comparing((ProductVO p) -> p.getRating() == null ? BigDecimal.ZERO : p.getRating()).reversed());
            } else if ("stock".equals(key)) {
                if (askStockLowForCompare) {
                    ranked.sort(Comparator.comparing((ProductVO p) -> p.getStock() == null ? Integer.MAX_VALUE : p.getStock()));
                } else {
                    ranked.sort(Comparator.comparing((ProductVO p) -> p.getStock() == null ? 0 : p.getStock()).reversed());
                }
            } else {
                ranked.sort(Comparator.comparing((ProductVO p) -> p.getPrice() == null ? BigDecimal.valueOf(Double.MAX_VALUE) : p.getPrice()));
            }
            ProductVO top = ranked.get(0);
            memory.lastFocusedProductId = top.getId();
            memory.lastAttributeKey = key;
            memory.lastAttributeSourceIds = ranked.stream().map(ProductVO::getId).filter(Objects::nonNull).toList();
            List<ProductVO> shownTop = ranked.stream().limit(4).toList();
            String bestLabel = "sales".equals(key)
                ? "\u9500\u91cf\u6700\u9ad8"
                : ("rating".equals(key)
                ? "\u8bc4\u5206\u6700\u9ad8"
                : ("stock".equals(key) ? (askStockLowForCompare ? "\u5e93\u5b58\u6700\u4f4e" : "\u5e93\u5b58\u6700\u9ad8") : "\u4ef7\u683c\u6700\u4f4e"));
            int bestGroupSize = 0;
            for (ProductVO p : shownTop) {
                if (sameMetricValue(p, top, key)) {
                    bestGroupSize++;
                }
            }
            if (bestGroupSize <= 0) {
                bestGroupSize = 1;
            }
            StringBuilder reply = new StringBuilder();
            reply.append("\u7ed3\u8bba\uff08\u6700\u4f18\uff09\uff1a").append(bestLabel).append("\n");
            for (int i = 0; i < bestGroupSize && i < shownTop.size(); i++) {
                ProductVO p = shownTop.get(i);
                reply.append(i + 1).append(". ")
                    .append(nvl(p.getName()))
                    .append(" (ID: ").append(p.getId()).append("\uff0c").append(formatAttributeValue(p, key)).append(")");
                if (i < bestGroupSize - 1) {
                    reply.append("\n");
                }
            }
            if (bestGroupSize < shownTop.size()) {
                reply.append("\n\u5907\u9009\uff08\u540c\u6279\u5019\u9009\uff09\uff1a");
                for (int i = bestGroupSize; i < shownTop.size(); i++) {
                    ProductVO p = shownTop.get(i);
                    reply.append("\n").append(i + 1).append(". ")
                        .append(nvl(p.getName()))
                        .append(" (ID: ").append(p.getId())
                        .append("\uff0c").append(formatAttributeValue(p, key)).append(")");
                }
            }
            if (bestGroupSize > 1) {
                reply.append("\n\u8bf4\u660e\uff1a\u5b58\u5728\u5e76\u5217\u7b2c\u4e00\uff0c\u6211\u5df2\u6309\u7efc\u5408\u76f8\u5173\u6027\u5c55\u793a\u987a\u5e8f\u3002");
            }
            reply.append("\n\u4f60\u53ef\u4ee5\u7ee7\u7eed\u8bf4\uff1a\u5176\u4ed6\u4e24\u4e2a\u5462\uff1f");
            return new AgentReply(reply.toString(), shownTop);
        }

        ProductVO target;
        int index = extractOrdinalIndex(lowered);
        if (index >= 1 && index <= shown.size()) {
            target = shown.get(index - 1);
        } else if (!referenceTone && memory.lastFocusedProductId != null) {
            target = productCache.get(memory.lastFocusedProductId);
            if (target == null) {
                target = shown.get(0);
            }
        } else {
            target = shown.get(0);
        }
        if (target == null) {
            return null;
        }

        boolean askSales = containsAny(lowered, "\u9500\u91cf", "\u6708\u9500", "\u5356\u4e86\u591a\u5c11", "\u70ed\u5ea6", "\u706b\u7206", "\u70ed\u95e8", "\u7206\u6b3e", "sales", "sold", "popular", "hot");
        boolean askPrice = containsAny(lowered, "\u4ef7\u683c", "\u591a\u5c11\u94b1", "\u591a\u5c11\u7c73", "\u8d35\u4e0d\u8d35", "price");
        boolean askRating = containsAny(lowered, "\u8bc4\u5206", "\u53e3\u7891", "\u8bc4\u4ef7\u600e\u4e48\u6837", "rating");
        boolean askStock = containsAny(lowered, "\u5e93\u5b58", "\u6709\u8d27\u5417", "\u6709\u73b0\u8d27\u5417", "stock", "inventory");
        boolean askBrand = containsAny(lowered, "\u54c1\u724c", "\u54ea\u4e2a\u724c\u5b50", "brand");
        boolean askCategory = containsAny(lowered, "\u7c7b\u76ee", "\u4ec0\u4e48\u7c7b", "category");

        List<String> details = new ArrayList<>();
        if (askSales) {
            Integer sales = target.getSales();
            if (sales == null) {
                details.add("\u9500\u91cf\u6570\u636e\u6682\u65f6\u53d6\u4e0d\u5230");
            } else if (sales <= 0) {
                details.add("\u76ee\u524d\u9500\u91cf\u8fd8\u6bd4\u8f83\u4f4e");
            } else {
                details.add("\u9500\u91cf\u5927\u7ea6 " + sales);
            }
        }
        if (askPrice) {
            details.add("\u5230\u624b\u4ef7\u5927\u7ea6 " + nvl(String.valueOf(target.getPrice())) + " " + nvl(target.getCurrency()));
        }
        if (askRating) {
            BigDecimal r = target.getRating();
            if (r == null) {
                details.add("\u8bc4\u5206\u4fe1\u606f\u6682\u65f6\u53d6\u4e0d\u5230");
            } else if (r.compareTo(new BigDecimal("4.5")) >= 0) {
                details.add("\u8bc4\u5206\u5f88\u4e0d\u9519\uff0c\u5f53\u524d " + r);
            } else {
                details.add("\u8bc4\u5206\u5927\u7ea6 " + r);
            }
        }
        if (askStock) {
            Integer s = target.getStock();
            if (s == null) {
                details.add("\u5e93\u5b58\u6570\u636e\u6682\u65f6\u53d6\u4e0d\u5230");
            } else if (s <= 0) {
                details.add("\u8fd9\u6b3e\u76ee\u524d\u6682\u65f6\u65e0\u8d27");
            } else if (s <= 5) {
                details.add("\u8fd9\u6b3e\u8fd8\u6709\u5c11\u91cf\u73b0\u8d27\uff0c\u7ea6 " + s + " \u4ef6");
            } else {
                details.add("\u6709\u8d27\uff0c\u5f53\u524d\u5e93\u5b58\u7ea6 " + s + " \u4ef6");
            }
        }
        if (askBrand) {
            details.add("\u5b83\u7684\u54c1\u724c\u662f " + nvl(target.getBrand()));
        }
        if (askCategory) {
            details.add("\u8fd9\u6b3e\u5c5e\u4e8e " + nvl(target.getCategory()));
        }
        if (details.isEmpty()) {
            return null;
        }

        String reply;
        if (askStock && details.size() == 1) {
            reply = "\u5173\u4e8e\u8fd9\u6b3e\u300c" + nvl(target.getName()) + "\u300d\uff0c" + details.get(0) + "\u3002";
        } else if (askRating && details.size() == 1) {
            reply = "\u8fd9\u6b3e\u300c" + nvl(target.getName()) + "\u300d\u7684\u60c5\u51b5\u662f\uff1a" + details.get(0) + "\u3002";
        } else if (askSales && details.size() == 1) {
            reply = "\u8fd9\u6b3e\u300c" + nvl(target.getName()) + "\u300d\u76ee\u524d" + details.get(0) + "\u3002";
        } else {
            reply = "\u6211\u5e2e\u4f60\u770b\u4e86\u4e00\u4e0b\u300c" + nvl(target.getName()) + "\u300d\uff1a" + String.join("\uff0c", details) + "\u3002";
        }
        memory.lastFocusedProductId = target.getId();
        memory.lastAttributeKey = inferAskedAttributeKey(askSales, askPrice, askRating, askStock, askBrand, askCategory);
        memory.lastAttributeSourceIds = shown.stream().map(ProductVO::getId).filter(Objects::nonNull).toList();
        return new AgentReply(reply, List.of(target));
    }

    private AgentReply handleOtherItemsAttributeFollowUp(String prompt, ConversationMemory memory) {
        if (prompt == null || prompt.isBlank() || memory == null) {
            return null;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        boolean askOthers = containsAny(lowered, "\u5176\u4ed6", "\u53e6\u5916", "\u5176\u4f59", "\u90a3\u53e6\u5916", "other", "others");
        if (!askOthers || memory.lastAttributeKey == null || memory.lastAttributeKey.isBlank()) {
            return null;
        }
        List<Long> sourceIds = memory.lastAttributeSourceIds == null ? List.of() : memory.lastAttributeSourceIds;
        if (sourceIds.isEmpty()) {
            sourceIds = memory.lastShownProductIds;
        }
        if (sourceIds == null || sourceIds.size() < 2) {
            return null;
        }
        int want = containsAny(lowered, "\u4e24\u4e2a", "\u4e24\u4ef6", "\u4e24\u4e2a\u5462", "two") ? 2 : 3;
        List<ProductVO> others = new ArrayList<>();
        for (Long id : sourceIds) {
            if (id == null) continue;
            if (memory.lastFocusedProductId != null && memory.lastFocusedProductId.equals(id)) {
                continue;
            }
            ProductVO p = productCache.get(id);
            if (p != null) {
                others.add(p);
            }
            if (others.size() >= want) break;
        }
        if (others.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("\u5176\u4ed6\u51e0\u4e2a\u662f\uff1a");
        for (int i = 0; i < others.size(); i++) {
            ProductVO p = others.get(i);
            sb.append("\n").append(i + 1).append(". ").append(nvl(p.getName()))
                .append(" (ID: ").append(p.getId()).append("\uff0c")
                .append(formatAttributeValue(p, memory.lastAttributeKey)).append(")");
        }
        return new AgentReply(sb.toString(), others);
    }

    private String inferAskedAttributeKey(boolean askSales, boolean askPrice, boolean askRating, boolean askStock, boolean askBrand, boolean askCategory) {
        if (askSales) return "sales";
        if (askPrice) return "price";
        if (askRating) return "rating";
        if (askStock) return "stock";
        if (askBrand) return "brand";
        if (askCategory) return "category";
        return "";
    }

    private String formatAttributeValue(ProductVO p, String key) {
        if (p == null || key == null) {
            return "";
        }
        return switch (key) {
            case "sales" -> "\u9500\u91cf " + nvl(String.valueOf(p.getSales()));
            case "price" -> "\u4ef7\u683c " + nvl(String.valueOf(p.getPrice())) + " " + nvl(p.getCurrency());
            case "rating" -> "\u8bc4\u5206 " + nvl(String.valueOf(p.getRating()));
            case "stock" -> "\u5e93\u5b58 " + nvl(String.valueOf(p.getStock()));
            case "brand" -> "\u54c1\u724c " + nvl(p.getBrand());
            case "category" -> "\u7c7b\u76ee " + nvl(p.getCategory());
            default -> "";
        };
    }

    private String buildSelectionReason(ProductVO selected, ProductVO asked, ConversationMemory memory) {
        if (selected == null) {
            return "\u5b83\u5bf9\u5f53\u524d\u9700\u6c42\u66f4\u5339\u914d\u3002";
        }
        if (asked == null || Objects.equals(selected.getId(), asked.getId())) {
            return "\u5b83\u5728\u5f53\u524d\u5019\u9009\u91cc\u7684\u7efc\u5408\u8868\u73b0\u66f4\u7a33\u5b9a\u3002";
        }

        List<String> points = new ArrayList<>();
        String key = memory == null ? "" : nvl(memory.lastAttributeKey);

        if ("price".equals(key) && isPriceBetter(selected, asked)) {
            points.add("\u4ef7\u683c\u66f4\u4f18\uff08" + nvl(String.valueOf(selected.getPrice())) + " vs " + nvl(String.valueOf(asked.getPrice())) + " " + nvl(selected.getCurrency()) + "\uff09");
        } else if ("rating".equals(key) && isRatingBetter(selected, asked)) {
            points.add("\u8bc4\u5206\u66f4\u9ad8\uff08" + nvl(String.valueOf(selected.getRating())) + " vs " + nvl(String.valueOf(asked.getRating())) + "\uff09");
        } else if ("sales".equals(key) && isSalesBetter(selected, asked)) {
            points.add("\u9500\u91cf\u66f4\u9ad8\uff08" + nvl(String.valueOf(selected.getSales())) + " vs " + nvl(String.valueOf(asked.getSales())) + "\uff09");
        } else if ("stock".equals(key) && isStockBetter(selected, asked)) {
            points.add("\u5e93\u5b58\u66f4\u5145\u8db3\uff08" + nvl(String.valueOf(selected.getStock())) + " vs " + nvl(String.valueOf(asked.getStock())) + "\uff09");
        }

        if (points.isEmpty() && isRatingBetter(selected, asked)) {
            points.add("\u8bc4\u5206\u66f4\u9ad8\uff08" + nvl(String.valueOf(selected.getRating())) + " vs " + nvl(String.valueOf(asked.getRating())) + "\uff09");
        }
        if (points.size() < 2 && isSalesBetter(selected, asked)) {
            points.add("\u9500\u91cf\u66f4\u9ad8\uff08" + nvl(String.valueOf(selected.getSales())) + " vs " + nvl(String.valueOf(asked.getSales())) + "\uff09");
        }
        if (points.size() < 2 && isPriceBetter(selected, asked)) {
            points.add("\u4ef7\u683c\u66f4\u4f18\uff08" + nvl(String.valueOf(selected.getPrice())) + " vs " + nvl(String.valueOf(asked.getPrice())) + " " + nvl(selected.getCurrency()) + "\uff09");
        }
        if (points.isEmpty()) {
            points.add("\u5728\u8bc4\u5206/\u9500\u91cf/\u4ef7\u683c\u7684\u7efc\u5408\u6743\u8861\u4e0b\u66f4\u9002\u5408\u5f53\u524d\u7b5b\u9009");
        }
        return String.join("\uff0c", points) + "\u3002";
    }

    private List<ProductVO> rankForAgentChoice(List<ProductVO> scope) {
        List<ProductVO> pool = new ArrayList<>();
        for (ProductVO p : scope) {
            if (p != null && p.getId() != null) {
                pool.add(p);
            }
        }
        if (pool.isEmpty()) {
            return List.of();
        }
        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;
        int maxSales = 0;
        int maxStock = 0;
        for (ProductVO p : pool) {
            if (p.getPrice() != null) {
                if (minPrice == null || p.getPrice().compareTo(minPrice) < 0) minPrice = p.getPrice();
                if (maxPrice == null || p.getPrice().compareTo(maxPrice) > 0) maxPrice = p.getPrice();
            }
            if (p.getSales() != null) {
                maxSales = Math.max(maxSales, Math.max(0, p.getSales()));
            }
            if (p.getStock() != null) {
                maxStock = Math.max(maxStock, Math.max(0, p.getStock()));
            }
        }
        final BigDecimal fMinPrice = minPrice;
        final BigDecimal fMaxPrice = maxPrice;
        final int fMaxSales = maxSales;
        final int fMaxStock = maxStock;

        pool.sort((a, b) -> Double.compare(choiceScore(b, fMinPrice, fMaxPrice, fMaxSales, fMaxStock), choiceScore(a, fMinPrice, fMaxPrice, fMaxSales, fMaxStock)));
        return pool;
    }

    private double choiceScore(ProductVO p, BigDecimal minPrice, BigDecimal maxPrice, int maxSales, int maxStock) {
        double rating = p.getRating() == null ? 0.0 : Math.max(0.0, Math.min(5.0, p.getRating().doubleValue())) / 5.0;
        double sales = (maxSales <= 0 || p.getSales() == null) ? 0.0 : Math.max(0.0, Math.min(1.0, (double) Math.max(0, p.getSales()) / (double) maxSales));
        double stock = (maxStock <= 0 || p.getStock() == null) ? 0.0 : Math.max(0.0, Math.min(1.0, (double) Math.max(0, p.getStock()) / (double) maxStock));
        double price = 0.5;
        if (minPrice != null && maxPrice != null && p.getPrice() != null && maxPrice.compareTo(minPrice) > 0) {
            BigDecimal range = maxPrice.subtract(minPrice);
            BigDecimal offset = p.getPrice().subtract(minPrice);
            double pos = offset.doubleValue() / range.doubleValue();
            pos = Math.max(0.0, Math.min(1.0, pos));
            price = 1.0 - pos;
        }
        return rating * 0.4 + sales * 0.3 + price * 0.2 + stock * 0.1;
    }

    private String buildAgentChoiceReason(ProductVO best, ProductVO second) {
        if (best == null) {
            return "\u8be5\u5546\u54c1\u66f4\u7b26\u5408\u5f53\u524d\u8bed\u5883\u3002";
        }
        if (second == null) {
            return "\u5728\u5f53\u524d\u5019\u9009\u91cc\u5b83\u7684\u6570\u636e\u8868\u73b0\u6700\u5747\u8861\u3002";
        }
        List<String> points = new ArrayList<>();
        if (isRatingBetter(best, second)) {
            points.add("\u8bc4\u5206\u66f4\u9ad8\uff08" + nvl(String.valueOf(best.getRating())) + " vs " + nvl(String.valueOf(second.getRating())) + "\uff09");
        }
        if (isSalesBetter(best, second)) {
            points.add("\u9500\u91cf\u66f4\u9ad8\uff08" + nvl(String.valueOf(best.getSales())) + " vs " + nvl(String.valueOf(second.getSales())) + "\uff09");
        }
        if (isPriceBetter(best, second)) {
            points.add("\u4ef7\u683c\u66f4\u4f18\uff08" + nvl(String.valueOf(best.getPrice())) + " vs " + nvl(String.valueOf(second.getPrice())) + " " + nvl(best.getCurrency()) + "\uff09");
        }
        if (isStockBetter(best, second)) {
            points.add("\u5e93\u5b58\u66f4\u5145\u8db3\uff08" + nvl(String.valueOf(best.getStock())) + " vs " + nvl(String.valueOf(second.getStock())) + "\uff09");
        }
        if (points.isEmpty()) {
            points.add("\u7efc\u5408\u8bc4\u5206/\u9500\u91cf/\u4ef7\u683c/\u5e93\u5b58\u540e\uff0c\u5b83\u7684\u603b\u4f53\u8868\u73b0\u66f4\u597d");
        }
        return String.join("\uff0c", points) + "\u3002";
    }

    private boolean isRatingBetter(ProductVO a, ProductVO b) {
        if (a.getRating() == null || b.getRating() == null) {
            return false;
        }
        return a.getRating().compareTo(b.getRating()) > 0;
    }

    private boolean isSalesBetter(ProductVO a, ProductVO b) {
        if (a.getSales() == null || b.getSales() == null) {
            return false;
        }
        return a.getSales() > b.getSales();
    }

    private boolean isStockBetter(ProductVO a, ProductVO b) {
        if (a.getStock() == null || b.getStock() == null) {
            return false;
        }
        return a.getStock() > b.getStock();
    }

    private boolean isPriceBetter(ProductVO a, ProductVO b) {
        if (a.getPrice() == null || b.getPrice() == null) {
            return false;
        }
        return a.getPrice().compareTo(b.getPrice()) < 0;
    }

    private boolean sameMetricValue(ProductVO a, ProductVO b, String key) {
        if (a == null || b == null || key == null) {
            return false;
        }
        return switch (key) {
            case "sales" -> Objects.equals(a.getSales(), b.getSales());
            case "price" -> Objects.equals(a.getPrice(), b.getPrice());
            case "rating" -> Objects.equals(a.getRating(), b.getRating());
            case "stock" -> Objects.equals(a.getStock(), b.getStock());
            case "brand" -> Objects.equals(a.getBrand(), b.getBrand());
            case "category" -> Objects.equals(a.getCategory(), b.getCategory());
            default -> false;
        };
    }

    private int extractOrdinalIndex(String lowered) {
        if (lowered == null || lowered.isBlank()) {
            return -1;
        }
        Matcher m = Pattern.compile("\u7b2c\\s*(\\d{1,2})\\s*(?:\u4e2a|\u6b3e|\u4ef6|\u6761|\u53f0|\u90e8)?").matcher(lowered);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        if (containsAny(lowered, "\u7b2c\u4e00\u4e2a", "\u7b2c1\u4e2a", "\u7b2c\u4e00\u6b3e", "\u7b2c1\u6b3e")) return 1;
        if (containsAny(lowered, "\u7b2c\u4e8c\u4e2a", "\u7b2c2\u4e2a", "\u7b2c\u4e8c\u6b3e", "\u7b2c2\u6b3e")) return 2;
        if (containsAny(lowered, "\u7b2c\u4e09\u4e2a", "\u7b2c3\u4e2a", "\u7b2c\u4e09\u6b3e", "\u7b2c3\u6b3e")) return 3;
        if (containsAny(lowered, "\u7b2c\u56db\u4e2a", "\u7b2c4\u4e2a")) return 4;
        if (containsAny(lowered, "\u7b2c\u4e94\u4e2a", "\u7b2c5\u4e2a")) return 5;
        return -1;
    }

    private AgentReply handleConversationOnly(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT).trim();
        if (matchesAny(lowered,
            s -> s.contains("\u4f60\u4f1a\u4ec0\u4e48"),
            s -> s.contains("\u80fd\u505a\u4ec0\u4e48"),
            s -> s.contains("\u4f60\u80fd\u5e72\u4ec0\u4e48"),
            s -> s.contains("\u4f60\u90fd\u80fd\u5e72\u561b"),
            s -> s.contains("\u4f60\u53ef\u4ee5\u505a\u4ec0\u4e48"),
            s -> s.contains("\u4f60\u6709\u4ec0\u4e48\u529f\u80fd"),
            s -> s.contains("\u600e\u4e48\u7528"),
            s -> s.contains("\u600e\u4e48\u73a9"),
            s -> s.contains("help"),
            s -> s.contains("how to use"),
            s -> s.contains("what can you do"))) {
            return new AgentReply("\u6211\u53ef\u4ee5\u5e2e\u4f60\u63a8\u8350\u5546\u54c1\u3001\u6309\u9884\u7b97\u7b5b\u9009\u3001\u6309\u4ef7\u683c\u6392\u5e8f\u3001\u52a0\u5165\u8d2d\u7269\u8f66\u3001\u6279\u91cf\u52a0\u8d2d\u548c\u4e0b\u5355\u3002", List.of());
        }
        if (matchesAny(lowered,
            s -> s.contains("\u8c22\u8c22"),
            s -> s.equals("thanks"),
            s -> s.equals("thank you"))) {
            return new AgentReply("\u4e0d\u5ba2\u6c14\u3002\u4f60\u53ef\u4ee5\u7ee7\u7eed\u8bf4\uff1a\u518d\u6765\u51e0\u4e2a\u3001\u8fd8\u6709\u5176\u4ed6\u7684\u5417\uff1f", List.of());
        }
        if (matchesAny(lowered,
            s -> s.contains("\u4f60\u662f\u8c01"),
            s -> s.contains("\u4f60\u662f\u4ec0\u4e48"),
            s -> s.contains("who are you"))) {
            return new AgentReply("\u6211\u662f\u4f60\u7684\u7535\u5546\u8d2d\u7269\u52a9\u624b\uff0c\u8d1f\u8d23\u627e\u5546\u54c1\u3001\u63a8\u8350\u3001\u52a0\u8d2d\u548c\u4e0b\u5355\u3002", List.of());
        }
        return null;
    }
    private String topicHintByIntent(IntentType intent) {
        return switch (intent) {
            case HEADPHONE -> "\u8033\u673a headphone";
            case SHOE -> "\u978b shoes";
            case BAG -> "\u5305 bag";
            case LIGHT -> "\u706f lamp lighting";
            case BIKE -> "\u81ea\u884c\u8f66 bicycle bike cycling";
            case OUTDOOR -> "\u767b\u5c71 \u5f92\u6b65 \u6237\u5916 hiking trekking outdoor camping";
            case COMPUTER -> "\u7535\u8111 laptop computer";
            case ELECTRONICS -> "\u7535\u5b50\u4ea7\u54c1 electronics";
            case BEDDING -> "\u88ab\u5b50 bedding comforter quilt duvet";
            case DAILY -> "\u751f\u6d3b\u7528\u54c1 household";
            default -> "";
        };
    }

    private String topicHintByShown(List<ProductVO> shown) {
        if (shown == null || shown.isEmpty()) {
            return "";
        }
        StringBuilder sample = new StringBuilder();
        int limit = Math.min(4, shown.size());
        for (int i = 0; i < limit; i++) {
            ProductVO p = shown.get(i);
            if (p == null) {
                continue;
            }
            sample.append(nvl(p.getName())).append(" ")
                .append(nvl(p.getCategory())).append(" ");
        }
        String raw = sample.toString().trim().toLowerCase(Locale.ROOT);
        if (raw.isBlank()) {
            return "";
        }
        if (containsAny(raw, "\u706f", "\u706f\u5177", "\u7167\u660e", "lamp", "light", "lighting", "sconce", "chandelier", "bulb")) {
            return "\u706f lamp lighting";
        }
        if (containsAny(raw, "\u81ea\u884c\u8f66", "\u5355\u8f66", "\u9a91\u884c", "bicycle", "bike", "cycling", "mtb", "bmx", "tricycle", "ebike", "e-bike")) {
            return "\u81ea\u884c\u8f66 bicycle bike cycling";
        }
        if (containsAnyTerm(raw, OUTDOOR_TERMS)) {
            return "\u767b\u5c71 \u5f92\u6b65 \u6237\u5916 hiking trekking outdoor";
        }
        if (isPetBowlQuery(raw)) {
            return "\u72d7\u7897 pet bowl feeder";
        }

        Map<String, Integer> freq = new LinkedHashMap<>();
        Set<String> generic = Set.of(
            "\u9002\u7528", "\u517c\u5bb9", "\u4ea7\u54c1", "\u5546\u54c1", "\u63a8\u8350", "\u54c1\u8d28",
            "for", "with", "and", "the", "kit", "set", "pack", "inch"
        );
        List<String> tokens = splitQueryTokens(normalizeQuery(raw));
        for (String t : tokens) {
            if (t == null || t.isBlank()) {
                continue;
            }
            String token = t.trim().toLowerCase(Locale.ROOT);
            if (QUERY_STOP_WORDS.contains(token) || generic.contains(token)) {
                continue;
            }
            if (token.matches("\\d+")) {
                continue;
            }
            if (token.contains(" ")) {
                continue;
            }
            if (!containsCjk(token) && token.length() < 3) {
                continue;
            }
            if (containsCjk(token) && token.length() > 8) {
                continue;
            }
            if (!containsCjk(token) && token.length() > 16) {
                continue;
            }
            freq.merge(token, 1, Integer::sum);
        }
        if (freq.isEmpty()) {
            return "";
        }
        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(freq.entrySet());
        ranked.sort((a, b) -> {
            int byCount = Integer.compare(b.getValue(), a.getValue());
            if (byCount != 0) {
                return byCount;
            }
            return Integer.compare(a.getKey().length(), b.getKey().length());
        });
        List<String> picked = new ArrayList<>();
        for (Map.Entry<String, Integer> e : ranked) {
            if (!picked.contains(e.getKey())) {
                picked.add(e.getKey());
            }
            if (picked.size() >= 4) {
                break;
            }
        }
        return String.join(" ", picked);
    }

    private List<ProductVO> filterByTopicHint(List<ProductVO> candidates, String topicHint) {
        if (candidates == null || candidates.isEmpty() || topicHint == null || topicHint.isBlank()) {
            return candidates == null ? List.of() : candidates;
        }
        List<String> tokens = splitQueryTokens(normalizeQuery(topicHint));
        List<String> effective = new ArrayList<>();
        for (String t : tokens) {
            if (t == null || t.isBlank()) {
                continue;
            }
            String token = t.toLowerCase(Locale.ROOT).trim();
            if (QUERY_STOP_WORDS.contains(token)) {
                continue;
            }
            if (!containsCjk(token) && token.length() < 3) {
                continue;
            }
            effective.add(token);
        }
        if (effective.isEmpty()) {
            return candidates;
        }
        List<ProductVO> filtered = new ArrayList<>();
        for (ProductVO p : candidates) {
            String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getBrand()) + " " + nvl(p.getDescription()))
                .toLowerCase(Locale.ROOT);
            for (String token : effective) {
                if (text.contains(token)) {
                    filtered.add(p);
                    break;
                }
            }
        }
        return filtered;
    }

    private boolean isHeadphoneLikeProduct(ProductVO p) {
        String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getDescription())).toLowerCase(Locale.ROOT);
        for (String t : HEADPHONE_TERMS) {
            if (text.contains(t.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean isStrictHeadphoneProduct(ProductVO p) {
        String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getDescription())).toLowerCase(Locale.ROOT);
        boolean hasCore = containsAnyTerm(text, HEADPHONE_STRICT_TERMS);
        if (!hasCore) {
            return false;
        }
        if (containsAnyTerm(text, HEADPHONE_EXCLUDE_TERMS)) {
            return false;
        }
        return true;
    }

    private boolean isHeadphoneAccessoryOnlyProduct(ProductVO p) {
        String name = nvl(p.getName()).toLowerCase(Locale.ROOT);
        String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getDescription())).toLowerCase(Locale.ROOT);
        boolean mentionsAccessory = containsAnyTerm(text, HEADPHONE_ACCESSORY_TERMS);
        if (!mentionsAccessory) {
            return false;
        }
        boolean nameHasAccessory = containsAnyTerm(name, HEADPHONE_ACCESSORY_TERMS)
            || containsAnyTerm(name, HEADPHONE_ACCESSORY_ONLY_TERMS);
        boolean nameHasCoreDevice = containsAnyTerm(name, HEADPHONE_STRICT_TERMS);
        boolean hasMainFeature = containsAnyTerm(text, HEADPHONE_MAIN_FEATURE_TERMS);
        // Accessory-only if title centers on accessories and lacks core device signals.
        return nameHasAccessory && !nameHasCoreDevice && !hasMainFeature;
    }

    private boolean isStrictMouseProduct(ProductVO p) {
        String text = (nvl(p.getName()) + " " + nvl(p.getDescription())).toLowerCase(Locale.ROOT);
        boolean hasCore = containsAnyTerm(text, MOUSE_STRICT_TERMS);
        if (!hasCore) {
            return false;
        }
        return !containsAnyTerm(text, MOUSE_EXCLUDE_TERMS);
    }

    private boolean isStrictKeyboardProduct(ProductVO p) {
        String text = (nvl(p.getName()) + " " + nvl(p.getDescription())).toLowerCase(Locale.ROOT);
        boolean hasCore = containsAnyTerm(text, KEYBOARD_STRICT_TERMS);
        if (!hasCore) {
            return false;
        }
        return !containsAnyTerm(text, KEYBOARD_EXCLUDE_TERMS);
    }

    private boolean isPhoneLikeProduct(ProductVO p) {
        String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getDescription())).toLowerCase(Locale.ROOT);
        boolean hasCore = containsAnyTerm(text, PHONE_STRICT_TERMS);
        if (!hasCore) {
            return false;
        }
        return !containsAnyTerm(text, PHONE_EXCLUDE_TERMS);
    }

    private List<ProductVO> filterCandidatesByPromptSubject(String prompt, List<ProductVO> candidates) {
        if (prompt == null || prompt.isBlank() || candidates == null || candidates.isEmpty()) {
            return candidates == null ? List.of() : candidates;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        boolean phoneCue = lastPhoneIntentIndex(lowered) >= 0;
        boolean headphoneCue = containsAnyTerm(lowered, HEADPHONE_STRICT_TERMS) || containsAnyTerm(lowered, HEADPHONE_ACCESSORY_TERMS);
        if (phoneCue) {
            List<ProductVO> phones = candidates.stream().filter(this::isPhoneLikeProduct).toList();
            if (!phones.isEmpty()) {
                return phones;
            }
        }
        if (headphoneCue) {
            List<ProductVO> headphones = candidates.stream().filter(this::isStrictHeadphoneProduct).toList();
            if (!headphones.isEmpty()) {
                return headphones;
            }
        }
        return candidates;
    }

    private boolean likelyBagProduct(ProductVO p) {
        String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getDescription())).toLowerCase(Locale.ROOT);
        boolean hasStrong = containsAnyTerm(text, BAG_STRONG_TERMS);
        boolean hasWeak = containsAnyTerm(text, BAG_TERMS);
        boolean noisy = containsAnyTerm(text, BAG_NOISE_TERMS);
        if (hasStrong) {
            return true;
        }
        if (noisy && !hasStrong) {
            return false;
        }
        return hasWeak;
    }

    private boolean isLightLikeProduct(ProductVO p) {
        String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getDescription())).toLowerCase(Locale.ROOT);
        return containsAnyTerm(text, LIGHT_TERMS);
    }

    private boolean isBikeLikeProduct(ProductVO p) {
        String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getDescription())).toLowerCase(Locale.ROOT);
        boolean matched = containsAnyTerm(text, BIKE_TERMS);
        if (!matched) {
            return false;
        }
        if (containsAnyTerm(text, BIKE_EXCLUDE_TERMS)) {
            return false;
        }
        if (containsAnyTerm(text, SHOE_TERMS) && !containsAnyTerm(text, BIKE_PRODUCT_TERMS)) {
            return false;
        }
        if (containsAnyTerm(text, BIKE_NON_PRODUCT_TERMS) && !containsAnyTerm(text, BIKE_PRODUCT_TERMS)) {
            return false;
        }
        return true;
    }

    private boolean isOutdoorLikeProduct(ProductVO p) {
        String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getDescription())).toLowerCase(Locale.ROOT);
        boolean matched = containsAnyTerm(text, OUTDOOR_TERMS)
            || (containsAny(text, "hiking", "trekking", "camping", "outdoor") && containsAny(text, "backpack", "boot", "tent", "jacket", "pole", "sleeping bag", "headlamp"));
        if (!matched) {
            return false;
        }
        return !containsAnyTerm(text, OUTDOOR_EXCLUDE_TERMS);
    }

    private boolean isPetLikeProduct(ProductVO p) {
        String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getDescription())).toLowerCase(Locale.ROOT);
        return containsAnyTerm(text, PET_TERMS);
    }

    private boolean isBabyLikeProduct(ProductVO p) {
        String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getDescription())).toLowerCase(Locale.ROOT);
        return containsAnyTerm(text, BABY_TERMS);
    }

    private boolean isBookLikeProduct(ProductVO p) {
        String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getDescription())).toLowerCase(Locale.ROOT);
        boolean matched = containsAnyTerm(text, BOOK_STRONG_TERMS) || containsAnyTerm(text, BOOK_TERMS);
        if (!matched) {
            return false;
        }
        return !containsAnyTerm(text, BOOK_EXCLUDE_TERMS);
    }

    private boolean isToyLikeProduct(ProductVO p) {
        String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getDescription())).toLowerCase(Locale.ROOT);
        if (!containsAnyTerm(text, TOY_TERMS)) {
            return false;
        }
        return !containsAnyTerm(text, TOY_PET_EXCLUDE_TERMS);
    }

    private boolean isChildToyLikeProduct(ProductVO p) {
        String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getDescription())).toLowerCase(Locale.ROOT);
        return containsAnyTerm(text, TOY_TERMS) && containsAnyTerm(text, TOY_CHILD_TERMS) && !containsAnyTerm(text, TOY_PET_EXCLUDE_TERMS);
    }

    private boolean isMakeupLikeProduct(ProductVO p) {
        String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getDescription())).toLowerCase(Locale.ROOT);
        boolean matched = containsAnyTerm(text, MAKEUP_TERMS);
        if (!matched) {
            return false;
        }
        return !containsAnyTerm(text, MAKEUP_EXCLUDE_TERMS);
    }

    private boolean isDailyLikeProduct(ProductVO p) {
        String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getDescription())).toLowerCase(Locale.ROOT);
        boolean matched = containsAnyTerm(text, DAILY_STRONG_TERMS)
            || containsAnyTerm(text, DAILY_TERMS)
            || containsAnyTerm(text, FOOD_TERMS);
        if (!matched) {
            return false;
        }
        return !containsAnyTerm(text, DAILY_EXCLUDE_TERMS);
    }

    private boolean isBeddingLikeProduct(ProductVO p) {
        String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getDescription())).toLowerCase(Locale.ROOT);
        boolean matched = containsAnyTerm(text, BEDDING_TERMS)
            || containsAny(text, "blanket", "bedspread", "coverlet", "sheet set", "pillow sham");
        if (!matched) {
            return false;
        }
        return !containsAnyTerm(text, BEDDING_EXCLUDE_TERMS);
    }

    private boolean isFoodLikeProduct(ProductVO p) {
        String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getCategoryPath()) + " " + nvl(p.getDescription())).toLowerCase(Locale.ROOT);
        boolean hasFoodSignal = containsAnyTerm(text, FOOD_TERMS)
            || containsAny(text, "\u98df\u54c1", "\u96f6\u98df", "\u996e\u6599", "\u8425\u517b", "food", "snack", "beverage", "nutrition");
        if (!hasFoodSignal) {
            return false;
        }
        if (containsAnyTerm(text, FOOD_EXCLUDE_TERMS) || containsAnyTerm(text, FOOD_NON_EDIBLE_TERMS)) {
            return false;
        }
        return containsAnyTerm(text, FOOD_EDIBLE_HINT_TERMS);
    }

    private List<ProductVO> filterEdibleCandidates(List<ProductVO> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<ProductVO> out = new ArrayList<>();
        for (ProductVO p : candidates) {
            if (p != null && isFoodLikeProduct(p)) {
                out.add(p);
            }
        }
        return out;
    }

    private List<ProductVO> strictFoodCandidates(int limit) {
        ensureProductCacheLoaded();
        int capped = Math.max(1, limit);
        List<ProductVO> edible = new ArrayList<>();
        for (ProductVO p : productCache.values()) {
            if (p != null && isFoodLikeProduct(p)) {
                edible.add(p);
            }
        }
        edible.sort(
            Comparator.comparing((ProductVO p) -> p.getSales() == null ? 0 : p.getSales()).reversed()
                .thenComparing((ProductVO p) -> p.getRating() == null ? BigDecimal.ZERO : p.getRating(), Comparator.reverseOrder())
        );
        if (edible.size() <= capped) {
            return edible;
        }
        return edible.subList(0, capped);
    }

    private boolean hasKitchenStorageSignals(ProductVO p) {
        String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getDescription())).toLowerCase(Locale.ROOT);
        boolean kitchen = containsAnyTerm(text, KITCHEN_CORE_TERMS);
        boolean storage = containsAnyTerm(text, STORAGE_CORE_TERMS);
        return kitchen && storage;
    }

    private boolean likelyPetBowlProduct(ProductVO p) {
        String text = (nvl(p.getName()) + " " + nvl(p.getCategory()) + " " + nvl(p.getDescription())).toLowerCase(Locale.ROOT);
        boolean hasPet = containsAny(text, "\u72d7", "\u732b", "\u5ba0\u7269", "dog", "cat", "pet");
        boolean hasBowl = containsAny(text, "\u7897", "\u98df\u76c6", "\u996d\u76c6", "\u5582\u98df", "bowl", "feeder", "dish");
        return hasPet && hasBowl;
    }

    private boolean isPetBowlQuery(String lowered) {
        if (lowered == null || lowered.isBlank()) {
            return false;
        }
        boolean pet = containsAny(lowered, "\u72d7", "\u732b", "\u5ba0\u7269", "dog", "cat", "pet");
        boolean bowl = containsAny(lowered, "\u7897", "\u98df\u76c6", "\u996d\u76c6", "\u5582\u98df", "bowl", "feeder", "dish");
        return pet && bowl;
    }

    @SafeVarargs
    private final boolean matchesAny(String input, Predicate<String>... checks) {
        for (Predicate<String> check : checks) {
            if (check.test(input)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String text, String... keys) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String k : keys) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }
    private boolean containsAnyTerm(String text, List<String> terms) {
        if (text == null || text.isBlank() || terms == null || terms.isEmpty()) {
            return false;
        }
        for (String t : terms) {
            if (text.contains(t)) {
                return true;
            }
        }
        return false;
    }

    private int lastIndexAny(String text, List<String> terms) {
        if (text == null || text.isBlank() || terms == null || terms.isEmpty()) {
            return -1;
        }
        int best = -1;
        String lowered = text.toLowerCase(Locale.ROOT);
        for (String t : terms) {
            if (t == null || t.isBlank()) {
                continue;
            }
            int idx = lowered.lastIndexOf(t.toLowerCase(Locale.ROOT));
            if (idx > best) {
                best = idx;
            }
        }
        return best;
    }

    private boolean isExplicitIntentSwitchPrompt(String prompt, ConversationMemory memory, IntentType currentIntent) {
        if (prompt == null || prompt.isBlank() || memory == null) {
            return false;
        }
        if (memory.lastIntent == IntentType.NONE) {
            return false;
        }
        IntentType rawIntent = detectIntent(prompt);
        IntentType targetIntent = rawIntent != IntentType.NONE ? rawIntent : currentIntent;
        if (targetIntent == IntentType.NONE || targetIntent == memory.lastIntent) {
            return false;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        boolean hasNeg = containsAny(lowered, "\u4e0d\u8981", "\u522b", "\u4e0d\u770b", "\u6392\u9664", "not", "don't", "no");
        boolean hasSearchVerb = containsAny(
            lowered,
            "\u63a8\u8350", "\u627e", "\u641c", "\u641c\u7d22", "\u6211\u60f3\u4e70", "\u6211\u60f3\u8981", "\u6211\u8981", "\u6362\u6210", "\u6362\u4e2a", "\u6539\u6210",
            "recommend", "find", "search", "show", "switch to", "change to", "i want", "want"
        );
        return hasSearchVerb || hasNeg;
    }

    private boolean shouldInheritIntentFromContext(
        String prompt,
        String lowered,
        ConversationMemory memory,
        DialogAct dialogAct,
        boolean explicitFreshSearch,
        boolean explicitIntentSwitch
    ) {
        if (memory == null || explicitFreshSearch || explicitIntentSwitch) {
            return false;
        }
        IntentType memoryIntent = inferIntentFromMemory(memory);
        if (memoryIntent == IntentType.NONE) {
            return false;
        }
        boolean hasContext = (memory.lastShownProductIds != null && !memory.lastShownProductIds.isEmpty())
            || (memory.lastAttributeSourceIds != null && !memory.lastAttributeSourceIds.isEmpty())
            || memory.lastFocusedProductId != null;
        if (!hasContext) {
            return false;
        }
        if (dialogAct == DialogAct.FOLLOW_UP || dialogAct == DialogAct.CONFIRM || dialogAct == DialogAct.ACTION) {
            return true;
        }
        String text = lowered == null ? nvl(prompt).toLowerCase(Locale.ROOT) : lowered;
        if (hasCurrentScopeCue(text) || hasOtherScopeCue(text) || hasBatchScopeCue(text)) {
            return true;
        }
        if (containsAny(
            text,
            "这个", "那个", "这款", "那款", "这件", "那件", "上一条", "上一个", "刚才那个", "按刚才的",
            "this one", "that one", "previous one", "same type", "as before", "based on that"
        )) {
            return true;
        }
        if (prompt == null) {
            return false;
        }
        String compact = prompt.replaceAll("\\s+", "");
        return compact.length() <= 16 && !isExplicitNewNeedPrompt(prompt);
    }

    private boolean hasCurrentScopeCue(String lowered) {
        return containsAnyTerm(lowered, SCOPE_CURRENT_TERMS);
    }

    private boolean hasOtherScopeCue(String lowered) {
        return containsAnyTerm(lowered, SCOPE_OTHER_TERMS);
    }

    private boolean hasBatchScopeCue(String lowered) {
        return containsAnyTerm(lowered, SCOPE_BATCH_TERMS);
    }

    private boolean hasScopeResetCue(String lowered) {
        return containsAnyTerm(lowered, SCOPE_RESET_TERMS);
    }

    private String strategyHint(String prompt, QueryConstraints constraints) {
        String lowered = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        if (containsAnyTerm(lowered, VALUE_TERMS) || (constraints != null && constraints.sortPref() == SortPref.PRICE_ASC)) {
            return "\u5df2\u6309\u201c\u6027\u4ef7\u6bd4/\u4ef7\u683c\u4ece\u4f4e\u5230\u9ad8\u201d\u4f18\u5148\u7b5b\u9009\u3002";
        }
        if (containsAnyTerm(lowered, PREMIUM_TERMS) || (constraints != null && constraints.sortPref() == SortPref.PRICE_DESC)) {
            return "\u5df2\u6309\u201c\u9ad8\u7aef/\u4ef7\u683c\u4ece\u9ad8\u5230\u4f4e\u201d\u4f18\u5148\u7b5b\u9009\u3002";
        }
        if (containsAnyTerm(lowered, HOT_TERMS) || (constraints != null && constraints.sortPref() == SortPref.SALES_DESC)) {
            return "\u5df2\u6309\u201c\u70ed\u95e8\u9500\u91cf\u201d\u4f18\u5148\u7b5b\u9009\u3002";
        }
        if (containsAnyTerm(lowered, RATING_TERMS) || (constraints != null && constraints.sortPref() == SortPref.RATING_DESC)) {
            return "\u5df2\u6309\u201c\u9ad8\u8bc4\u5206\u53e3\u7891\u201d\u4f18\u5148\u7b5b\u9009\u3002";
        }
        return "\u5df2\u6309\u7efc\u5408\u76f8\u5173\u6027\u7b5b\u9009\u3002";
    }

    private String buildNeedSummary(String prompt, QueryConstraints constraints) {
        StringBuilder sb = new StringBuilder();
        sb.append("\u5df2\u7406\u89e3\u4f60\u7684\u9700\u6c42");
        if (prompt != null && !prompt.isBlank()) {
            sb.append("\uff1a").append(prompt.trim());
        }
        List<String> tags = new ArrayList<>();
        if (constraints != null) {
            if (constraints.minPrice() != null || constraints.maxPrice() != null) {
                String min = constraints.minPrice() == null ? "0" : constraints.minPrice().toPlainString();
                String max = constraints.maxPrice() == null ? "\u4e0d\u9650" : constraints.maxPrice().toPlainString();
                tags.add("\u4ef7\u683c " + min + "-" + max);
            }
            if (constraints.sortPref() != null && constraints.sortPref() != SortPref.DEFAULT) {
                tags.add("\u6392\u5e8f " + constraints.sortPref().name());
            }
            if (constraints.requestedCount() > 0) {
                tags.add("\u6570\u91cf " + constraints.requestedCount());
            }
        }
        if (!tags.isEmpty()) {
            sb.append(" (").append(String.join(" / ", tags)).append(")");
        }
        return sb.toString();
    }

    private String specificExampleForEmptyResult(String prompt, IntentType intent, ConversationMemory memory) {
        IntentType effective = intent == null ? IntentType.NONE : intent;
        if (effective == IntentType.NONE && prompt != null && !prompt.isBlank()) {
            effective = detectIntent(prompt);
        }
        if (effective == IntentType.NONE && containsAnyTerm(nvl(prompt).toLowerCase(Locale.ROOT), OUTDOOR_SCENARIO_TERMS)) {
            effective = IntentType.OUTDOOR;
        }
        if (effective == IntentType.NONE && containsAnyTerm(nvl(prompt).toLowerCase(Locale.ROOT), TRAVEL_SCENARIO_TERMS)) {
            effective = IntentType.BAG;
        }
        if (effective == IntentType.NONE && containsAnyTerm(nvl(prompt).toLowerCase(Locale.ROOT), STAY_SCENARIO_TERMS)) {
            effective = IntentType.BEDDING;
        }
        if (effective == IntentType.NONE && containsAnyTerm(nvl(prompt).toLowerCase(Locale.ROOT), EAT_SCENARIO_TERMS)) {
            effective = IntentType.DAILY;
        }
        if (effective == IntentType.NONE) {
            effective = inferIntentFromMemory(memory);
        }
        return switch (effective) {
            case HEADPHONE -> "\u84dd\u7259\u8033\u673a 200\u5143\u4ee5\u5185";
            case SHOE -> "\u5f92\u6b65\u978b \u7537\u6b3e \u9632\u6ed1 400\u5143\u4ee5\u5185";
            case BAG -> "\u767b\u5c71\u80cc\u5305 40L \u9632\u6c34";
            case OUTDOOR -> "\u767b\u5c71\u80cc\u5305 40L \u9632\u6c34 500\u5143\u4ee5\u5185";
            case LIGHT -> "\u5934\u706f \u5145\u7535\u6b3e \u8f7b\u91cf";
            case BIKE -> "\u5c71\u5730\u81ea\u884c\u8f66 \u94dd\u5408\u91d1 \u5165\u95e8\u6b3e";
            case COMPUTER -> "\u8f7b\u8584\u672c 16G\u5185\u5b58 5000\u5143\u4ee5\u5185";
            case ELECTRONICS -> "\u624b\u673a \u7eed\u822a\u957f \u62cd\u7167\u597d 3000\u5143\u4ee5\u5185";
            case BEDDING -> "\u88ab\u5b50 \u5927\u53f7 \u8f7b\u8584 \u5168\u5b63";
            case DAILY -> "\u96f6\u98df\u996e\u6599 \u65e5\u5e38\u56e4\u8d27 100\u5143\u4ee5\u5185";
            default -> "\u8bf4\u4e0b\u54c1\u7c7b + \u9884\u7b97 + \u5173\u952e\u9700\u6c42\uff0c\u6bd4\u5982\uff1a\u96f6\u98df 100\u5143\u4ee5\u5185 \u6216 \u5e8a\u4e0a\u56db\u4ef6\u5957 \u5168\u5b63";
        };
    }

    private void maybeLogMetrics() {
        long total = requestCount.get();
        if (total <= 0 || total % 20 != 0) {
            return;
        }
        System.out.println("[AI Agent Metrics] total=" + total
            + ", empty=" + emptyResultCount.get()
            + ", clarify=" + clarifyCount.get()
            + ", handoff=" + handoffCount.get());
    }

    private boolean containsCjk(String text) {
        return text != null && CJK_PATTERN.matcher(text).find();
    }

    private boolean looksLikeFreshSearch(String prompt) {
        return isExplicitNewNeedPrompt(prompt);
    }

    private boolean isPreferenceOnlyPrompt(String lowered) {
        if (lowered == null || lowered.isBlank()) {
            return false;
        }
        return containsAny(
            lowered,
            "\u504f\u539a", "\u504f\u8584", "\u539a\u6696", "\u66f4\u6696", "\u8f7b\u8584", "\u900f\u6c14",
            "\u5927\u53f7", "\u7279\u5927\u53f7", "\u5927\u53f7\u5e8a", "\u7279\u5927\u53f7\u5e8a",
            "\u53ea\u8981", "\u4e0d\u8981\u5957\u88c5", "\u53ea\u8981\u88ab\u5b50",
            "thick", "thin", "warmer", "warm", "lightweight", "breathable",
            "queen", "king", "only", "without set", "no set", "duvet only", "comforter only"
        );
    }

    private boolean isExplicitNewNeedPrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        if (isPreferenceOnlyPrompt(lowered)) {
            return false;
        }
        if (isAddToCartIntent(lowered)
            || isCheckoutIntent(lowered)
            || isIncrementIntent(lowered)
            || isBatchAddIntent(lowered)
            || isRemoveCartIntent(lowered)
            || isCartQueryIntent(lowered)
            || isClearCartIntent(lowered)
            || isReplaceProductIntent(lowered)) {
            return false;
        }
        if (isRecommendOneFollowUp(prompt)
            || isFollowUpQuery(prompt)
            || isComparativeFollowUp(prompt)
            || isCheaperFollowUp(prompt)) {
            return false;
        }
        boolean ask = containsAny(
            lowered,
            "\u63a8\u8350", "\u627e", "\u641c", "\u641c\u7d22", "\u6709\u6ca1\u6709", "\u6211\u60f3\u4e70", "\u6211\u8981", "\u60f3\u8981", "\u60f3\u4e70", "\u9700\u8981",
            "\u5e2e\u6211\u627e", "\u7ed9\u6211\u63a8\u8350", "\u7ed9\u6211\u627e", "\u6765\u4e2a", "\u6765\u70b9",
            "recommend", "find", "search", "show me", "looking for", "i want", "i need"
        );
        if (!ask) {
            return false;
        }
        boolean anchoredCue = hasCurrentScopeCue(lowered)
            || hasBatchScopeCue(lowered)
            || hasOtherScopeCue(lowered)
            || containsAny(lowered, "\u8fd9\u4e2a", "\u90a3\u4e2a", "\u7b2c\u4e00\u4e2a", "\u7b2c\u4e8c\u4e2a", "this one", "that one", "the first", "the second");
        List<String> promptTopicTokens = extractPromptTopicTokens(prompt);
        boolean explicitTopic = promptTopicTokens != null && !promptTopicTokens.isEmpty();
        if (explicitTopic) {
            anchoredCue = hasCurrentScopeCue(lowered) || hasBatchScopeCue(lowered) || hasOtherScopeCue(lowered);
        }
        if (anchoredCue) {
            return false;
        }
        List<String> tokens = splitQueryTokens(normalizeQuery(prompt));
        int effective = 0;
        for (String t : tokens) {
            if (t == null || t.isBlank() || QUERY_STOP_WORDS.contains(t)) {
                continue;
            }
            if (!containsCjk(t) && t.length() < 3) {
                continue;
            }
            effective++;
        }
        return effective >= 1;
    }

    private boolean shouldFailClosedForSpecificQuery(String prompt, boolean anchoredFollowUp, List<ProductVO> candidates) {
        if (prompt == null || prompt.isBlank() || candidates == null || candidates.isEmpty()) {
            return false;
        }
        if (detectIntent(prompt) != IntentType.NONE) {
            return false;
        }
        if (anchoredFollowUp) {
            return false;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        boolean explicitSearch = containsAny(
            lowered,
            "\u63a8\u8350", "\u627e", "\u641c", "\u641c\u7d22", "\u6211\u60f3\u4e70", "\u6211\u8981", "\u60f3\u8981", "\u60f3\u4e70", "\u9700\u8981", "\u6709\u6ca1\u6709",
            "recommend", "find", "search", "show me", "looking for"
        );
        if (!explicitSearch) {
            // Even without explicit search verbs, a short noun-like query should not return unrelated fillers.
            List<String> quickTokens = splitQueryTokens(normalizeQuery(prompt));
            int effectiveQuick = 0;
            for (String t : quickTokens) {
                if (!QUERY_STOP_WORDS.contains(t) && (containsCjk(t) || t.length() >= 3)) {
                    effectiveQuick++;
                }
            }
            explicitSearch = effectiveQuick >= 1;
        }
        if (!explicitSearch) {
            return false;
        }
        List<String> tokens = splitQueryTokens(normalizeQuery(prompt));
        List<String> effective = new ArrayList<>();
        for (String t : tokens) {
            if (!QUERY_STOP_WORDS.contains(t)) {
                effective.add(t);
            }
        }
        if (effective.isEmpty()) {
            return false;
        }
        ProductVO top = candidates.get(0);
        String text = (nvl(top.getName()) + " " + nvl(top.getCategory()) + " " + nvl(top.getBrand()) + " " + nvl(top.getDescription()))
            .toLowerCase(Locale.ROOT);
        int hits = 0;
        for (String t : effective) {
            String key = t.toLowerCase(Locale.ROOT);
            if (key.length() < 2 && !containsCjk(key)) {
                continue;
            }
            if (text.contains(key)) {
                hits++;
            }
        }
        return hits <= 0;
    }

    private List<String> rewriteToEnglishTerms(String query) {
        if (indexingInProgress || chatModel == null || query == null || query.isBlank()) {
            return List.of();
        }
        try {
            String prompt = "Convert the user shopping query to 3-5 short English search keywords. "
                + "Output only comma-separated keywords, no explanation.\nQuery: " + query;
            String raw = callChatModel("rewrite_to_english", prompt);
            if (raw == null || raw.isBlank()) {
                return List.of();
            }
            List<String> aliases = new ArrayList<>();
            for (String part : raw.toLowerCase(Locale.ROOT).split("[,\\n;|]+")) {
                String cleaned = part.trim().replaceAll("\\s+", " ");
                if (cleaned.length() < 2 || cleaned.length() > 40) {
                    continue;
                }
                if (!aliases.contains(cleaned)) {
                    aliases.add(cleaned);
                }
                if (aliases.size() >= 5) {
                    break;
                }
            }
            return aliases;
        } catch (Exception ex) {
            return List.of();
        }
    }
    private AgentReply handleTransactionalIntent(String prompt, Long userId, ConversationMemory memory) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);

        if (isPreviousBatchSwitchPrompt(lowered) && memory != null && memory.previousShownProductIds != null && !memory.previousShownProductIds.isEmpty()) {
            memory.lastShownProductIds = new ArrayList<>(memory.previousShownProductIds);
            List<ProductVO> shown = lastShownProducts(memory);
            if (!shown.isEmpty()) {
                memory.lastFocusedProductId = shown.get(0).getId();
                StringBuilder sb = new StringBuilder("\u5df2\u4e3a\u4f60\u5207\u6362\u5230\u4e0a\u4e00\u6279\u5019\u9009\u5546\u54c1\uff1a");
                for (int i = 0; i < Math.min(4, shown.size()); i++) {
                    ProductVO p = shown.get(i);
                    sb.append("\n").append(i + 1).append(". ").append(nvl(p.getName()))
                        .append(" (ID: ").append(p.getId()).append("\uff0c\u4ef7\u683c: ").append(nvl(String.valueOf(p.getPrice()))).append(" ").append(nvl(p.getCurrency())).append(")");
                }
                return new AgentReply(sb.toString(), shown.stream().limit(4).toList());
            }
        }

        if (isCheckoutIntent(lowered)) {
            if (userId == null) {
                return new AgentReply("\u68c0\u6d4b\u5230\u4e0b\u5355\u610f\u56fe\uff0c\u4f46\u4f60\u8fd8\u6ca1\u6709\u767b\u5f55\uff0c\u8bf7\u5148\u767b\u5f55\u3002", List.of());
            }
            ShippingInfo shippingInfo = extractShippingInfo(prompt);
            if (!shippingInfo.complete()) {
                return new AgentReply("\u4e0b\u5355\u9700\u8981\u6536\u8d27\u4fe1\u606f\uff1a\u6536\u8d27\u4eba\u3001\u7535\u8bdd\u3001\u5730\u5740\u3002\u793a\u4f8b\uff1a\u4e0b\u5355 \u6536\u8d27\u4eba \u5f20\u4e09 \u7535\u8bdd 13800138000 \u5730\u5740 \u4e0a\u6d77\u5e02\u6d66\u4e1c\u65b0\u533aXX\u8def1\u53f7", List.of());
            }
            List<OrderItemRequest> items = buildOrderItemsFromPromptOrCart(userId, prompt);
            if (items.isEmpty()) {
                return new AgentReply("\u6ca1\u6709\u53ef\u4e0b\u5355\u5546\u54c1\u3002\u4f60\u53ef\u4ee5\u5148\u8bf4\uff1a\u52a0\u5165\u8d2d\u7269\u8f66 \u5546\u54c1ID 123 \u6570\u91cf 2", List.of());
            }
            try {
                CreateOrderRequest req = new CreateOrderRequest();
                req.setName(shippingInfo.name());
                req.setPhone(shippingInfo.phone());
                req.setAddress(shippingInfo.address());
                req.setItems(items);
                OrderCreateVO created = shopOrderService.createOrder(userId, req);
                String msg = "\u4e0b\u5355\u6210\u529f\uff0c\u8ba2\u5355\u53f7: " + nvl(created.getOrderId()) + "\uff0c\u5e94\u4ed8\u91d1\u989d: " + created.getTotalAmount();
                return new AgentReply(msg, List.of());
            } catch (Exception ex) {
                return new AgentReply("\u4e0b\u5355\u5931\u8d25: " + ex.getMessage(), List.of());
            }
        }

        if (isUndoLastAddIntent(lowered)) {
            if (userId == null) {
                return new AgentReply("\u8981\u64a4\u9500\u52a0\u8d2d\uff0c\u8bf7\u5148\u767b\u5f55\u3002", List.of());
            }
            if (memory == null || memory.lastCartAddProductId == null || memory.lastCartAddQty <= 0) {
                return new AgentReply("\u8fd9\u8fb9\u6ca1\u6709\u53ef\u64a4\u9500\u7684\u6700\u8fd1\u4e00\u6b21\u52a0\u8d2d\u8bb0\u5f55\u3002", List.of());
            }
            try {
                List<CartItemVO> cart = shopCartService.listCart(userId);
                CartItemVO hit = null;
                for (CartItemVO c : cart) {
                    if (c != null && Objects.equals(c.getProductId(), memory.lastCartAddProductId)) {
                        hit = c;
                        break;
                    }
                }
                if (hit == null) {
                    return new AgentReply("\u8d2d\u7269\u8f66\u91cc\u6ca1\u6709\u627e\u5230\u521a\u624d\u90a3\u4ef6\u5546\u54c1\uff0c\u53ef\u80fd\u5df2\u88ab\u79fb\u9664\u3002", List.of());
                }
                int remain = Math.max(0, (hit.getQuantity() == null ? 0 : hit.getQuantity()) - memory.lastCartAddQty);
                if (remain <= 0) {
                    shopCartService.removeCart(userId, hit.getId(), hit.getProductId());
                } else {
                    shopCartService.updateCart(userId, hit.getId(), hit.getProductId(), remain);
                }
                ProductVO p = productCache.get(memory.lastCartAddProductId);
                memory.lastCartAddQty = 0;
                return new AgentReply("\u5df2\u64a4\u9500\u6700\u8fd1\u4e00\u6b21\u52a0\u8d2d\uff1a" + (p == null ? "\u8be5\u5546\u54c1" : p.getName()) + "\u3002", p == null ? List.of() : List.of(p));
            } catch (Exception ex) {
                return new AgentReply("\u64a4\u9500\u5931\u8d25\uff1a" + ex.getMessage(), List.of());
            }
        }

        if (isCartQueryIntent(lowered)) {
            if (userId == null) {
                return new AgentReply("\u8981\u67e5\u770b\u8d2d\u7269\u8f66\uff0c\u8bf7\u5148\u767b\u5f55\u3002", List.of());
            }
            List<CartItemVO> cart = shopCartService.listCart(userId);
            if (cart == null || cart.isEmpty()) {
                return new AgentReply("\u4f60\u7684\u8d2d\u7269\u8f66\u8fd8\u662f\u7a7a\u7684\u3002\u53ef\u4ee5\u5148\u8bf4\uff1a\u63a8\u8350\u8033\u673a\uff0c\u7136\u540e\u8bf4\u201c\u628a\u7b2c\u4e00\u4e2a\u52a0\u5165\u8d2d\u7269\u8f66\u201d\u3002", List.of());
            }
            int totalQty = 0;
            BigDecimal totalAmount = BigDecimal.ZERO;
            StringBuilder sb = new StringBuilder("\u4f60\u7684\u8d2d\u7269\u8f66\u91cc\u6709\u8fd9\u4e9b\u5546\u54c1\uff1a");
            List<ProductVO> products = new ArrayList<>();
            int limit = Math.min(4, cart.size());
            for (int i = 0; i < cart.size(); i++) {
                CartItemVO item = cart.get(i);
                int qty = item.getQuantity() == null ? 1 : Math.max(1, item.getQuantity());
                totalQty += qty;
                if (item.getPrice() != null) {
                    totalAmount = totalAmount.add(item.getPrice().multiply(BigDecimal.valueOf(qty)));
                }
                if (i < limit) {
                    sb.append("\n").append(i + 1).append(". ")
                        .append(nvl(item.getName()))
                        .append(" x ").append(qty)
                        .append(" (ID: ").append(nvl(String.valueOf(item.getProductId())))
                        .append("\uff0c\u5355\u4ef7: ").append(nvl(String.valueOf(item.getPrice())))
                        .append(")");
                    ProductVO p = toProductFromCartItem(item);
                    if (p != null) {
                        products.add(p);
                    }
                }
            }
            sb.append("\n\n\u5171 ").append(totalQty).append(" \u4ef6\uff0c\u9884\u4f30\u603b\u989d ").append(totalAmount).append(" USD\u3002")
                .append("\n\u4f60\u53ef\u4ee5\u7ee7\u7eed\u8bf4\uff1a\u628a\u7b2c\u4e00\u4e2a\u4e0b\u5355 / \u79fb\u9664\u7b2c\u4e8c\u4e2a / \u6e05\u7a7a\u8d2d\u7269\u8f66\u3002");
            if (!products.isEmpty()) {
                memory.lastShownProductIds = products.stream().map(ProductVO::getId).filter(Objects::nonNull).toList();
                memory.lastFocusedProductId = products.get(0).getId();
            }
            return new AgentReply(sb.toString(), products);
        }

        if (isClearCartIntent(lowered)) {
            if (userId == null) {
                return new AgentReply("\u8981\u6e05\u7a7a\u8d2d\u7269\u8f66\uff0c\u8bf7\u5148\u767b\u5f55\u3002", List.of());
            }
            List<CartItemVO> cart = shopCartService.listCart(userId);
            if (cart == null || cart.isEmpty()) {
                return new AgentReply("\u4f60\u7684\u8d2d\u7269\u8f66\u672c\u6765\u5c31\u662f\u7a7a\u7684\u3002", List.of());
            }
            int cleared = 0;
            for (CartItemVO c : cart) {
                try {
                    shopCartService.removeCart(userId, c.getId(), c.getProductId());
                    cleared++;
                } catch (Exception ignored) {
                }
            }
            memory.lastCartAddQty = 0;
            return new AgentReply("\u5df2\u6e05\u7a7a\u8d2d\u7269\u8f66\uff0c\u79fb\u9664\u4e86 " + cleared + " \u4ef6\u5546\u54c1\u3002", List.of());
        }

        if (isReplaceProductIntent(lowered)) {
            if (userId == null) {
                return new AgentReply("\u8981\u66ff\u6362\u5546\u54c1\uff0c\u8bf7\u5148\u767b\u5f55\u3002", List.of());
            }
            List<CartItemVO> cart = shopCartService.listCart(userId);
            if (cart == null || cart.isEmpty()) {
                return new AgentReply("\u4f60\u7684\u8d2d\u7269\u8f66\u662f\u7a7a\u7684\uff0c\u5148\u52a0\u8d2d\u4e00\u4ef6\u518d\u8bf4\u201c\u6362\u6210\u53e6\u5916\u4e00\u4e2a\u201d\u5373\u53ef\u3002", List.of());
            }
            ProductVO target = resolveTargetProduct(prompt, lastShownProducts(memory));
            if ((target == null || target.getId() == null) && memory != null && memory.lastFocusedProductId != null) {
                target = productCache.get(memory.lastFocusedProductId);
            }
            if (target == null || target.getId() == null) {
                return new AgentReply(
                    "\u6211\u8fd8\u4e0d\u786e\u5b9a\u8981\u6362\u6210\u54ea\u4e2a\u5546\u54c1\u3002\u53ef\u4ee5\u8bf4\uff1a\u628a\u7b2c\u4e00\u4e2a\u6362\u6210\u7b2c\u4e8c\u4e2a / \u6362\u6210 ID 123\u3002",
                    List.of()
                );
            }
            CartItemVO source = resolveCartItemForReplacement(prompt, cart, memory, target);
            if (source == null) {
                return new AgentReply(
                    "\u6211\u8fd8\u4e0d\u786e\u5b9a\u4f60\u8981\u66ff\u6362\u8d2d\u7269\u8f66\u91cc\u7684\u54ea\u4ef6\u5546\u54c1\u3002\u53ef\u4ee5\u8bf4\uff1a\u628a\u8d2d\u7269\u8f66\u7b2c\u4e00\u4e2a\u6362\u6210\u8fd9\u4e2a\u3002",
                    List.of(target)
                );
            }
            if (Objects.equals(source.getProductId(), target.getId())) {
                return new AgentReply("\u4f60\u8981\u6362\u6210\u7684\u5546\u54c1\u5df2\u7ecf\u5728\u8d2d\u7269\u8f66\u91cc\u4e86\u3002", List.of(target));
            }
            int qty = resolveReplacementQuantity(prompt, source);
            try {
                shopCartService.removeCart(userId, source.getId(), source.getProductId());
                shopCartService.addToCart(userId, target.getId(), qty);
                memory.lastFocusedProductId = target.getId();
                memory.lastCartAddProductId = target.getId();
                memory.lastCartAddQty = qty;
                ProductVO from = toProductFromCartItem(source);
                String msg = "\u5df2\u4e3a\u4f60\u66ff\u6362\uff1a"
                    + nvl(source.getName()) + " \u2192 " + nvl(target.getName())
                    + "\uff0c\u6570\u91cf " + qty + "\u3002";
                if (from != null && from.getId() != null && !Objects.equals(from.getId(), target.getId())) {
                    return new AgentReply(msg, List.of(target, from));
                }
                return new AgentReply(msg, List.of(target));
            } catch (Exception ex) {
                return new AgentReply("\u66ff\u6362\u5931\u8d25\uff1a" + ex.getMessage(), List.of(target));
            }
        }

        if (isRemoveCartIntent(lowered)) {
            if (userId == null) {
                return new AgentReply("\u8981\u5220\u9664\u8d2d\u7269\u8f66\u5546\u54c1\uff0c\u8bf7\u5148\u767b\u5f55\u3002", List.of());
            }
            List<CartItemVO> cart = shopCartService.listCart(userId);
            if (cart == null || cart.isEmpty()) {
                return new AgentReply("\u4f60\u7684\u8d2d\u7269\u8f66\u662f\u7a7a\u7684\uff0c\u6ca1\u6709\u53ef\u79fb\u9664\u7684\u5546\u54c1\u3002", List.of());
            }
            CartItemVO target = resolveCartItemForRemoval(prompt, cart);
            if (target == null) {
                return new AgentReply("\u6211\u8fd8\u4e0d\u786e\u5b9a\u8981\u79fb\u9664\u54ea\u4ef6\u5546\u54c1\u3002\u4f60\u53ef\u4ee5\u8bf4\uff1a\u79fb\u9664\u7b2c\u4e00\u4e2a / \u5220\u9664\u6700\u8d35\u7684\u3002", List.of());
            }
            try {
                shopCartService.removeCart(userId, target.getId(), target.getProductId());
                ProductVO p = toProductFromCartItem(target);
                return new AgentReply("\u5df2\u4ece\u8d2d\u7269\u8f66\u79fb\u9664\uff1a" + nvl(target.getName()) + "\u3002", p == null ? List.of() : List.of(p));
            } catch (Exception ex) {
                return new AgentReply("\u79fb\u9664\u5931\u8d25\uff1a" + ex.getMessage(), List.of());
            }
        }

        if (isAddToCartIntent(lowered)) {
            if (userId == null) {
                return new AgentReply("\u68c0\u6d4b\u5230\u52a0\u8d2d\u610f\u56fe\uff0c\u4f46\u4f60\u8fd8\u6ca1\u6709\u767b\u5f55\uff0c\u8bf7\u5148\u767b\u5f55\u3002", List.of());
            }
            List<ProductVO> scope = lastShownProducts(memory);
            List<ProductVO> setTargets = resolveSetTargetsByPrompt(prompt, memory, scope);
            if (!setTargets.isEmpty()) {
                int perQty = extractPerItemQuantity(prompt);
                List<ProductVO> added = new ArrayList<>();
                int success = 0;
                for (ProductVO p : setTargets) {
                    if (p == null || p.getId() == null) {
                        continue;
                    }
                    try {
                        shopCartService.addToCart(userId, p.getId(), perQty);
                        added.add(p);
                        success++;
                    } catch (Exception ignored) {
                    }
                }
                if (success > 0) {
                    memory.lastFocusedProductId = added.get(0).getId();
                    memory.lastCartAddProductId = added.get(0).getId();
                    memory.lastCartAddQty = perQty;
                    return new AgentReply(
                        "\u5df2\u52a0\u5165\u8d2d\u7269\u8f66\uff1a\u5171 " + success + " \u4e2a\u5546\u54c1\uff0c\u6bcf\u4ef6\u6570\u91cf " + perQty + "\u3002",
                        added.stream().limit(4).toList()
                    );
                }
            }
            int leadingQty = extractLeadingQuantityBeforeMetric(prompt);
            if (leadingQty > 1) {
                String metric = detectMetricToken(lowered, memory == null ? null : memory.lastAttributeKey);
                List<ProductVO> rankingScope = new ArrayList<>(scope);
                if (rankingScope.isEmpty() && memory != null && memory.lastAttributeSourceIds != null) {
                    for (Long id : memory.lastAttributeSourceIds) {
                        if (id == null) {
                            continue;
                        }
                        ProductVO p = productCache.get(id);
                        if (p != null) {
                            rankingScope.add(p);
                        }
                        if (rankingScope.size() >= 8) {
                            break;
                        }
                    }
                }
                if (metric != null && !metric.isBlank() && !rankingScope.isEmpty()) {
                    boolean pickLow = containsAny(lowered,
                        "\u6700\u4f4e", "\u6700\u5c11", "\u6700\u5dee", "\u66f4\u4f4e", "\u66f4\u5c11", "\u66f4\u4fbf\u5b9c", "\u6700\u4fbf\u5b9c", "lowest", "least", "worst", "cheapest", "cheaper");
                    boolean pickHigh = containsAny(lowered,
                        "\u6700\u9ad8", "\u6700\u591a", "\u6700\u597d", "\u6700\u706b", "\u6700\u70ed", "\u6700\u8d35", "highest", "most", "best", "hottest", "most expensive");
                    Comparator<ProductVO> cmp = comparatorByMetric(metric, pickLow && !pickHigh);
                    if (cmp != null) {
                        rankingScope.sort(cmp);
                        ProductVO target = rankingScope.get(0);
                        CartItemVO item = shopCartService.addToCart(userId, target.getId(), leadingQty);
                        int finalQty = item == null || item.getQuantity() == null ? leadingQty : item.getQuantity();
                        memory.lastFocusedProductId = target.getId();
                        memory.lastCartAddProductId = target.getId();
                        memory.lastCartAddQty = leadingQty;
                        return new AgentReply(
                            "\u5df2\u52a0\u5165\u8d2d\u7269\u8f66\uff1a" + target.getName() + " x " + leadingQty + "\uff08\u6309\u4f60\u7684\u8bed\u4e49\u89e3\u8bfb\u4e3a\u201c\u8be5\u5546\u54c1\u6570\u91cf " + leadingQty + "\u201d\uff09\uff0c\u5f53\u524d\u8d2d\u7269\u8f66\u6570\u91cf\uff1a" + finalQty,
                            List.of(target)
                        );
                    }
                }
            }
            List<ProductVO> parsedTargets = resolveTopNTargetsByPrompt(prompt, memory, scope);
            if (!parsedTargets.isEmpty()) {
                int perQty = extractPerItemQuantity(prompt);
                List<ProductVO> added = new ArrayList<>();
                int success = 0;
                for (ProductVO p : parsedTargets) {
                    if (p == null || p.getId() == null) {
                        continue;
                    }
                    try {
                        shopCartService.addToCart(userId, p.getId(), perQty);
                        added.add(p);
                        success++;
                    } catch (Exception ignored) {
                    }
                }
                if (success > 0) {
                    memory.lastFocusedProductId = added.get(0).getId();
                    memory.lastCartAddProductId = added.get(0).getId();
                    memory.lastCartAddQty = perQty;
                    return new AgentReply(
                        "\u5df2\u52a0\u5165\u8d2d\u7269\u8f66\uff1a\u5171 " + success + " \u4e2a\u5546\u54c1\uff0c\u6bcf\u4ef6\u6570\u91cf " + perQty + "\u3002",
                        added.stream().limit(4).toList()
                    );
                }
            }
            List<ProductVO> topTargets = resolveTopNTargetsByLastAttributeMemory(prompt, memory);
            if (!topTargets.isEmpty()) {
                int perQty = extractPerItemQuantity(prompt);
                List<ProductVO> added = new ArrayList<>();
                int success = 0;
                for (ProductVO p : topTargets) {
                    if (p == null || p.getId() == null) {
                        continue;
                    }
                    try {
                        shopCartService.addToCart(userId, p.getId(), perQty);
                        added.add(p);
                        success++;
                    } catch (Exception ignored) {
                    }
                }
                if (success > 0) {
                    memory.lastFocusedProductId = added.get(0).getId();
                    memory.lastCartAddProductId = added.get(0).getId();
                    memory.lastCartAddQty = perQty;
                    return new AgentReply(
                        "\u5df2\u628a\u6761\u4ef6\u6700\u4f18\u7684 " + success + " \u4e2a\u5546\u54c1\u52a0\u5165\u8d2d\u7269\u8f66\uff0c\u6bcf\u4ef6\u6570\u91cf " + perQty + "\u3002",
                        added.stream().limit(4).toList()
                    );
                }
            }
            ProductVO target = resolveTargetByLastAttributeMemory(prompt, memory);
            if (target == null || target.getId() == null) {
                target = resolveTargetProduct(prompt, scope);
            }
            if ((target == null || target.getId() == null) && memory.lastFocusedProductId != null) {
                target = productCache.get(memory.lastFocusedProductId);
            }
            if (target == null || target.getId() == null) {
                return new AgentReply(
                    "\u6211\u8fd8\u65e0\u6cd5\u786e\u5b9a\u4f60\u8981\u52a0\u8d2d\u7684\u5546\u54c1\u3002\u4f60\u53ef\u4ee5\u8bf4\uff1a\u628a\u7b2c\u4e00\u4e2a\u52a0\u5165\u8d2d\u7269\u8f66 / \u628a\u6700\u4fbf\u5b9c\u7684\u52a0\u5165\u8d2d\u7269\u8f66 / \u5546\u54c1ID 123 \u52a0\u8d2d\u3002",
                    List.of()
                );
            }
            int qty = Math.max(1, extractQuantity(prompt));
            CartItemVO item = shopCartService.addToCart(userId, target.getId(), qty);
            int finalQty = item == null || item.getQuantity() == null ? qty : item.getQuantity();
            memory.lastFocusedProductId = target.getId();
            memory.lastCartAddProductId = target.getId();
            memory.lastCartAddQty = qty;
            return new AgentReply("\u5df2\u52a0\u5165\u8d2d\u7269\u8f66\uff1a" + target.getName() + " x " + qty + "\uff0c\u5f53\u524d\u8d2d\u7269\u8f66\u6570\u91cf\uff1a" + finalQty, List.of(target));
        }

        if (isIncrementIntent(lowered)) {
            if (userId == null) {
                return new AgentReply("\u68c0\u6d4b\u5230\u52a0\u8d2d\u610f\u56fe\uff0c\u4f46\u4f60\u8fd8\u6ca1\u6709\u767b\u5f55\uff0c\u8bf7\u5148\u767b\u5f55\u3002", List.of());
            }
            ProductVO target = resolveTargetProduct(prompt, lastShownProducts(memory));
            if ((target == null || target.getId() == null) && memory.lastFocusedProductId != null) {
                target = productCache.get(memory.lastFocusedProductId);
            }
            if (target == null || target.getId() == null) {
                return new AgentReply("\u8bf7\u544a\u8bc9\u6211\u8981\u52a0\u8d2d\u54ea\u4ef6\u5546\u54c1\uff0c\u4f8b\u5982\uff1a\u591a\u4e70\u0031\u4ef6 \u5546\u54c1ID 123", List.of());
            }
            int qty = Math.max(1, extractQuantity(prompt));
            CartItemVO item = shopCartService.addToCart(userId, target.getId(), qty);
            int finalQty = item == null || item.getQuantity() == null ? qty : item.getQuantity();
            memory.lastFocusedProductId = target.getId();
            memory.lastCartAddProductId = target.getId();
            memory.lastCartAddQty = qty;
            return new AgentReply("\u5df2\u52a0\u8d2d\uff1a" + target.getName() + " x " + qty + "\uff0c\u8d2d\u7269\u8f66\u5f53\u524d\u6570\u91cf\uff1a" + finalQty, List.of(target));
        }

        if (isBatchAddIntent(lowered)) {
            if (userId == null) {
                return new AgentReply("\u68c0\u6d4b\u5230\u6279\u91cf\u52a0\u8d2d\u610f\u56fe\uff0c\u4f46\u4f60\u8fd8\u6ca1\u6709\u767b\u5f55\uff0c\u8bf7\u5148\u767b\u5f55\u3002", List.of());
            }
            List<Long> ids = extractProductIds(prompt);
            if (ids.isEmpty()) {
                ids = new ArrayList<>(memory.lastShownProductIds);
            }
            if (ids.isEmpty()) {
                return new AgentReply("\u8bf7\u63d0\u4f9b\u5546\u54c1ID\u5217\u8868\uff0c\u4f8b\u5982\uff1a\u628a 101,102,103 \u4e00\u8d77\u52a0\u5165\u8d2d\u7269\u8f66", List.of());
            }
            int qty = Math.max(1, extractQuantity(prompt));
            List<ProductVO> added = new ArrayList<>();
            int success = 0;
            for (Long id : ids) {
                if (id == null) {
                    continue;
                }
                try {
                    shopCartService.addToCart(userId, id, qty);
                    ProductVO p = productCache.get(id);
                    if (p != null) {
                        added.add(p);
                    }
                    success++;
                } catch (Exception ignored) {
                }
            }
            if (success == 0) {
                return new AgentReply("\u6279\u91cf\u52a0\u8d2d\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u5546\u54c1ID\u662f\u5426\u6709\u6548\u3002", List.of());
            }
            return new AgentReply("\u5df2\u6279\u91cf\u52a0\u5165\u8d2d\u7269\u8f66\uff0c\u5171 " + success + " \u4ef6\u5546\u54c1\uff0c\u6bcf\u4ef6\u6570\u91cf " + qty + "\u3002", added.stream().limit(4).toList());
        }
        return null;
    }
    private List<ProductVO> lastShownProducts(ConversationMemory memory) {
        if (memory.lastShownProductIds == null || memory.lastShownProductIds.isEmpty()) {
            return List.of();
        }
        List<ProductVO> result = new ArrayList<>();
        for (Long id : memory.lastShownProductIds) {
            ProductVO p = productCache.get(id);
            if (p != null) {
                result.add(p);
            }
        }
        return result;
    }

    private List<OrderItemRequest> buildOrderItemsFromPromptOrCart(Long userId, String prompt) {
        List<OrderItemRequest> items = new ArrayList<>();
        List<Long> promptIds = extractProductIds(prompt);
        int qty = Math.max(1, extractQuantity(prompt));
        if (!promptIds.isEmpty()) {
            for (Long id : promptIds) {
                OrderItemRequest item = new OrderItemRequest();
                item.setProductId(id);
                item.setQuantity(qty);
                items.add(item);
            }
            return items;
        }
        String lowered = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        if (containsAny(lowered, "\u524d\u4e24\u4e2a", "\u524d2\u4e2a", "\u524d\u4e24\u4ef6", "first two")) {
            List<ProductVO> shown = lastShownProducts(memoryFor(userId));
            int take = Math.min(2, shown.size());
            for (int i = 0; i < take; i++) {
                ProductVO p = shown.get(i);
                if (p == null || p.getId() == null) {
                    continue;
                }
                OrderItemRequest item = new OrderItemRequest();
                item.setProductId(p.getId());
                item.setQuantity(qty);
                items.add(item);
            }
            if (!items.isEmpty()) {
                return items;
            }
        }

        List<CartItemVO> cart = shopCartService.listCart(userId);
        if (cart == null || cart.isEmpty()) {
            return List.of();
        }
        for (CartItemVO c : cart) {
            if (c.getProductId() == null) {
                continue;
            }
            OrderItemRequest item = new OrderItemRequest();
            item.setCartId(c.getId());
            item.setProductId(c.getProductId());
            item.setQuantity(c.getQuantity() == null ? 1 : Math.max(1, c.getQuantity()));
            items.add(item);
        }
        return items;
    }

    private ShippingInfo extractShippingInfo(String prompt) {
        String name = extractGroup(SHIP_NAME_PATTERN, prompt);
        String phone = extractGroup(SHIP_PHONE_PATTERN, prompt);
        String address = extractGroup(SHIP_ADDRESS_PATTERN, prompt);
        return new ShippingInfo(name, phone, address);
    }

    private String extractGroup(Pattern pattern, String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return "";
        }
        return nvl(matcher.group(1)).trim();
    }

    private List<Long> extractProductIds(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return List.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        Matcher inlineMatcher = PRODUCT_IDS_INLINE_PATTERN.matcher(prompt);
        while (inlineMatcher.find()) {
            String raw = inlineMatcher.group(1);
            for (String part : PRODUCT_IDS_SEPARATOR_PATTERN.split(raw)) {
                try {
                    Long id = Long.parseLong(part.trim());
                    if (productCache.containsKey(id)) {
                        ids.add(id);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        Matcher matcher = PRODUCT_ID_PATTERN.matcher(prompt);
        while (matcher.find()) {
            try {
                Long id = Long.parseLong(matcher.group(1));
                if (productCache.containsKey(id)) {
                    ids.add(id);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return new ArrayList<>(ids);
    }

    private boolean isCheckoutIntent(String lowered) {
        return containsAny(lowered,
            "\u4e0b\u5355", "\u7ed3\u7b97", "\u63d0\u4ea4\u8ba2\u5355", "\u5e2e\u6211\u4e70", "\u7acb\u5373\u8d2d\u4e70", "checkout", "place order", "buy now");
    }

    private boolean isIncrementIntent(String lowered) {
        return containsAny(lowered,
            "\u591a\u4e70", "\u518d\u6765\u4e00\u4ef6", "\u518d\u52a0\u4e00\u4ef6", "\u52a0\u4e00\u4ef6", "\u518d\u4e70", "\u518d\u6765\u4e00\u4e2a",
            "\u518d\u6765\u4e24\u4e2a", "\u518d\u52a0\u4e24\u4e2a", "\u52a0\u4e24\u4e2a", "\u6765\u4e24\u4e2a", "\u7ed9\u6211\u52a0\u5165\u4e24\u4e2a",
            "\u6765\u4e00\u4ef6", "\u6765\u4e00\u4e2a", "\u5c31\u5b83\u4e86\u6765\u4e00\u4ef6",
            "another one", "one more", "add two", "two more");
    }

    private boolean isBatchAddIntent(String lowered) {
        return containsAny(lowered,
            "\u4e00\u8d77\u52a0\u5165\u8d2d\u7269\u8f66", "\u90fd\u52a0\u5165\u8d2d\u7269\u8f66", "\u6279\u91cf\u52a0\u5165\u8d2d\u7269\u8f66", "\u4e00\u8d77\u52a0\u8d2d", "\u4e00\u952e\u52a0\u8d2d",
            "add all to cart", "add these to cart", "batch add");
    }

    private boolean isCartQueryIntent(String lowered) {
        if (lowered == null || lowered.isBlank()) {
            return false;
        }
        // Action intents must not be misclassified as cart query.
        boolean hasAction = containsAny(lowered,
            "\u52a0\u5165", "\u52a0\u8d2d", "\u653e\u5165", "\u653e\u8fdb", "\u4e22\u8fdb", "\u585e\u8fdb",
            "\u79fb\u9664", "\u5220\u9664", "\u5220\u6389", "\u6e05\u7a7a",
            "\u4e0b\u5355", "\u7ed3\u7b97", "\u4ed8\u6b3e",
            "add", "remove", "delete", "clear", "checkout", "order", "buy");
        if (hasAction) {
            return false;
        }
        boolean hasCartWord = containsAny(lowered,
            "\u8d2d\u7269\u8f66", "\u6211\u7684\u8f66", "\u8f66\u91cc", "cart", "shopping cart");
        boolean queryTone = containsAny(lowered,
            "\u6709\u4ec0\u4e48", "\u6709\u54ea\u4e9b", "\u770b\u770b", "\u67e5\u770b", "\u5c55\u793a", "\u5185\u5bb9", "\u6e05\u5355", "\u6709\u5565",
            "\u8fd8\u6709\u4ec0\u4e48", "\u5728\u5417", "\u662f\u4ec0\u4e48",
            "what", "show", "list", "in it", "what's in");
        boolean questionMark = lowered.contains("?") || lowered.contains("\uff1f");
        boolean questionParticle = containsAny(lowered, "\u5417", "\u4e48", "\u5462");
        boolean explicitQuery = queryTone || questionMark || questionParticle;
        return hasCartWord && (explicitQuery || lowered.trim().equals("\u8d2d\u7269\u8f66") || lowered.trim().equals("cart"));
    }
    private String handleAddToCartIntent(String prompt, Long userId, List<ProductVO> candidates) {
        String normalized = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        boolean askAddCart = isAddToCartIntent(normalized);

        if (!askAddCart) {
            return null;
        }

        if (userId == null) {
            return "\u68c0\u6d4b\u5230\u52a0\u8d2d\u610f\u56fe\uff0c\u4f46\u4f60\u8fd8\u6ca1\u6709\u767b\u5f55\u3002";
        }

        ProductVO target = resolveTargetProduct(prompt, candidates);
        if (target == null || target.getId() == null) {
            return "\u8bf7\u63d0\u4f9b\u5546\u54c1ID\uff0c\u6216\u8005\u66f4\u5177\u4f53\u7684\u5546\u54c1\u540d\u79f0\u3002";
        }

        int quantity = extractQuantity(prompt);
        CartItemVO cartItem = shopCartService.addToCart(userId, target.getId(), quantity);
        int finalQty = cartItem == null || cartItem.getQuantity() == null ? quantity : cartItem.getQuantity();
        return "\u5df2\u52a0\u5165\u8d2d\u7269\u8f66\uff1a" + target.getName() + " x " + quantity + "\uff0c\u5f53\u524d\u8d2d\u7269\u8f66\u6570\u91cf\uff1a" + finalQty;
    }
    private ProductVO resolveTargetProduct(String prompt, List<ProductVO> candidates) {
        if (prompt == null) {
            return candidates == null || candidates.isEmpty() ? null : candidates.get(0);
        }

        List<ProductVO> pool = candidates == null ? List.of() : candidates;
        Matcher idMatcher = PRODUCT_ID_PATTERN.matcher(prompt);
        if (idMatcher.find()) {
            Long id = Long.parseLong(idMatcher.group(1));
            ProductVO byId = productCache.get(id);
            if (byId != null) {
                return byId;
            }
            return shopProductService.getProductDetail(id);
        }

        String lowered = prompt.toLowerCase(Locale.ROOT);
        if (!pool.isEmpty() && containsAny(lowered, "\u4e0d\u8981\u7b2c\u4e00\u4e2a", "\u6362\u6210\u7b2c\u4e8c\u4e2a", "\u6539\u7b2c\u4e8c\u4e2a")) {
            if (pool.size() >= 2) {
                return pool.get(1);
            }
        }
        if (!pool.isEmpty() && containsAny(lowered, "\u4e0d\u8981\u7b2c\u4e8c\u4e2a", "\u6362\u6210\u7b2c\u4e00\u4e2a", "\u6539\u7b2c\u4e00\u4e2a")) {
            return pool.get(0);
        }
        if (!pool.isEmpty() && hasOtherScopeCue(lowered)) {
            if (pool.size() >= 2) {
                return pool.get(1);
            }
            return pool.get(0);
        }
        int ordinal = extractOrdinalIndex(lowered);
        if (ordinal > 0 && !pool.isEmpty()) {
            int idx = Math.min(pool.size(), ordinal) - 1;
            return pool.get(idx);
        }
        if (!pool.isEmpty() && containsAny(lowered, "\u6700\u4fbf\u5b9c", "\u4ef7\u683c\u6700\u4f4e", "cheapest", "lowest price")) {
            return pool.stream()
                .filter(p -> p != null && p.getPrice() != null)
                .min(Comparator.comparing(ProductVO::getPrice))
                .orElse(pool.get(0));
        }
        if (!pool.isEmpty() && containsAny(lowered, "\u6700\u706b\u7206", "\u6700\u70ed\u95e8", "\u9500\u91cf\u6700\u9ad8", "hottest", "best seller", "most popular")) {
            return pool.stream()
                .max(Comparator.comparing((ProductVO p) -> p.getSales() == null ? 0 : p.getSales()))
                .orElse(pool.get(0));
        }
        if (!pool.isEmpty() && containsAny(lowered, "\u8bc4\u5206\u6700\u9ad8", "\u6700\u597d", "best rated", "top rated")) {
            return pool.stream()
                .max(Comparator
                    .comparing((ProductVO p) -> p.getRating() == null ? BigDecimal.ZERO : p.getRating())
                    .thenComparing(p -> p.getSales() == null ? 0 : p.getSales()))
                .orElse(pool.get(0));
        }

        for (ProductVO p : pool) {
            if (p.getName() != null && lowered.contains(p.getName().toLowerCase(Locale.ROOT))) {
                return p;
            }
        }
        if (pool.isEmpty()) {
            for (ProductVO p : productCache.values()) {
                if (p.getName() != null && lowered.contains(p.getName().toLowerCase(Locale.ROOT))) {
                    return p;
                }
            }
            return null;
        }
        return pool.get(0);
    }

    private boolean isAddToCartIntent(String lowered) {
        if (lowered == null || lowered.isBlank()) {
            return false;
        }
        return lowered.contains("add to cart")
            || lowered.contains("add it")
            || lowered.contains("put into cart")
            || lowered.contains("put in cart")
            || lowered.contains("add this")
            || lowered.contains(ZH_ADD_CART)
            || containsAny(
                lowered,
                "\u52a0\u8d2d", "\u653e\u5165\u8d2d\u7269\u8f66", "\u653e\u5230\u8d2d\u7269\u8f66", "\u5e2e\u6211\u52a0\u8d2d",
                "\u52a0\u5165", "\u7ed9\u6211\u52a0\u5165", "\u52a0\u4e24\u4e2a", "\u52a0\u4e00\u4e2a", "\u6765\u4e24\u4e2a", "\u518d\u6765\u4e24\u4e2a",
                "\u52a0\u8fdb\u53bb", "\u52a0\u5230\u91cc\u9762", "\u653e\u8fdb\u53bb", "\u7ed9\u6211\u52a0\u8fdb\u53bb",
                "\u653e\u8fdb\u8d2d\u7269\u8f66", "\u653e\u5230\u8d2d\u7269\u8f66\u91cc", "\u653e\u5165\u8f66", "\u4e22\u8fdb\u8d2d\u7269\u8f66", "\u585e\u8fdb\u8d2d\u7269\u8f66",
                "\u6536\u8fdb\u8d2d\u7269\u8f66", "\u6dfb\u52a0\u5230\u8d2d\u7269\u8f66", "\u7ed9\u6211\u653e\u8fdb\u8d2d\u7269\u8f66", "\u653e\u4e00\u4e0b\u8d2d\u7269\u8f66",
                "\u628a\u4ed6\u52a0\u5165\u8d2d\u7269\u8f66", "\u628a\u5b83\u52a0\u5165\u8d2d\u7269\u8f66", "\u628a\u8fd9\u4e2a\u52a0\u5165\u8d2d\u7269\u8f66", "\u628a\u7b2c\u4e00\u4e2a\u52a0\u5165\u8d2d\u7269\u8f66",
                "\u628a\u6700\u4fbf\u5b9c\u7684\u52a0\u5165\u8d2d\u7269\u8f66", "\u628a\u6700\u706b\u7206\u7684\u52a0\u5165\u8d2d\u7269\u8f66"
            );
    }

    private ProductVO resolveTargetByLastAttributeMemory(String prompt, ConversationMemory memory) {
        if (prompt == null || memory == null || memory.lastAttributeSourceIds == null || memory.lastAttributeSourceIds.isEmpty()) {
            return null;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        String key = memory.lastAttributeKey == null ? "" : memory.lastAttributeKey.trim();
        if (key.isBlank()) {
            return null;
        }
        boolean mentionRating = containsAny(lowered, "\u8bc4\u5206", "\u53e3\u7891", "rating", "rated");
        boolean mentionSales = containsAny(lowered, "\u9500\u91cf", "\u70ed\u5ea6", "\u7206\u6b3e", "\u70ed\u95e8", "sales", "sold", "popular");
        boolean mentionPrice = containsAny(lowered, "\u4ef7\u683c", "\u4fbf\u5b9c", "\u8d35", "\u591a\u5c11\u94b1", "price", "cheap", "expensive", "cheaper");
        boolean mentionStock = containsAny(lowered, "\u5e93\u5b58", "\u6709\u8d27", "\u73b0\u8d27", "stock", "inventory");
        boolean mentionsOtherMetric = ("rating".equals(key) && (mentionSales || mentionPrice || mentionStock))
            || ("sales".equals(key) && (mentionRating || mentionPrice || mentionStock))
            || ("price".equals(key) && (mentionRating || mentionSales || mentionStock))
            || ("stock".equals(key) && (mentionRating || mentionSales || mentionPrice));
        if (mentionsOtherMetric) {
            return null;
        }

        boolean dirHigh = containsAny(lowered,
            "\u6700\u9ad8", "\u6700\u5927", "\u6700\u591a", "\u6700\u597d", "\u66f4\u9ad8", "\u66f4\u591a", "\u66f4\u597d",
            "\u6700\u70ed\u95e8", "\u6700\u706b\u7206", "\u6700\u8d35",
            "highest", "largest", "most", "best", "higher", "more", "most expensive");
        boolean dirLow = containsAny(lowered,
            "\u6700\u4f4e", "\u6700\u5c11", "\u6700\u5dee", "\u66f4\u4f4e", "\u66f4\u5c11", "\u66f4\u4fbf\u5b9c", "\u4fbf\u5b9c\u4e00\u70b9",
            "\u6700\u4fbf\u5b9c", "\u6700\u7701\u94b1",
            "lowest", "least", "worst", "lower", "less", "cheaper", "cheapest");
        if (!dirHigh && !dirLow) {
            return null;
        }
        if (dirHigh && dirLow) {
            if (containsAny(lowered, "\u4fbf\u5b9c", "cheap", "cheaper", "cheapest")) {
                dirHigh = false;
            } else if (containsAny(lowered, "\u8d35", "expensive")) {
                dirLow = false;
            }
        }

        List<ProductVO> pool = new ArrayList<>();
        for (Long id : memory.lastAttributeSourceIds) {
            if (id == null) {
                continue;
            }
            ProductVO p = productCache.get(id);
            if (p != null) {
                pool.add(p);
            }
            if (pool.size() >= 8) {
                break;
            }
        }
        if (pool.isEmpty()) {
            return null;
        }

        if ("rating".equals(key)) {
            return dirLow
                ? pool.stream().min(Comparator.comparing((ProductVO p) -> p.getRating() == null ? BigDecimal.ZERO : p.getRating())).orElse(pool.get(0))
                : pool.stream().max(Comparator.comparing((ProductVO p) -> p.getRating() == null ? BigDecimal.ZERO : p.getRating())).orElse(pool.get(0));
        }
        if ("sales".equals(key)) {
            return dirLow
                ? pool.stream().min(Comparator.comparing((ProductVO p) -> p.getSales() == null ? 0 : p.getSales())).orElse(pool.get(0))
                : pool.stream().max(Comparator.comparing((ProductVO p) -> p.getSales() == null ? 0 : p.getSales())).orElse(pool.get(0));
        }
        if ("stock".equals(key)) {
            return dirLow
                ? pool.stream().min(Comparator.comparing((ProductVO p) -> p.getStock() == null ? 0 : p.getStock())).orElse(pool.get(0))
                : pool.stream().max(Comparator.comparing((ProductVO p) -> p.getStock() == null ? 0 : p.getStock())).orElse(pool.get(0));
        }
        if ("price".equals(key)) {
            boolean wantHighPrice = containsAny(lowered, "\u6700\u8d35", "most expensive", "highest price") || (dirHigh && !containsAny(lowered, "\u4fbf\u5b9c", "cheap", "cheaper", "cheapest"));
            return wantHighPrice
                ? pool.stream().max(Comparator.comparing((ProductVO p) -> p.getPrice() == null ? BigDecimal.ZERO : p.getPrice())).orElse(pool.get(0))
                : pool.stream().min(Comparator.comparing((ProductVO p) -> p.getPrice() == null ? BigDecimal.valueOf(Double.MAX_VALUE) : p.getPrice())).orElse(pool.get(0));
        }
        return null;
    }

    private AgentReply maybeAskLowConfidenceClarify(String prompt, IntentType intent, List<ProductVO> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        List<ProductVO> head = candidates.stream().limit(Math.min(3, candidates.size())).toList();
        if (intent != IntentType.NONE) {
            List<String> anchors = buildGeneralAnchorTerms(prompt, intent);
            if (!anchors.isEmpty()) {
                int sum = 0;
                for (ProductVO p : head) {
                    sum += topicAnchorScore(p, anchors);
                }
                double avg = sum / (double) head.size();
                if (avg < 0.8d) {
                    String hint = topicHintByIntent(intent);
                    String sample = hint == null || hint.isBlank() ? "category + budget + use-case" : hint;
                    return new AgentReply(
                        "\u6682\u65f6\u6ca1\u627e\u5230\u9ad8\u7f6e\u4fe1\u5ea6\u7684\u540c\u7c7b\u5546\u54c1\uff0c\u8bf7\u518d\u5177\u4f53\u4e00\u70b9\uff0c\u4f8b\u5982\uff1a" + sample + "\u3002",
                        List.of()
                    );
                }
            }
        }
        if (intent == IntentType.MOUSE) {
            long good = head.stream().filter(this::isStrictMouseProduct).count();
            if (good == 0) {
                return new AgentReply("\u6211\u5148\u786e\u8ba4\u4e00\u4e0b\uff0c\u4f60\u8981\u7684\u662f\u9f20\u6807\u4e3b\u54c1\u8fd8\u662f\u9f20\u6807\u578b\u914d\u4ef6\uff1f", List.of());
            }
        } else if (intent == IntentType.KEYBOARD) {
            long good = head.stream().filter(this::isStrictKeyboardProduct).count();
            if (good == 0) {
                return new AgentReply("\u6211\u5148\u786e\u8ba4\u4e00\u4e0b\uff0c\u4f60\u8981\u7684\u662f\u952e\u76d8\u4e3b\u54c1\u8fd8\u662f\u952e\u76d8\u578b\u914d\u4ef6\uff1f", List.of());
            }
        } else if (intent == IntentType.HEADPHONE) {
            long good = head.stream().filter(this::isStrictHeadphoneProduct).count();
            if (good == 0) {
                return new AgentReply("\u6211\u5148\u786e\u8ba4\u4e00\u4e0b\uff0c\u4f60\u8981\u7684\u662f\u8033\u673a\u4e3b\u54c1\u8fd8\u662f\u8033\u673a\u914d\u4ef6\uff1f", List.of());
            }
        }
        return null;
    }

    private List<ProductVO> enforceStrictIntentCandidates(IntentType intent, List<ProductVO> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (intent == IntentType.MOUSE) {
            List<ProductVO> strict = candidates.stream().filter(this::isStrictMouseProduct).toList();
            return strict.isEmpty() ? List.of() : strict;
        }
        if (intent == IntentType.KEYBOARD) {
            List<ProductVO> strict = candidates.stream().filter(this::isStrictKeyboardProduct).toList();
            return strict.isEmpty() ? List.of() : strict;
        }
        if (intent == IntentType.HEADPHONE) {
            List<ProductVO> strict = candidates.stream().filter(this::isStrictHeadphoneProduct).toList();
            return strict.isEmpty() ? List.of() : strict;
        }
        return candidates;
    }

    private List<ProductVO> resolveTopNTargetsByLastAttributeMemory(String prompt, ConversationMemory memory) {
        if (prompt == null || memory == null || memory.lastAttributeSourceIds == null || memory.lastAttributeSourceIds.isEmpty()) {
            return List.of();
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        if (!containsAny(lowered, "\u4e24\u4e2a", "\u4e24\u4ef6", "\u4e24\u6b3e", "2\u4e2a", "2\u4ef6", "top 2", "two")) {
            return List.of();
        }
        String key = nvl(memory.lastAttributeKey).trim();
        if (key.isBlank()) {
            return List.of();
        }
        boolean mentionRating = containsAny(lowered, "\u8bc4\u5206", "\u53e3\u7891", "rating", "rated");
        boolean mentionSales = containsAny(lowered, "\u9500\u91cf", "\u70ed\u5ea6", "\u7206\u6b3e", "\u70ed\u95e8", "sales", "sold", "popular");
        boolean mentionPrice = containsAny(lowered, "\u4ef7\u683c", "\u4fbf\u5b9c", "\u8d35", "\u591a\u5c11\u94b1", "price", "cheap", "expensive", "cheaper");
        boolean mentionStock = containsAny(lowered, "\u5e93\u5b58", "\u6709\u8d27", "\u73b0\u8d27", "stock", "inventory");
        boolean mentionsOtherMetric = ("rating".equals(key) && (mentionSales || mentionPrice || mentionStock))
            || ("sales".equals(key) && (mentionRating || mentionPrice || mentionStock))
            || ("price".equals(key) && (mentionRating || mentionSales || mentionStock))
            || ("stock".equals(key) && (mentionRating || mentionSales || mentionPrice));
        if (mentionsOtherMetric) {
            return List.of();
        }

        List<ProductVO> pool = new ArrayList<>();
        for (Long id : memory.lastAttributeSourceIds) {
            if (id == null) continue;
            ProductVO p = productCache.get(id);
            if (p != null) {
                pool.add(p);
            }
            if (pool.size() >= 8) break;
        }
        if (pool.size() < 2) {
            return List.of();
        }
        return pool.stream().limit(2).toList();
    }

    private List<ProductVO> resolveTopNTargetsByPrompt(String prompt, ConversationMemory memory, List<ProductVO> scope) {
        if (prompt == null || prompt.isBlank() || scope == null || scope.isEmpty()) {
            return List.of();
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        int targetCount = extractTargetItemCount(lowered);
        if (targetCount < 2) {
            return List.of();
        }
        String metric = detectMetricToken(lowered, memory == null ? null : memory.lastAttributeKey);
        if (metric == null || metric.isBlank()) {
            return List.of();
        }
        boolean pickLow = containsAny(lowered,
            "\u6700\u4f4e", "\u6700\u5c11", "\u6700\u5dee", "\u66f4\u4f4e", "\u66f4\u5c11", "\u66f4\u4fbf\u5b9c", "\u6700\u4fbf\u5b9c", "lowest", "least", "worst", "cheapest", "cheaper");
        boolean pickHigh = containsAny(lowered,
            "\u6700\u9ad8", "\u6700\u591a", "\u6700\u597d", "\u6700\u706b", "\u6700\u70ed", "\u6700\u8d35", "highest", "most", "best", "hottest", "most expensive");

        Comparator<ProductVO> cmp = comparatorByMetric(metric, pickLow && !pickHigh);
        if (cmp == null) {
            return List.of();
        }
        List<ProductVO> sorted = new ArrayList<>(scope);
        sorted.sort(cmp);
        return sorted.stream().limit(Math.min(10, targetCount)).toList();
    }

    private List<ProductVO> resolveSetTargetsByPrompt(String prompt, ConversationMemory memory, List<ProductVO> scope) {
        if (prompt == null || prompt.isBlank()) {
            return List.of();
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        List<ProductVO> pool = new ArrayList<>(scope == null ? List.of() : scope);
        if (pool.isEmpty() && memory != null && memory.lastAttributeSourceIds != null) {
            for (Long id : memory.lastAttributeSourceIds) {
                if (id == null) {
                    continue;
                }
                ProductVO p = productCache.get(id);
                if (p != null) {
                    pool.add(p);
                }
                if (pool.size() >= 10) {
                    break;
                }
            }
        }
        if (pool.size() < 2) {
            return List.of();
        }

        boolean hasExcept = containsAny(lowered, "\u9664\u4e86", "\u9664\u53bb", "\u9664\u5f00", "\u6392\u9664", "\u4e0d\u8981", "\u4e0d\u542b", "\u5254\u9664", "\u975e");
        boolean hasAll = containsAny(lowered, "\u5176\u4ed6\u90fd", "\u5176\u4f59\u90fd", "\u5269\u4e0b\u7684\u90fd", "\u5168\u90e8", "\u5168\u90fd", "\u90fd\u52a0\u5165", "\u90fd\u653e\u8fdb", "all", "the rest");
        if (hasExcept && hasAll) {
            ProductVO excluded = resolveExcludedTargetFromPool(prompt, pool);
            if (excluded == null || excluded.getId() == null) {
                return List.of();
            }
            return pool.stream()
                .filter(p -> p != null && p.getId() != null && !Objects.equals(p.getId(), excluded.getId()))
                .toList();
        }

        List<ProductVO> logicTargets = resolveLogicalTargetsFromPool(lowered, pool);
        if (!logicTargets.isEmpty()) {
            return logicTargets;
        }

        return List.of();
    }

    private ProductVO resolveExcludedTargetFromPool(String prompt, List<ProductVO> pool) {
        if (pool == null || pool.isEmpty()) {
            return null;
        }
        String lowered = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        int ordinal = extractOrdinalIndex(lowered);
        if (ordinal > 0 && ordinal <= pool.size()) {
            return pool.get(ordinal - 1);
        }
        Matcher idMatcher = PRODUCT_ID_PATTERN.matcher(prompt == null ? "" : prompt);
        if (idMatcher.find()) {
            try {
                Long id = Long.parseLong(idMatcher.group(1));
                for (ProductVO p : pool) {
                    if (p != null && Objects.equals(p.getId(), id)) {
                        return p;
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }

        boolean low = containsAny(lowered, "\u6700\u4f4e", "\u6700\u5c11", "\u6700\u4fbf\u5b9c", "\u6700\u5dee", "lowest", "least", "cheapest", "worst");
        boolean high = containsAny(lowered, "\u6700\u9ad8", "\u6700\u591a", "\u6700\u8d35", "\u6700\u597d", "highest", "most", "best", "most expensive");
        String metric = detectMetricToken(lowered, "");
        if (!metric.isBlank() && (low || high)) {
            Comparator<ProductVO> cmp = comparatorByMetric(metric, low && !high);
            if (cmp != null) {
                List<ProductVO> sorted = new ArrayList<>(pool);
                sorted.sort(cmp);
                return sorted.get(0);
            }
        }

        for (ProductVO p : pool) {
            if (p != null && p.getName() != null && !p.getName().isBlank() && lowered.contains(p.getName().toLowerCase(Locale.ROOT))) {
                return p;
            }
        }
        return null;
    }

    private List<ProductVO> resolveLogicalTargetsFromPool(String lowered, List<ProductVO> pool) {
        if (lowered == null || lowered.isBlank() || pool == null || pool.size() < 2) {
            return List.of();
        }
        boolean hasAnd = containsAny(lowered, "\u4e14", "\u5e76\u4e14", "\u540c\u65f6", " and ");
        boolean hasOr = containsAny(lowered, "\u6216", "\u6216\u8005", " or ");
        if (!hasAnd && !hasOr) {
            return List.of();
        }
        List<MetricCondition> conditions = extractMetricConditions(lowered);
        if (conditions.size() < 2) {
            return List.of();
        }

        Set<Long> selectedIds = new LinkedHashSet<>();
        boolean initialized = false;
        for (MetricCondition c : conditions) {
            List<ProductVO> bucket = selectMetricGroup(pool, c.metric(), c.preferLow());
            Set<Long> ids = new LinkedHashSet<>();
            for (ProductVO p : bucket) {
                if (p != null && p.getId() != null) {
                    ids.add(p.getId());
                }
            }
            if (!initialized) {
                selectedIds.addAll(ids);
                initialized = true;
                continue;
            }
            if (hasAnd && !hasOr) {
                selectedIds.retainAll(ids);
            } else {
                selectedIds.addAll(ids);
            }
        }

        if (selectedIds.isEmpty()) {
            List<ProductVO> fallback = selectMetricGroup(pool, conditions.get(0).metric(), conditions.get(0).preferLow());
            if (!fallback.isEmpty()) {
                return fallback.stream().limit(Math.min(2, fallback.size())).toList();
            }
            return List.of();
        }

        List<ProductVO> out = new ArrayList<>();
        for (ProductVO p : pool) {
            if (p != null && p.getId() != null && selectedIds.contains(p.getId())) {
                out.add(p);
            }
        }
        return out;
    }

    private List<MetricCondition> extractMetricConditions(String lowered) {
        List<MetricCondition> conditions = new ArrayList<>();
        boolean lowHint = containsAny(lowered, "\u4f4e", "\u5c11", "\u4fbf\u5b9c", "\u6700\u4f4e", "\u6700\u5c11", "low", "least", "cheap", "cheaper", "cheapest");
        boolean highHint = containsAny(lowered, "\u9ad8", "\u591a", "\u8d35", "\u6700\u9ad8", "\u6700\u591a", "high", "highest", "most", "best", "expensive");

        if (containsAny(lowered, "\u8bc4\u5206", "\u53e3\u7891", "rating", "rated")) {
            boolean preferLow = containsAny(lowered, "\u8bc4\u5206\u4f4e", "\u6700\u4f4e\u8bc4\u5206", "lowest rating");
            conditions.add(new MetricCondition("rating", preferLow));
        }
        if (containsAny(lowered, "\u4ef7\u683c", "\u4fbf\u5b9c", "\u8d35", "\u591a\u5c11\u94b1", "price", "cheap", "expensive")) {
            boolean preferLow = containsAny(lowered, "\u4fbf\u5b9c", "\u4ef7\u4f4e", "\u6700\u4f4e\u4ef7", "cheap", "cheaper", "cheapest", "low price")
                || (lowHint && !highHint);
            conditions.add(new MetricCondition("price", preferLow));
        }
        if (containsAny(lowered, "\u9500\u91cf", "\u70ed\u5ea6", "\u7206\u6b3e", "\u70ed\u95e8", "sales", "sold", "popular")) {
            boolean preferLow = containsAny(lowered, "\u9500\u91cf\u4f4e", "\u6700\u4f4e\u9500\u91cf", "lowest sales");
            conditions.add(new MetricCondition("sales", preferLow));
        }
        if (containsAny(lowered, "\u5e93\u5b58", "\u6709\u8d27", "\u73b0\u8d27", "stock", "inventory")) {
            boolean preferLow = containsAny(lowered, "\u5e93\u5b58\u5c11", "\u6700\u5c11\u5e93\u5b58", "\u5e93\u5b58\u4f4e", "least stock", "lowest stock");
            conditions.add(new MetricCondition("stock", preferLow));
        }
        return conditions;
    }

    private List<ProductVO> selectMetricGroup(List<ProductVO> pool, String metric, boolean preferLow) {
        Comparator<ProductVO> cmp = comparatorByMetric(metric, preferLow);
        if (cmp == null || pool == null || pool.isEmpty()) {
            return List.of();
        }
        List<ProductVO> sorted = new ArrayList<>(pool);
        sorted.sort(cmp);
        int keep = Math.max(1, (int) Math.ceil(sorted.size() / 2.0));
        List<ProductVO> out = new ArrayList<>(sorted.subList(0, keep));
        ProductVO boundary = sorted.get(keep - 1);
        for (int i = keep; i < sorted.size(); i++) {
            ProductVO p = sorted.get(i);
            if (sameMetricValue(p, boundary, metric)) {
                out.add(p);
            } else {
                break;
            }
        }
        return out;
    }

    private String detectMetricToken(String lowered, String fallback) {
        if (lowered == null) {
            return fallback == null ? "" : fallback;
        }
        if (containsAny(lowered, "\u8bc4\u5206", "\u53e3\u7891", "rating", "rated")) return "rating";
        if (containsAny(lowered, "\u9500\u91cf", "\u70ed\u5ea6", "\u7206\u6b3e", "\u70ed\u95e8", "sales", "sold", "popular")) return "sales";
        if (containsAny(lowered, "\u4ef7\u683c", "\u4fbf\u5b9c", "\u8d35", "\u591a\u5c11\u94b1", "price", "cheap", "expensive", "cheaper")) return "price";
        if (containsAny(lowered, "\u5e93\u5b58", "\u6709\u8d27", "\u73b0\u8d27", "stock", "inventory")) return "stock";
        return fallback == null ? "" : fallback;
    }

    private Comparator<ProductVO> comparatorByMetric(String metric, boolean asc) {
        Comparator<ProductVO> cmp;
        switch (metric) {
            case "rating" -> cmp = Comparator.comparing((ProductVO p) -> p.getRating() == null ? BigDecimal.ZERO : p.getRating());
            case "sales" -> cmp = Comparator.comparing((ProductVO p) -> p.getSales() == null ? 0 : p.getSales());
            case "stock" -> cmp = Comparator.comparing((ProductVO p) -> p.getStock() == null ? 0 : p.getStock());
            case "price" -> cmp = Comparator.comparing((ProductVO p) -> p.getPrice() == null ? BigDecimal.valueOf(Double.MAX_VALUE) : p.getPrice());
            default -> {
                return null;
            }
        }
        return asc ? cmp : cmp.reversed();
    }

    private int extractTargetItemCount(String lowered) {
        if (lowered == null || lowered.isBlank()) {
            return 0;
        }
        if (containsAny(lowered, "\u524d\u4e24\u4e2a", "\u4e24\u4e2a", "\u4e24\u4ef6", "\u4e24\u6b3e", "2\u4e2a", "2\u4ef6", "two", "top 2")) return 2;
        if (containsAny(lowered, "\u4e09\u4e2a", "\u4e09\u4ef6", "\u4e09\u6b3e", "3\u4e2a", "3\u4ef6", "three", "top 3")) return 3;
        if (containsAny(lowered, "\u56db\u4e2a", "\u56db\u4ef6", "\u56db\u6b3e", "4\u4e2a", "4\u4ef6", "four", "top 4")) return 4;
        return 0;
    }

    private int extractPerItemQuantity(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return 1;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        if (containsAny(lowered, "\u6bcf\u4e2a", "\u6bcf\u4ef6", "\u90fd\u52a0", "\u90fd\u6765", "\u5404", "each", "per item")) {
            return Math.max(1, extractQuantity(prompt));
        }
        return 1;
    }

    private int extractLeadingQuantityBeforeMetric(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return 0;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        if (containsAny(lowered, "\u90fd", "\u5206\u522b", "each", "per item")) {
            return 0;
        }
        int metricIdx = firstMetricIndex(lowered);
        if (metricIdx <= 0) {
            return 0;
        }
        Matcher m = Pattern.compile("(\\d{1,2}|\u4e24|\u4fe9|\u4e8c|\u4e09|\u56db|\u4e94|\u516d|\u4e03|\u516b|\u4e5d|\u5341)\\s*(?:\u4e2a|\u4ef6|\u6b3e|\u53f0|\u53cc|\u53ea|\u4efd)?").matcher(lowered);
        while (m.find()) {
            if (m.start() >= metricIdx) {
                continue;
            }
            if (metricIdx - m.start() > 12) {
                continue;
            }
            String token = m.group(1);
            int n = parseSmallCountToken(token);
            if (n > 1) {
                return n;
            }
        }
        return 0;
    }

    private int firstMetricIndex(String lowered) {
        int idx = Integer.MAX_VALUE;
        String[] markers = new String[] {
            "\u8bc4\u5206", "\u53e3\u7891", "\u9500\u91cf", "\u70ed\u5ea6", "\u7206\u6b3e", "\u70ed\u95e8", "\u4ef7\u683c", "\u4fbf\u5b9c", "\u8d35", "\u5e93\u5b58",
            "rating", "sales", "popular", "price", "stock", "inventory", "cheapest", "lowest", "highest", "best"
        };
        for (String m : markers) {
            int p = lowered.indexOf(m);
            if (p >= 0 && p < idx) {
                idx = p;
            }
        }
        return idx == Integer.MAX_VALUE ? -1 : idx;
    }

    private int parseSmallCountToken(String token) {
        if (token == null || token.isBlank()) {
            return 0;
        }
        if (token.matches("\\d{1,2}")) {
            try {
                return Math.max(0, Math.min(99, Integer.parseInt(token)));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return switch (token) {
            case "\u4e24", "\u4fe9", "\u4e8c" -> 2;
            case "\u4e09" -> 3;
            case "\u56db" -> 4;
            case "\u4e94" -> 5;
            case "\u516d" -> 6;
            case "\u4e03" -> 7;
            case "\u516b" -> 8;
            case "\u4e5d" -> 9;
            case "\u5341" -> 10;
            default -> 0;
        };
    }

    private ProductVO toProductFromCartItem(CartItemVO item) {
        if (item == null) {
            return null;
        }
        if (item.getProductId() != null) {
            ProductVO cached = productCache.get(item.getProductId());
            if (cached != null) {
                return cached;
            }
        }
        ProductVO p = new ProductVO();
        p.setId(item.getProductId());
        p.setName(item.getName());
        p.setPrice(item.getPrice());
        p.setImage(item.getImage());
        p.setCategory(item.getCategory());
        p.setStock(item.getStock());
        p.setCurrency("USD");
        return p;
    }

    private int extractQuantity(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return 1;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);

        // 1) Explicit quantity labels should always win, e.g. "閺佷即鍣?2", "qty: 3".
        Matcher labeled = QUANTITY_LABELED_PATTERN.matcher(lowered);
        if (labeled.find()) {
            try {
                return clampQuantity(Integer.parseInt(labeled.group(1)));
            } catch (NumberFormatException ignored) {
            }
        }

        // 2) Multiplier style, e.g. "x 2".
        Matcher multiplier = MULTIPLIER_QTY_PATTERN.matcher(lowered);
        if (multiplier.find()) {
            try {
                return clampQuantity(Integer.parseInt(multiplier.group(1)));
            } catch (NumberFormatException ignored) {
            }
        }

        // 3) Unit count phrase, e.g. "2娑?2娴?.
        Matcher countMatcher = COUNT_PATTERN.matcher(lowered);
        if (countMatcher.find()) {
            try {
                return clampQuantity(Integer.parseInt(countMatcher.group(1)));
            } catch (NumberFormatException ignored) {
            }
        }

        // 4) Generic numeric fallback: avoid using product id as quantity.
        Long productId = null;
        Matcher idMatcher = PRODUCT_ID_PATTERN.matcher(prompt);
        if (idMatcher.find()) {
            try {
                productId = Long.parseLong(idMatcher.group(1));
            } catch (NumberFormatException ignored) {
                productId = null;
            }
        }
        Matcher matcher = NUMBER_PATTERN.matcher(prompt);
        while (matcher.find()) {
            try {
                int n = Integer.parseInt(matcher.group(1));
                if (productId != null && productId == n) {
                    continue;
                }
                return clampQuantity(n);
            } catch (NumberFormatException ignored) {
            }
        }

        // 5) Chinese/English small-number words.
        if (containsAny(lowered, "\u4e24", "\u4e24\u4e2a", "\u4e24\u4ef6", "\u4fe9", "two")) return 2;
        if (containsAny(lowered, "\u4e09", "\u4e09\u4e2a", "\u4e09\u4ef6", "three")) return 3;
        if (containsAny(lowered, "\u56db", "\u56db\u4e2a", "\u56db\u4ef6", "four")) return 4;
        if (containsAny(lowered, "\u4e94", "\u4e94\u4e2a", "\u4e94\u4ef6", "five")) return 5;
        if (containsAny(lowered, "\u4e00\u5bf9", "a pair")) return 2;
        return 1;
    }

    private int clampQuantity(int n) {
        try {
            return Math.max(1, Math.min(99, n));
        } catch (Exception ex) {
            return 1;
        }
    }

    private boolean isReplaceProductIntent(String lowered) {
        return containsAny(
            lowered,
            "\u6362\u6210", "\u6539\u6210", "\u66ff\u6362\u6210", "\u6362\u5546\u54c1", "\u6362\u4e00\u6b3e", "\u6362\u8fd9\u4e2a", "\u6362\u8fd9\u4ef6",
            "replace with", "switch to", "change to", "swap to"
        );
    }

    private boolean isRemoveCartIntent(String lowered) {
        return containsAny(lowered,
            "\u79fb\u9664", "\u5220\u9664", "\u5220\u6389", "\u4ece\u8d2d\u7269\u8f66\u5220\u6389", "\u4ece\u8f66\u91cc\u5220\u6389",
            "remove from cart", "delete from cart", "remove");
    }

    private boolean isClearCartIntent(String lowered) {
        return containsAny(lowered,
            "\u6e05\u7a7a\u8d2d\u7269\u8f66", "\u6e05\u7a7a\u8f66", "\u5168\u90e8\u5220\u6389", "\u8f66\u91cc\u5168\u5220",
            "clear cart", "empty cart");
    }

    private boolean isUndoLastAddIntent(String lowered) {
        return containsAny(lowered,
            "\u64a4\u9500\u521a\u624d\u52a0\u8d2d", "\u64a4\u56de\u521a\u624d\u90a3\u6b21\u52a0\u8d2d", "\u53d6\u6d88\u521a\u624d\u52a0\u5165", "\u56de\u9000\u521a\u624d\u52a0\u8d2d",
            "undo last add", "undo add", "revert add");
    }

    private boolean isPreviousBatchSwitchPrompt(String lowered) {
        return containsAny(lowered,
            "\u524d\u4e00\u6b21\u63a8\u8350", "\u524d\u6b21\u63a8\u8350", "\u4e0d\u662f\u8fd9\u4e00\u6b21", "\u6211\u8bf4\u7684\u662f\u524d\u4e00\u6b21", "\u5207\u56de\u4e0a\u4e00\u6279",
            "previous recommendation", "not this one", "the last batch");
    }

    private CartItemVO resolveCartItemForRemoval(String prompt, List<CartItemVO> cart) {
        if (cart == null || cart.isEmpty()) {
            return null;
        }
        String lowered = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        int ordinal = extractOrdinalIndex(lowered);
        if (ordinal > 0 && ordinal <= cart.size()) {
            return cart.get(ordinal - 1);
        }
        if (containsAny(lowered, "\u6700\u8d35", "\u4ef7\u683c\u6700\u9ad8", "most expensive")) {
            return cart.stream().filter(c -> c.getPrice() != null).max(Comparator.comparing(CartItemVO::getPrice)).orElse(cart.get(0));
        }
        if (containsAny(lowered, "\u6700\u4fbf\u5b9c", "\u4ef7\u683c\u6700\u4f4e", "cheapest")) {
            return cart.stream().filter(c -> c.getPrice() != null).min(Comparator.comparing(CartItemVO::getPrice)).orElse(cart.get(0));
        }
        if (containsAny(lowered, "\u6700\u540e\u4e00\u4e2a", "last one")) {
            return cart.get(cart.size() - 1);
        }
        return cart.get(0);
    }

    private CartItemVO resolveCartItemForReplacement(String prompt, List<CartItemVO> cart, ConversationMemory memory, ProductVO target) {
        if (cart == null || cart.isEmpty()) {
            return null;
        }
        String lowered = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        if (containsAny(lowered, "\u8d2d\u7269\u8f66", "\u8f66\u91cc", "cart", "\u7b2c")) {
            CartItemVO explicit = resolveCartItemForRemoval(prompt, cart);
            if (explicit != null) {
                return explicit;
            }
        }
        if (memory != null && memory.lastCartAddProductId != null) {
            for (CartItemVO c : cart) {
                if (c != null && Objects.equals(c.getProductId(), memory.lastCartAddProductId)) {
                    return c;
                }
            }
        }
        if (memory != null && memory.lastFocusedProductId != null) {
            for (CartItemVO c : cart) {
                if (c != null && Objects.equals(c.getProductId(), memory.lastFocusedProductId)) {
                    return c;
                }
            }
        }
        if (target != null && target.getId() != null) {
            for (CartItemVO c : cart) {
                if (c != null && c.getProductId() != null && !Objects.equals(c.getProductId(), target.getId())) {
                    return c;
                }
            }
        }
        return cart.get(0);
    }

    private int resolveReplacementQuantity(String prompt, CartItemVO source) {
        if (hasExplicitQuantityCue(prompt)) {
            return Math.max(1, extractQuantity(prompt));
        }
        if (source == null || source.getQuantity() == null) {
            return 1;
        }
        return Math.max(1, source.getQuantity());
    }

    private boolean hasExplicitQuantityCue(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        return QUANTITY_LABELED_PATTERN.matcher(lowered).find()
            || MULTIPLIER_QTY_PATTERN.matcher(lowered).find()
            || containsAny(
                lowered,
                "\u4e24\u4e2a", "\u4e24\u4ef6", "\u4e09\u4e2a", "\u4e09\u4ef6", "\u56db\u4e2a", "\u56db\u4ef6", "\u4e94\u4e2a", "\u4e94\u4ef6",
                "two", "three", "four", "five", "a pair"
            );
    }

    private String normalizePromptForIntent(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String original = input.trim();
        loadNormalizationDict();
        String out = input;
        out = normalizeActionVerbs(out);
        for (Map.Entry<String, String> e : normalizationRules) {
            if (e.getKey() == null || e.getKey().isBlank()) {
                continue;
            }
            out = out.replace(e.getKey(), nvl(e.getValue()));
        }
        for (String filler : DISCOURSE_FILLERS) {
            out = out.replace(filler, " ");
        }
        out = out
            // Keep Chinese words intact; only normalize common punctuation to spaces.
            .replaceAll("[,\\uFF0C;\\uFF1B\\u3001.!?\\uFF01\\uFF1F]+", " ")
            .replaceAll("\\s+", " ")
            .trim();
        // Never allow normalization to erase the whole user utterance.
        return out.isBlank() ? original : out;
    }

    /**
     * Normalize common colloquial action verbs into canonical shopping intents.
     * Keep replacements conservative to avoid harming query semantics.
     */
    private String normalizeActionVerbs(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String out = input;

        // Recommend / search verbs
        out = out.replace("\u5e2e\u6211\u627e", "\u63a8\u8350");
        out = out.replace("\u7ed9\u6211\u627e", "\u63a8\u8350");
        out = out.replace("\u627e\u4e00\u627e", "\u63a8\u8350");
        out = out.replace("\u641c\u4e00\u641c", "\u63a8\u8350");
        out = out.replace("\u641c\u4e00\u4e2a", "\u63a8\u8350");

        // Compare verbs
        out = out.replace("\u6bd4\u4e00\u6bd4", "\u5bf9\u6bd4");
        out = out.replace("\u6bd4\u8f83\u6bd4\u8f83", "\u5bf9\u6bd4");
        out = out.replace("\u5bf9\u6bd4\u4e00\u4e2a", "\u5bf9\u6bd4");
        out = out.replace("pk\u4e00\u4e2a", "\u5bf9\u6bd4");

        // Product detail verbs
        out = out.replace("\u8bb2\u8bb2", "\u4ecb\u7ecd");
        out = out.replace("\u8bf4\u8bf4", "\u4ecb\u7ecd");
        out = out.replace("\u4ecb\u7ecd\u4e00\u4e0b", "\u4ecb\u7ecd");
        out = out.replace("\u5c55\u5f00\u8bf4\u8bf4", "\u4ecb\u7ecd");

        // Add-to-cart verbs
        out = out.replace("\u653e\u8fdb\u8d2d\u7269\u8f66", "\u52a0\u5165\u8d2d\u7269\u8f66");
        out = out.replace("\u653e\u5230\u8d2d\u7269\u8f66", "\u52a0\u5165\u8d2d\u7269\u8f66");
        out = out.replace("\u653e\u5165\u8d2d\u7269\u8f66", "\u52a0\u5165\u8d2d\u7269\u8f66");
        out = out.replace("\u585e\u8fdb\u8d2d\u7269\u8f66", "\u52a0\u5165\u8d2d\u7269\u8f66");
        out = out.replace("\u4e22\u8fdb\u8d2d\u7269\u8f66", "\u52a0\u5165\u8d2d\u7269\u8f66");
        out = out.replace("\u6dfb\u52a0\u8d2d\u7269\u8f66", "\u52a0\u5165\u8d2d\u7269\u8f66");
        out = out.replace("\u52a0\u5165\u5230\u8d2d\u7269\u8f66", "\u52a0\u5165\u8d2d\u7269\u8f66");
        out = out.replace("\u628a\u5b83\u52a0\u5230\u8d2d\u7269\u8f66", "\u52a0\u5165\u8d2d\u7269\u8f66");
        out = out.replace("\u6765\u4e00\u4ef6", "\u52a0\u5165\u8d2d\u7269\u8f66");
        out = out.replace("\u6765\u4e24\u4ef6", "\u52a0\u5165\u8d2d\u7269\u8f66");

        // Checkout verbs
        out = out.replace("\u4e0b\u5355", "\u7ed3\u7b97");
        out = out.replace("\u53bb\u4ed8\u6b3e", "\u7ed3\u7b97");
        out = out.replace("\u7acb\u5373\u4ed8\u6b3e", "\u7ed3\u7b97");
        out = out.replace("\u8d2d\u4e70", "\u7ed3\u7b97");
        out = out.replace("\u7ed3\u5355", "\u7ed3\u7b97");

        // Sort / filter verbs
        out = out.replace("\u6392\u5e8f", "\u6392\u5e8f");
        out = out.replace("\u7b5b\u9009", "\u7b5b\u9009");
        out = out.replace("\u8fc7\u6ee4", "\u7b5b\u9009");

        return out;
    }

    private synchronized void loadNormalizationDict() {
        long now = System.currentTimeMillis();
        if (!normalizationRules.isEmpty() && (now - normalizationRulesLastLoadAt) < NORMALIZATION_RULES_REFRESH_MS) {
            return;
        }
        List<Map.Entry<String, String>> loaded = new ArrayList<>();
        if (queryNormalizationLexiconMapper != null) {
            try {
                List<QueryNormalizationLexicon> rows = queryNormalizationLexiconMapper.listEnabledPromptRules();
                for (QueryNormalizationLexicon row : rows) {
                    if (row == null) {
                        continue;
                    }
                    String from = nvl(row.getPhrase()).trim();
                    String to = nvl(row.getReplacement()).trim();
                    if (!from.isBlank()) {
                        loaded.add(Map.entry(from, to));
                    }
                }
            } catch (Exception ex) {
                lastError = "load normalization db failed: " + ex.getMessage();
            }
        }
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("ai/normalize-dict.txt")) {
            if (is == null) {
                loaded.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
                normalizationRules.clear();
                normalizationRules.addAll(loaded);
                normalizationRulesLastLoadAt = now;
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String t = line.trim();
                    if (t.isBlank() || t.startsWith("#")) {
                        continue;
                    }
                    String[] parts = t.split("=", 2);
                    if (parts.length != 2) {
                        continue;
                    }
                    String from = parts[0].trim();
                    String to = parts[1].trim();
                    if (!from.isBlank()) {
                        loaded.add(Map.entry(from, to));
                    }
                }
            }
        } catch (Exception ignored) {
            loaded.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
            normalizationRules.clear();
            normalizationRules.addAll(loaded);
            normalizationRulesLastLoadAt = now;
            return;
        }
        loaded.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
        normalizationRules.clear();
        normalizationRules.addAll(loaded);
        normalizationRulesLastLoadAt = now;
    }

    private String buildReplyText(String prompt, List<ProductVO> candidates, String actionResult, QueryConstraints constraints) {
        // Prefer LLM for final phrasing whenever available, then fall back to deterministic templates.
        if (chatModel != null && aiEnabled && candidates != null && !candidates.isEmpty()) {
            String llmResult = generateLlmReply(prompt, candidates, actionResult);
            if (llmResult != null
                && !llmResult.isBlank()
                && isLlmReplyConsistentWithCandidates(llmResult, candidates)) {
                return llmResult;
            }
        }

        if (prompt == null || prompt.isBlank()) {
            return "\u544a\u8bc9\u6211\u9884\u7b97\u3001\u54c1\u7c7b\u548c\u7528\u9014\uff0c\u6211\u53ef\u4ee5\u5e2e\u4f60\u63a8\u8350\u5546\u54c1\u3002";
        }

        String lowered = prompt.toLowerCase(Locale.ROOT);
        if (containsAnyTerm(lowered, COMPARE_TERMS) && candidates.size() >= 2) {
            ProductVO a = candidates.get(0);
            ProductVO b = candidates.get(1);
            return "\u6211\u5e2e\u4f60\u5bf9\u6bd4\u4e00\u4e0b\uff1a\n"
                + "1) " + nvl(a.getName()) + " \u4ef7\u683c " + a.getPrice() + " " + nvl(a.getCurrency()) + "\uff0c\u8bc4\u5206 " + nvl(String.valueOf(a.getRating())) + "\uff0c\u9500\u91cf " + nvl(String.valueOf(a.getSales()))
                + "\n2) " + nvl(b.getName()) + " \u4ef7\u683c " + b.getPrice() + " " + nvl(b.getCurrency()) + "\uff0c\u8bc4\u5206 " + nvl(String.valueOf(b.getRating())) + "\uff0c\u9500\u91cf " + nvl(String.valueOf(b.getSales()))
                + "\n\u4f60\u53ef\u4ee5\u7ee7\u7eed\u8bf4\uff1a\u66f4\u4fbf\u5b9c\u7684\u90a3\u4e2a / \u8bc4\u5206\u9ad8\u7684\u90a3\u4e2a / \u5c31\u8fd9\u4e2a\u4e0b\u5355";
        }
        if (containsAnyTerm(lowered, STOCK_TERMS) && !candidates.isEmpty()) {
            ProductVO p = candidates.get(0);
            return "\u5f53\u524d\u53c2\u8003\u5546\u54c1\uff1a" + nvl(p.getName()) + "\uff0c\u5e93\u5b58\u7ea6 " + nvl(String.valueOf(p.getStock())) + " \u4ef6\u3002";
        }
        if (containsAnyTerm(lowered, SHIPPING_TERMS) && !candidates.isEmpty()) {
            return "\u53d1\u8d27\u65f6\u95f4\u901a\u5e38\u53d7\u5e97\u94fa\u548c\u5730\u533a\u5f71\u54cd\u3002\u4f60\u53ef\u4ee5\u5148\u9009\u5b9a\u5546\u54c1\uff0c\u6211\u518d\u5e2e\u4f60\u5bf9\u6bd4\u73b0\u8d27/\u53d1\u8d27\u4f18\u5148\u7684\u51e0\u6b3e\u3002";
        }

        StringBuilder sb = new StringBuilder(buildNeedSummary(prompt, constraints))
            .append("\n")
            .append(strategyHint(prompt, constraints));
        sb.append("\n\u6211\u7ed9\u4f60\u627e\u5230\u4e86\u8fd9\u4e9b\u5019\u9009\u5546\u54c1\uff1a");
        int displayCount = constraints == null ? 4 : Math.max(1, constraints.requestedCount());
        displayCount = Math.min(displayCount, candidates.size());
        for (int i = 0; i < displayCount; i++) {
            ProductVO p = candidates.get(i);
            sb.append("\n")
                .append(i + 1)
                .append(". ")
                .append(Objects.toString(p.getName(), "Unknown product"))
                .append(" (ID: ")
                .append(p.getId())
                .append(", \u4ef7\u683c: ")
                .append(p.getPrice())
                .append(" ")
                .append(Objects.toString(p.getCurrency(), ""))
                .append(")")
                .append(" - ")
                .append(explainRecommendationReason(p, constraints, prompt, i));
        }

        if (actionResult != null && !actionResult.isBlank()) {
            sb.append("\n\n").append(actionResult);
        } else {
            sb.append("\n\n\u4f60\u53ef\u4ee5\u8fd9\u6837\u8bf4\uff1a\u52a0\u5165\u8d2d\u7269\u8f66 \u5546\u54c1ID 123 \u6570\u91cf 2");
        }

        return sb.toString();
    }

    private String explainRecommendationReason(ProductVO p, QueryConstraints constraints, String prompt, int rank) {
        SortPref pref = constraints == null ? SortPref.DEFAULT : constraints.sortPref();
        String lowered = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        if (pref == SortPref.SALES_DESC || containsAnyTerm(lowered, HOT_TERMS)) {
            return "\u9500\u91cf\u8868\u73b0\u66f4\u597d\uff0c\u5f53\u524d\u9500\u91cf " + nvl(String.valueOf(p.getSales())) + "\uff0c\u8bc4\u5206 " + nvl(String.valueOf(p.getRating()));
        }
        if (pref == SortPref.RATING_DESC || containsAnyTerm(lowered, RATING_TERMS)) {
            return "\u53e3\u7891\u4f18\u5148\uff0c\u8bc4\u5206 " + nvl(String.valueOf(p.getRating())) + "\uff0c\u9500\u91cf " + nvl(String.valueOf(p.getSales()));
        }
        if (pref == SortPref.PRICE_ASC || containsAnyTerm(lowered, VALUE_TERMS)) {
            return "\u4ef7\u683c\u66f4\u4f4e\uff0c\u5f53\u524d\u4ef7\u4f4d\u66f4\u53cb\u597d\uff0c\u5e93\u5b58 " + nvl(String.valueOf(p.getStock())) + " \u4ef6";
        }
        if (pref == SortPref.PRICE_DESC || containsAnyTerm(lowered, PREMIUM_TERMS)) {
            return "\u504f\u5411\u9ad8\u7aef\u4ef7\u4f4d\uff0c\u8bc4\u5206 " + nvl(String.valueOf(p.getRating())) + "\uff0c\u54c1\u724c " + nvl(p.getBrand());
        }
        if (rank == 0) {
            return "\u7efc\u5408\u76f8\u5173\u6027\u6700\u9ad8\uff0c\u5339\u914d\u5f53\u524d\u9700\u6c42";
        }
        return "\u7efc\u5408\u76f8\u5173\u6027\u8f83\u9ad8\uff0c\u4e0e\u4f60\u7684\u7b5b\u9009\u6761\u4ef6\u5339\u914d";
    }

    private String generateLlmReply(String prompt, List<ProductVO> candidates, String actionResult) {
        try {
            StringBuilder context = new StringBuilder();
            for (int i = 0; i < Math.min(6, candidates.size()); i++) {
                ProductVO p = candidates.get(i);
                context.append(i + 1)
                    .append(". ID=").append(p.getId())
                    .append(", name=").append(nvl(p.getName()))
                    .append(", category=").append(nvl(p.getCategory()))
                    .append(", brand=").append(nvl(p.getBrand()))
                    .append(", price=").append(p.getPrice()).append(" ").append(nvl(p.getCurrency()))
                    .append(", stock=").append(p.getStock())
                    .append("\n");
            }

            String instruction = "\u4f60\u662f\u7535\u5546\u8d2d\u7269\u52a9\u624b\u3002\u4ec5\u57fa\u4e8e\u5019\u9009\u5546\u54c1\u56de\u7b54\uff0c\u4f7f\u7528\u7b80\u4f53\u4e2d\u6587\uff0c\u7b80\u6d01\u660e\u786e\u3002"
                + "\u8f93\u51fa\u4f7f\u7528\u666e\u901a Markdown\uff1a\u53ef\u4ee5\u7528\u6bb5\u843d\u3001\u77ed\u5217\u8868\u3001\u6362\u884c\uff0c"
                + "\u4e0d\u8981\u5199 **\u52a0\u7c97\u6807\u9898**\uff0c\u4e0d\u8981\u6bcf\u6bb5\u90fd\u7528\u52a0\u7c97\u5305\u88f9\u3002"
                + "\u82e5\u9700\u5217\u51fa\u5019\u9009\uff0c\u5fc5\u987b\u4e00\u884c\u4e00\u4e2a\u5546\u54c1 ID\uff0c\u7981\u6b62\u4e00\u884c\u591a ID\uff08\u4f8b\u5982 ID 1 / 2\uff09\uff0c"
                + "\u4e14\u5217\u51fa\u7684\u5546\u54c1\u6570\u5fc5\u987b\u4e0e Candidates \u6570\u91cf\u5b8c\u5168\u4e00\u81f4\u3002";
            String fullPrompt = instruction
                + "\n\nUser input:\n" + nvl(prompt)
                + "\n\nCandidates:\n" + context
                + "\nExecution result:\n" + (actionResult == null ? "none" : actionResult);
            return sanitizeAgentMarkdown(callChatModel("reply_generation", fullPrompt));
        } catch (Exception ex) {
            lastError = "chat model fallback: " + ex.getMessage();
            System.err.println("[AI Agent] " + lastError);
            return null;
        }
    }

    private boolean isLlmReplyConsistentWithCandidates(String reply, List<ProductVO> candidates) {
        if (reply == null || reply.isBlank() || candidates == null || candidates.isEmpty()) {
            return true;
        }
        Set<Long> candidateIds = new LinkedHashSet<>();
        for (ProductVO p : candidates) {
            if (p != null && p.getId() != null) {
                candidateIds.add(p.getId());
            }
        }
        if (candidateIds.isEmpty()) {
            return true;
        }

        Pattern idPattern = Pattern.compile("(?i)(?:\\bID\\b|\\u5546\\u54c1\\s*ID)\\s*[:=\\uff1a]?\\s*(\\d+)");
        Set<Long> mentionedIds = new LinkedHashSet<>();

        String[] lines = reply.replace("\r\n", "\n").split("\n");
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            Matcher lineMatcher = idPattern.matcher(line);
            int idCountInLine = 0;
            while (lineMatcher.find()) {
                idCountInLine++;
                try {
                    mentionedIds.add(Long.parseLong(lineMatcher.group(1)));
                } catch (Exception ignored) {
                    // ignore parse failures
                }
            }
            // prevent one text row from mentioning multiple cards (e.g. ID 1 / 2)
            if (idCountInLine > 1) {
                return false;
            }
        }

        Matcher matcher = idPattern.matcher(reply);
        while (matcher.find()) {
            Long id;
            try {
                id = Long.parseLong(matcher.group(1));
            } catch (Exception ignored) {
                continue;
            }
            if (!candidateIds.contains(id)) {
                return false;
            }
        }

        if (!mentionedIds.isEmpty() && mentionedIds.size() != candidateIds.size()) {
            return false;
        }
        return true;
    }
    private String toDocumentText(ProductVO p) {
        return "[PRODUCT_ID=" + p.getId() + "] "
            + "name: " + nvl(p.getName()) + "; "
            + "brand: " + nvl(p.getBrand()) + "; "
            + "category: " + nvl(p.getCategory()) + "; "
            + "price: " + nvl(String.valueOf(p.getPrice())) + " " + nvl(p.getCurrency()) + "; "
            + "stock: " + nvl(String.valueOf(p.getStock())) + "; "
            + "rating: " + nvl(String.valueOf(p.getRating())) + "; "
            + "description: " + nvl(p.getDescription());
    }

    private Long extractProductId(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("\\[PRODUCT_ID=(\\d+)]").matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private EmbeddingStore<TextSegment> buildMilvusStoreOrFallback(String host, int port, String collection, int dimension) {
        try {
            Class<?> clazz = Class.forName("dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore");
            Method builderMethod = clazz.getMethod("builder");
            Object builder = builderMethod.invoke(null);
            invoke(builder, "host", String.class, host);
            invokeNumber(builder, "port", port);
            invoke(builder, "collectionName", String.class, collection);
            invokeNumber(builder, "dimension", dimension);
            Object store = invoke(builder, "build");
            return (EmbeddingStore<TextSegment>) store;
        } catch (Exception ex) {
            lastError = "milvus init fallback: " + ex.getMessage();
            System.err.println("[AI Agent] " + lastError);
            return new InMemoryEmbeddingStore<>();
        }
    }

    private static Object invoke(Object target, String methodName, Class<?> argType, Object arg) throws Exception {
        Method method = target.getClass().getMethod(methodName, argType);
        return method.invoke(target, arg);
    }

    private static Object invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static Object invokeNumber(Object target, String methodName, int value) throws Exception {
        try {
            return invoke(target, methodName, Integer.class, value);
        } catch (NoSuchMethodException ignored) {
            return invoke(target, methodName, int.class, value);
        }
    }

    private String callChatModel(String scene, String prompt) {
        if (chatModel == null) {
            return null;
        }
        String key = nvl(scene).isBlank() ? "unknown" : scene;
        llmChatCallsTotal.incrementAndGet();
        llmChatCallsByScene.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        try {
            return sanitizeAgentMarkdown(chatModel.chat(prompt));
        } catch (Exception ex) {
            llmChatCallErrors.incrementAndGet();
            throw ex;
        }
    }

    private void maybeForceLlmEveryTurn(String prompt) {
        if (!forceLlmEveryTurn || prompt == null || prompt.isBlank() || chatModel == null || !aiEnabled) {
            return;
        }
        String probe = "Reply exactly: ok";
        String payload = probe + "\nUser: " + limitLen(prompt, 140);
        try {
            callChatModel("forced_every_turn", payload);
        } catch (Exception ex) {
            lastError = "force llm every turn failed: " + nvl(ex.getMessage());
        }
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private String maskKey(String key) {
        String t = nvl(key).trim();
        if (t.isBlank()) {
            return "";
        }
        if (t.length() <= 10) {
            return "****";
        }
        return t.substring(0, 6) + "..." + t.substring(t.length() - 4);
    }

    private ConversationMemory memoryFor(Long userId) {
        long key = userId == null ? 0L : userId;
        ConversationMemory memory = memoryStore.computeIfAbsent(key, k -> new ConversationMemory());
        memory.userKey = key;
        return memory;
    }

    private AgentReply finalizeReply(ConversationMemory memory, String prompt, AgentReply reply) {
        if (reply != null && reply.content() != null) {
            reply = new AgentReply(sanitizeAgentMarkdown(reply.content()), reply.products());
        }
        if (memory != null) {
            if (prompt != null && !prompt.isBlank()) {
                memory.pushUser(prompt);
            }
            if (reply != null && reply.content() != null && !reply.content().isBlank()) {
                memory.pushAssistant(reply.content());
            }
            logTurnObservation(memory, prompt, reply);
        }
        return reply;
    }

    private void logTurnObservation(ConversationMemory memory, String prompt, AgentReply reply) {
        if (memory == null) {
            return;
        }
        int returned = reply == null || reply.products() == null ? 0 : reply.products().size();
        returnedProductSum.addAndGet(returned);
        if (memory.lastFrameAction != null) {
            actionCounters.get(memory.lastFrameAction).incrementAndGet();
        }
        if (memory.lastScopeType != null) {
            scopeCounters.get(memory.lastScopeType).incrementAndGet();
        }
        if (memory.lastIntent != null) {
            intentCounters.get(memory.lastIntent).incrementAndGet();
        }
        if (!observabilityEnabled) {
            return;
        }
        try {
            int shown = memory.lastShownProductIds == null ? 0 : memory.lastShownProductIds.size();
            String action = memory.lastFrameAction == null ? "UNKNOWN" : memory.lastFrameAction.name();
            String scope = memory.lastScopeType == null ? "UNKNOWN" : memory.lastScopeType.name();
            String intent = memory.lastIntent == null ? "NONE" : memory.lastIntent.name();
            String line = "{"
                + "\"ts\":" + System.currentTimeMillis() + ","
                + "\"event\":\"agent_turn\","
                + "\"userId\":" + memory.userKey + ","
                + "\"intent\":\"" + escapeJson(intent) + "\","
                + "\"action\":\"" + escapeJson(action) + "\","
                + "\"scope\":\"" + escapeJson(scope) + "\","
                + "\"shownCount\":" + shown + ","
                + "\"returnedCount\":" + returned + ","
                + "\"emptyResult\":" + (returned == 0) + ","
                + "\"prompt\":\"" + escapeJson(limitLen(prompt, 160)) + "\","
                + "\"reply\":\"" + escapeJson(limitLen(reply == null ? "" : reply.content(), 200)) + "\""
                + "}";
            System.out.println("[AI Agent Observe] " + line);
        } catch (Exception ignored) {
        }
    }

    private EnumMap<AgentActionRouter.FrameAction, AtomicLong> initActionCounters() {
        EnumMap<AgentActionRouter.FrameAction, AtomicLong> m = new EnumMap<>(AgentActionRouter.FrameAction.class);
        for (AgentActionRouter.FrameAction a : AgentActionRouter.FrameAction.values()) {
            m.put(a, new AtomicLong(0));
        }
        return m;
    }

    private EnumMap<ScopeType, AtomicLong> initScopeCounters() {
        EnumMap<ScopeType, AtomicLong> m = new EnumMap<>(ScopeType.class);
        for (ScopeType s : ScopeType.values()) {
            m.put(s, new AtomicLong(0));
        }
        return m;
    }

    private EnumMap<IntentType, AtomicLong> initIntentCounters() {
        EnumMap<IntentType, AtomicLong> m = new EnumMap<>(IntentType.class);
        for (IntentType i : IntentType.values()) {
            m.put(i, new AtomicLong(0));
        }
        return m;
    }

    private <E extends Enum<E>> Map<String, Long> toCounterMap(EnumMap<E, AtomicLong> source) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (Map.Entry<E, AtomicLong> e : source.entrySet()) {
            out.put(e.getKey().name(), e.getValue().get());
        }
        return out;
    }

    private Map<String, Long> toCounterMap(Map<String, AtomicLong> source) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (Map.Entry<String, AtomicLong> e : source.entrySet()) {
            out.put(e.getKey(), e.getValue().get());
        }
        return out;
    }

    private String limitLen(String s, int max) {
        String t = nvl(s).replaceAll("\\s+", " ").trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max);
    }

    private String escapeJson(String s) {
        return nvl(s)
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }

    private QueryConstraints mergeWithMemoryConstraints(QueryConstraints current, ConversationMemory memory, String prompt) {
        if (memory == null || memory.lastConstraints == null) {
            return current;
        }
        String lowered = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        IntentType promptIntent = detectIntent(prompt);
        if (hasScopeResetCue(lowered)) {
            return current;
        }
        if (promptIntent != IntentType.NONE && memory.lastIntent != IntentType.NONE && promptIntent != memory.lastIntent) {
            return current;
        }
        String anchorBrand = preferredBrandFromLastShown(memory);
        if (isSameBrandFollowUp(lowered) && hasText(anchorBrand)) {
            List<String> include = List.of(anchorBrand.toLowerCase(Locale.ROOT));
            List<String> exclude = current.excludeBrands().isEmpty() ? memory.lastConstraints.excludeBrands() : current.excludeBrands();
            List<String> attrs = current.attributeTerms().isEmpty() ? memory.lastConstraints.attributeTerms() : current.attributeTerms();
            return new QueryConstraints(
                current.minPrice(),
                current.maxPrice(),
                current.requestedCount(),
                current.sortPref() == SortPref.DEFAULT ? memory.lastConstraints.sortPref() : current.sortPref(),
                include,
                exclude,
                attrs
            );
        }
        if (isSwitchBrandFollowUp(lowered)) {
            Set<String> exclude = new LinkedHashSet<>();
            exclude.addAll(memory.lastConstraints.excludeBrands());
            exclude.addAll(memory.lastConstraints.includeBrands());
            if (hasText(anchorBrand)) {
                exclude.add(anchorBrand.toLowerCase(Locale.ROOT));
            }
            List<String> attrs = current.attributeTerms().isEmpty() ? memory.lastConstraints.attributeTerms() : current.attributeTerms();
            return new QueryConstraints(
                current.minPrice(),
                current.maxPrice(),
                current.requestedCount(),
                current.sortPref() == SortPref.DEFAULT ? memory.lastConstraints.sortPref() : current.sortPref(),
                List.of(),
                new ArrayList<>(exclude),
                attrs
            );
        }
        if (isCheaperFollowUp(lowered)) {
            BigDecimal baseline = minPriceOfLastShown(memory);
            if (baseline != null && baseline.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal delta = baseline.compareTo(BigDecimal.ONE) > 0 ? new BigDecimal("0.01") : new BigDecimal("0.0001");
                BigDecimal newMax = baseline.subtract(delta);
                if (newMax.compareTo(BigDecimal.ZERO) <= 0) {
                    newMax = baseline;
                }
                List<String> include = current.includeBrands().isEmpty() ? memory.lastConstraints.includeBrands() : current.includeBrands();
                List<String> exclude = current.excludeBrands().isEmpty() ? memory.lastConstraints.excludeBrands() : current.excludeBrands();
                List<String> attrs = current.attributeTerms().isEmpty() ? memory.lastConstraints.attributeTerms() : current.attributeTerms();
                return new QueryConstraints(
                    current.minPrice(),
                    newMax,
                    current.requestedCount(),
                    current.sortPref() == SortPref.DEFAULT ? SortPref.PRICE_ASC : current.sortPref(),
                    include,
                    exclude,
                    attrs
                );
            }
        }
        boolean hasBudget = containsAny(lowered, "\u4ee5\u5185", "\u4ee5\u4e0b", "\u4e0d\u8d85\u8fc7", "\u6700\u9ad8", "\u6700\u4f4e")
            || RANGE_PATTERN.matcher(lowered).find()
            || MAX_PRICE_PATTERN.matcher(lowered).find()
            || MIN_PRICE_PATTERN.matcher(lowered).find();
        boolean hasBrand = BRAND_ONLY_PATTERN.matcher(prompt == null ? "" : prompt).find()
            || BRAND_EXCLUDE_PATTERN.matcher(prompt == null ? "" : prompt).find();
        if (hasBudget || hasBrand) {
            return current;
        }
        return new QueryConstraints(
            memory.lastConstraints.minPrice(),
            memory.lastConstraints.maxPrice(),
            current.requestedCount(),
            current.sortPref(),
            memory.lastConstraints.includeBrands(),
            memory.lastConstraints.excludeBrands(),
            memory.lastConstraints.attributeTerms()
        );
    }

    private RelaxationResult recoverByRelaxing(String prompt, QueryConstraints constraints, IntentType intent) {
        ensureProductCacheLoaded();
        List<ProductVO> base = intent != IntentType.NONE
            ? strictIntentRetrieve(intent).stream().limit(topK * 8L).toList()
            : new ArrayList<>(productCache.values());

        QueryConstraints step1 = new QueryConstraints(
            constraints.minPrice(), constraints.maxPrice(), constraints.requestedCount(), constraints.sortPref(),
            constraints.includeBrands(), constraints.excludeBrands(), List.of()
        );
        List<ProductVO> s1 = rerankCandidates(prompt, step1, applyConstraints(base, step1), intent).stream().limit(topK).toList();
        if (!s1.isEmpty()) {
            return new RelaxationResult(s1, "\u6ca1\u6709\u5b8c\u5168\u547d\u4e2d\uff0c\u6211\u5df2\u5148\u653e\u5bbd\u90e8\u5206\u5c5e\u6027\u6761\u4ef6\u3002");
        }

        QueryConstraints step2 = new QueryConstraints(
            constraints.minPrice(), constraints.maxPrice(), constraints.requestedCount(), constraints.sortPref(),
            List.of(), List.of(), List.of()
        );
        List<ProductVO> s2 = rerankCandidates(prompt, step2, applyConstraints(base, step2), intent).stream().limit(topK).toList();
        if (!s2.isEmpty()) {
            return new RelaxationResult(s2, "\u6ca1\u6709\u5b8c\u5168\u547d\u4e2d\uff0c\u6211\u5df2\u653e\u5bbd\u54c1\u724c/\u5c5e\u6027\u6761\u4ef6\u3002");
        }

        if (constraints.maxPrice() != null) {
            BigDecimal widened = constraints.maxPrice().multiply(BigDecimal.valueOf(1.2));
            QueryConstraints step3 = new QueryConstraints(
                constraints.minPrice(), widened, constraints.requestedCount(), constraints.sortPref(),
                List.of(), List.of(), List.of()
            );
            List<ProductVO> s3 = rerankCandidates(prompt, step3, applyConstraints(base, step3), intent).stream().limit(topK).toList();
            if (!s3.isEmpty()) {
                return new RelaxationResult(s3, "\u6ca1\u6709\u5b8c\u5168\u547d\u4e2d\uff0c\u6211\u5df2\u5c06\u9884\u7b97\u4e0a\u9650\u653e\u5bbd 20%\u3002");
            }
        }
        return new RelaxationResult(List.of(), "");
    }

    private List<ProductVO> cheaperCandidatesForFollowUp(
        String prompt,
        QueryConstraints constraints,
        IntentType intent,
        ConversationMemory memory
    ) {
        if (memory == null) {
            return List.of();
        }
        BigDecimal baseline = minPriceOfLastShown(memory);
        if (baseline == null || baseline.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }
        BigDecimal delta = baseline.compareTo(BigDecimal.ONE) > 0 ? new BigDecimal("0.01") : new BigDecimal("0.0001");
        BigDecimal cheaperMax = baseline.subtract(delta);
        if (cheaperMax.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }
        BigDecimal cap = constraints.maxPrice();
        if (cap != null && cap.compareTo(cheaperMax) < 0) {
            cheaperMax = cap;
        }
        if (cheaperMax.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        QueryConstraints cheaperConstraints = new QueryConstraints(
            constraints.minPrice(),
            cheaperMax,
            constraints.requestedCount(),
            SortPref.PRICE_ASC,
            constraints.includeBrands(),
            constraints.excludeBrands(),
            constraints.attributeTerms()
        );

        Set<Long> exclude = memory.lastShownProductIds == null ? Set.of() : new LinkedHashSet<>(memory.lastShownProductIds);
        int poolLimit = Math.max(topK * 20, 80);
        LinkedHashMap<Long, ProductVO> merged = new LinkedHashMap<>();
        if (intent != IntentType.NONE) {
            for (ProductVO p : strictIntentRetrieve(intent, exclude, poolLimit)) {
                if (p != null && p.getId() != null) {
                    merged.putIfAbsent(p.getId(), p);
                }
            }
            if (merged.size() < topK && isRelaxedIntentFollowUpEnabled()) {
                for (ProductVO p : relaxedIntentFollowUpRetrieve(prompt, intent, exclude)) {
                    if (p != null && p.getId() != null) {
                        merged.putIfAbsent(p.getId(), p);
                    }
                }
            }
        } else {
            for (ProductVO p : removeSeen(dbFuzzyRetrieve(prompt, poolLimit), exclude)) {
                if (p != null && p.getId() != null) {
                    merged.putIfAbsent(p.getId(), p);
                }
            }
            for (ProductVO p : removeSeen(esFirstRetrieve(prompt, poolLimit), exclude)) {
                if (p != null && p.getId() != null) {
                    merged.putIfAbsent(p.getId(), p);
                }
            }
            List<ProductVO> lexical = aiEnabled ? fastHybridRetrieve(prompt) : keywordRetrieve(prompt);
            for (ProductVO p : removeSeen(lexical, exclude)) {
                if (p != null && p.getId() != null) {
                    merged.putIfAbsent(p.getId(), p);
                }
            }
            for (ProductVO p : deepKeywordRetrieve(prompt, exclude, poolLimit)) {
                if (p != null && p.getId() != null) {
                    merged.putIfAbsent(p.getId(), p);
                }
            }
        }
        if (merged.isEmpty()) {
            return List.of();
        }
        List<ProductVO> pool = new ArrayList<>(merged.values());
        pool = applyConstraints(pool, cheaperConstraints);
        pool = enforceStrictIntentCandidates(intent, pool);
        if (pool.isEmpty()) {
            return List.of();
        }
        List<ProductVO> sorted = new ArrayList<>(pool);
        sorted.sort(
            Comparator
                .comparing((ProductVO p) -> p.getPrice() == null ? BigDecimal.valueOf(Double.MAX_VALUE) : p.getPrice())
                .thenComparing((ProductVO p) -> p.getSales() == null ? 0 : p.getSales(), Comparator.reverseOrder())
        );
        return sorted.stream().limit(topK).toList();
    }

    private boolean shouldDiversifyFollowUp(String prompt, boolean followUp, ConversationMemory memory) {
        return followUp
            && prompt != null
            && !prompt.isBlank()
            && memory != null
            && !memory.recentShownIds.isEmpty();
    }

    private FreshResult freshCandidatesForFollowUp(
        String prompt,
        QueryConstraints constraints,
        IntentType intent,
        ConversationMemory memory,
        List<ProductVO> current
    ) {
        Set<Long> recentSeen = memory.recentShownSet();
        Set<Long> lastBatchSeen = memory.lastShownProductIds == null ? Set.of() : new LinkedHashSet<>(memory.lastShownProductIds);
        List<ProductVO> fresh = removeSeen(current, lastBatchSeen);
        if (fresh.isEmpty() && !recentSeen.isEmpty()) {
            fresh = removeSeen(current, recentSeen);
        }
        if (!fresh.isEmpty()) {
            return new FreshResult(fresh.stream().limit(topK).toList(), "\u5df2\u4f18\u5148\u4e3a\u4f60\u6362\u4e00\u6279\u4e0d\u91cd\u590d\u7684\u5019\u9009\u3002");
        }

        List<ProductVO> alt;
        if (intent != IntentType.NONE) {
            alt = strictIntentRetrieve(intent, lastBatchSeen);
            if (alt.isEmpty() && !recentSeen.isEmpty()) {
                alt = strictIntentRetrieve(intent, recentSeen);
            }
            if (alt.isEmpty() && isRelaxedIntentFollowUpEnabled()) {
                alt = relaxedIntentFollowUpRetrieve(prompt, intent, lastBatchSeen);
            }
            if (alt.isEmpty() && !recentSeen.isEmpty() && isRelaxedIntentFollowUpEnabled()) {
                alt = relaxedIntentFollowUpRetrieve(prompt, intent, recentSeen);
            }
        } else {
            List<ProductVO> merged = new ArrayList<>();
            merged.addAll(removeSeen(dbFuzzyRetrieve(prompt, topK * 6), lastBatchSeen));
            merged.addAll(removeSeen(esFirstRetrieve(prompt), lastBatchSeen));
            if (merged.isEmpty()) {
                merged.addAll(removeSeen(aiEnabled ? fastHybridRetrieve(prompt) : keywordRetrieve(prompt), lastBatchSeen));
            }
            if (merged.isEmpty()) {
                merged.addAll(removeSeen(esFirstRetrieve(prompt, topK * 6), lastBatchSeen));
            }
            if (merged.isEmpty()) {
                merged.addAll(deepKeywordRetrieve(prompt, lastBatchSeen, topK * 6));
            }
            if (merged.isEmpty() && !recentSeen.isEmpty()) {
                merged.addAll(removeSeen(esFirstRetrieve(prompt, topK * 6), recentSeen));
                if (merged.isEmpty()) {
                    merged.addAll(deepKeywordRetrieve(prompt, recentSeen, topK * 6));
                }
            }
            alt = merged;
        }
        if (alt.isEmpty()) {
            return new FreshResult(List.of(), "");
        }

        alt = applyConstraints(alt, constraints);
        alt = rerankCandidates(prompt, constraints, alt, intent).stream().limit(topK).toList();
        if (!alt.isEmpty()) {
            return new FreshResult(alt, "\u5df2\u4f18\u5148\u4e3a\u4f60\u6362\u4e00\u6279\u4e0d\u91cd\u590d\u7684\u5019\u9009\u3002");
        }
        // For follow-up like "鏉╂ɑ婀侀崥?, do not fallback to repeated old batch.
        return new FreshResult(List.of(), "\u57fa\u4e8e\u4f60\u4e0a\u4e00\u8f6e\u7684\u4e3b\u9898\uff0c\u6682\u65f6\u6ca1\u627e\u5230\u66f4\u591a\u4e0d\u91cd\u590d\u7684\u540c\u7c7b\u5546\u54c1\u3002");
    }

    private List<ProductVO> relaxedIntentFollowUpRetrieve(String prompt, IntentType intent, Set<Long> excludeIds) {
        if (intent == IntentType.NONE) {
            return List.of();
        }
        int limit = Math.max(8, topK * 8);
        String base = nvl(prompt).trim();
        String hint = topicHintByIntent(intent);
        List<String> queries = new ArrayList<>();
        if (!base.isBlank()) {
            queries.add(base);
        }
        String withHint = (base + " " + hint).trim();
        if (!withHint.isBlank() && !queries.contains(withHint)) {
            queries.add(withHint);
        }
        if (!hint.isBlank() && !queries.contains(hint)) {
            queries.add(hint);
        }

        Set<Long> seen = new LinkedHashSet<>();
        Set<Long> excluded = excludeIds == null ? Set.of() : excludeIds;
        List<ProductVO> merged = new ArrayList<>();
        for (String q : queries) {
            for (ProductVO p : removeSeen(dbFuzzyRetrieve(q, limit), excluded)) {
                if (p != null && p.getId() != null && seen.add(p.getId())) {
                    merged.add(p);
                }
            }
            for (ProductVO p : removeSeen(esFirstRetrieve(q, limit), excluded)) {
                if (p != null && p.getId() != null && seen.add(p.getId())) {
                    merged.add(p);
                }
            }
            List<ProductVO> lexical = aiEnabled ? fastHybridRetrieve(q) : keywordRetrieve(q);
            for (ProductVO p : removeSeen(lexical, excluded)) {
                if (p != null && p.getId() != null && seen.add(p.getId())) {
                    merged.add(p);
                }
            }
            for (ProductVO p : deepKeywordRetrieve(q, excluded, limit)) {
                if (p != null && p.getId() != null && seen.add(p.getId())) {
                    merged.add(p);
                }
            }
            if (merged.size() >= limit) {
                break;
            }
        }
        if (merged.isEmpty()) {
            return List.of();
        }
        List<ProductVO> filtered = applyIntentFilter(withHint.isBlank() ? base : withHint, merged);
        if (filtered.isEmpty()) {
            return List.of();
        }
        return filtered.stream().limit(limit).toList();
    }

    private List<ProductVO> removeSeen(List<ProductVO> input, Set<Long> seen) {
        if (input == null || input.isEmpty() || seen == null || seen.isEmpty()) {
            return input == null ? List.of() : input;
        }
        List<ProductVO> out = new ArrayList<>();
        for (ProductVO p : input) {
            if (p == null || p.getId() == null || seen.contains(p.getId())) {
                continue;
            }
            out.add(p);
        }
        return out;
    }

    private List<ProductVO> deepKeywordRetrieve(String query, Set<Long> excludeIds, int limit) {
        int capped = Math.max(1, limit);
        String keyword = query == null ? "" : query.trim();
        if (keyword.isBlank()) {
            return List.of();
        }
        Set<Long> dedup = new LinkedHashSet<>();
        Set<Long> excluded = excludeIds == null ? Set.of() : excludeIds;
        List<ProductVO> merged = new ArrayList<>();
        for (String term : expandSearchTerms(keyword)) {
            List<ProductVO> list = shopProductService.listProducts(null, term);
            for (ProductVO p : list) {
                if (p == null || p.getId() == null) {
                    continue;
                }
                if (excluded.contains(p.getId()) || !dedup.add(p.getId())) {
                    continue;
                }
                merged.add(p);
                if (merged.size() >= capped) {
                    return merged;
                }
            }
        }
        return merged;
    }

    private List<ProductVO> proactiveDbRetrieve(String query, int limit) {
        int capped = Math.max(1, limit);
        String raw = nvl(query).trim();
        if (raw.isBlank()) {
            return List.of();
        }
        String lowered = raw.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        terms.add(raw);

        String normalized = normalizeQuery(raw);
        if (!normalized.isBlank()) {
            terms.add(normalized);
        }
        for (String token : splitQueryTokens(normalized)) {
            if (token == null || token.isBlank() || QUERY_STOP_WORDS.contains(token)) {
                continue;
            }
            terms.add(token);
        }

        // Stylus queries often need synonym expansion to hit catalog naming variants.
        if (containsAny(lowered, "\u624b\u5199\u7b14", "\u89e6\u63a7\u7b14", "\u7535\u5bb9\u7b14", "stylus", "apple pencil", "pen for ipad")) {
            terms.add("\u624b\u5199\u7b14");
            terms.add("\u89e6\u63a7\u7b14");
            terms.add("\u7535\u5bb9\u7b14");
            terms.add("stylus");
            terms.add("pencil");
        }

        LinkedHashMap<Long, ProductVO> merged = new LinkedHashMap<>();
        for (String term : terms) {
            List<ProductVO> batch = shopProductService.listProducts(null, term);
            for (ProductVO p : batch) {
                if (p == null || p.getId() == null) {
                    continue;
                }
                merged.putIfAbsent(p.getId(), p);
                if (merged.size() >= capped) {
                    return new ArrayList<>(merged.values());
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    private List<ProductVO> dbFuzzyRetrieve(String query, int limit) {
        int capped = Math.max(1, limit);
        String raw = nvl(query).trim();
        if (raw.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String lowered = raw.toLowerCase(Locale.ROOT);
        String normalized = normalizeQuery(raw);
        if (!lowered.isBlank()) {
            terms.add(lowered);
        }
        if (!normalized.isBlank()) {
            terms.add(normalized);
        }
        for (String t : splitQueryTokens(normalized)) {
            String token = nvl(t).trim().toLowerCase(Locale.ROOT);
            if (token.isBlank() || QUERY_STOP_WORDS.contains(token)) {
                continue;
            }
            terms.add(token);
        }

        // Add short CJK n-grams so phrases like "闂傚倷鑳堕幊鎾绘偤閵娾晛鍨傚┑鍌涙偠閳ь剙鎳橀弫鍌炴偩鐏炲憡鐏嗛梻浣烘嚀閸㈡煡鎯岄崼婵愮劷闁割偅娲橀崐? still hit "闂備浇宕甸崑鐐哄礄瑜版帒纾婚柛鏇ㄥ灡閸?.
        String compactCjk = lowered.replaceAll("\\s+", "");
        if (containsCjk(compactCjk) && compactCjk.length() >= 2) {
            for (int n = 2; n <= 3; n++) {
                if (compactCjk.length() < n) {
                    continue;
                }
                for (int i = 0; i + n <= compactCjk.length(); i++) {
                    String gram = compactCjk.substring(i, i + n);
                    if (QUERY_STOP_WORDS.contains(gram)) {
                        continue;
                    }
                    terms.add(gram);
                }
            }
        }

        // Expand query with aliases learned from the current product catalog.
        List<String> seedTerms = new ArrayList<>(terms);
        for (String seed : seedTerms) {
            List<String> aliases = aliasExpansionMap.get(seed);
            if (aliases == null || aliases.isEmpty()) {
                continue;
            }
            int added = 0;
            for (String alias : aliases) {
                if (alias == null || alias.isBlank()) {
                    continue;
                }
                terms.add(alias);
                added++;
                if (added >= 12) {
                    break;
                }
            }
        }

        LinkedHashMap<Long, ProductVO> merged = new LinkedHashMap<>();
        for (String term : terms) {
            if (term == null || term.isBlank()) {
                continue;
            }
            List<ProductVO> batch = shopProductService.listProducts(null, term);
            for (ProductVO p : batch) {
                if (p == null || p.getId() == null) {
                    continue;
                }
                merged.putIfAbsent(p.getId(), p);
                if (merged.size() >= capped) {
                    return new ArrayList<>(merged.values());
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    private boolean isCheaperFollowUp(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        return containsAny(
            lowered,
            "\u66f4\u4fbf\u5b9c", "\u4fbf\u5b9c\u70b9", "\u4fbf\u5b9c\u4e00\u70b9", "\u518d\u4fbf\u5b9c\u70b9", "\u518d\u4fbf\u5b9c\u4e00\u70b9",
            "\u66f4\u4f4e\u4ef7", "\u4ef7\u683c\u66f4\u4f4e", "\u8fd8\u6709\u66f4\u4fbf\u5b9c\u7684\u5417",
            "\u592a\u8d35", "\u6709\u70b9\u8d35", "\u8d35\u4e86", "\u80fd\u4fbf\u5b9c\u70b9\u5417", "\u518d\u4f4e\u4e00\u70b9", "\u964d\u4ef7",
            "cheaper", "lower price", "less expensive"
        );
    }

    private boolean isExplicitCompareQuestion(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        return containsAny(
            lowered,
            "\u54ea\u4e2a", "\u54ea\u6b3e", "\u54ea\u4e00\u4e2a", "\u5bf9\u6bd4", "\u6bd4\u8f83", "\u6bd4\u4e00\u4e0b", "vs", "versus",
            "which", "compare", "between"
        );
    }

    private boolean shouldTreatAsCheaperSearch(String prompt) {
        if (!isCheaperFollowUp(prompt)) {
            return false;
        }
        String lowered = nvl(prompt).toLowerCase(Locale.ROOT);
        if (isExplicitCompareQuestion(prompt)) {
            return false;
        }
        return containsAny(
            lowered,
            "\u592a\u8d35", "\u6709\u70b9\u8d35", "\u8d35\u4e86", "\u8fd8\u6709", "\u6362\u4e00\u6279", "\u518d\u6765",
            "\u6211\u60f3\u8981", "\u7ed9\u6211", "\u63a8\u8350", "\u627e\u66f4\u4fbf\u5b9c",
            "any cheaper", "cheaper options", "more affordable", "another cheaper", "i want", "show me"
        ) || isFollowUpQuery(prompt);
    }

    private boolean shouldTreatAsPreferenceSearch(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        if (shouldTreatAsCheaperSearch(prompt)) {
            return true;
        }
        if (isExplicitCompareQuestion(prompt)) {
            return false;
        }
        String lowered = nvl(prompt).toLowerCase(Locale.ROOT);
        boolean hasPreferenceTarget = containsAny(
            lowered,
            "\u8bc4\u5206\u66f4\u9ad8", "\u9ad8\u8bc4\u5206", "\u9500\u91cf\u66f4\u9ad8", "\u66f4\u70ed\u95e8", "\u66f4\u9ad8\u7aef",
            "\u53e3\u7891\u66f4\u597d", "\u8d28\u91cf\u66f4\u597d", "\u6027\u4ef7\u6bd4\u66f4\u9ad8",
            "higher rating", "top rated", "more popular", "better quality", "higher sales"
        );
        if (!hasPreferenceTarget) {
            return false;
        }
        return containsAny(
            lowered,
            "\u6211\u60f3\u8981", "\u7ed9\u6211", "\u6765\u70b9", "\u63a8\u8350", "\u627e", "\u6362\u4e00\u6279", "\u8fd8\u6709",
            "i want", "show me", "recommend", "another", "more"
        ) || isFollowUpQuery(prompt);
    }

    private boolean isSwitchBrandFollowUp(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        return containsAny(
            lowered,
            "\u6362\u4e2a\u724c\u5b50", "\u6362\u4e2a\u54c1\u724c", "\u6362\u724c\u5b50", "\u6362\u54c1\u724c",
            "\u522b\u7684\u724c\u5b50", "\u5176\u4ed6\u54c1\u724c", "\u4e0d\u8981\u8fd9\u4e2a\u724c\u5b50", "\u4e0d\u8981\u8fd9\u4e2a\u54c1\u724c",
            "other brand", "different brand", "another brand"
        );
    }

    private boolean isSameBrandFollowUp(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        return containsAny(
            lowered,
            "\u540c\u54c1\u724c", "\u8fd8\u662f\u8fd9\u4e2a\u724c\u5b50", "\u5c31\u8981\u8fd9\u4e2a\u724c\u5b50", "\u8fd9\u4e2a\u724c\u5b50\u7684",
            "same brand", "this brand"
        );
    }

    private String preferredBrandFromLastShown(ConversationMemory memory) {
        if (memory == null || memory.lastShownProductIds == null || memory.lastShownProductIds.isEmpty()) {
            return "";
        }
        for (Long id : memory.lastShownProductIds) {
            ProductVO p = productCache.get(id);
            if (p == null) {
                continue;
            }
            String b = nvl(p.getBrand()).trim();
            if (!b.isBlank()) {
                return b;
            }
        }
        return "";
    }

    private BigDecimal minPriceOfLastShown(ConversationMemory memory) {
        if (memory == null || memory.lastShownProductIds == null || memory.lastShownProductIds.isEmpty()) {
            return null;
        }
        BigDecimal min = null;
        for (Long id : memory.lastShownProductIds) {
            ProductVO p = productCache.get(id);
            if (p == null || p.getPrice() == null) {
                continue;
            }
            if (min == null || p.getPrice().compareTo(min) < 0) {
                min = p.getPrice();
            }
        }
        return min;
    }

    private AgentReply handleMemoryRecall(String prompt, ConversationMemory memory) {
        if (prompt == null || prompt.isBlank() || memory == null) {
            return null;
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        boolean askHistory = containsAny(lowered, "\u4e4b\u524d", "\u521a\u624d", "\u4e0a\u6b21", "\u4e0a\u4e00\u8f6e", "\u524d\u9762");
        if (!askHistory) {
            return null;
        }
        if (containsAny(lowered, "\u63a8\u8350\u4e86\u4ec0\u4e48", "\u4e0a\u6b21\u63a8\u8350", "\u4e4b\u524d\u7684\u5546\u54c1", "\u524d\u9762\u90a3\u4e9b")) {
            List<ProductVO> shown = lastShownProducts(memory);
            if (shown.isEmpty()) {
                return new AgentReply("\u6211\u8fd9\u8fb9\u6682\u65f6\u6ca1\u6709\u4f60\u4e0a\u4e00\u8f6e\u7684\u5546\u54c1\u8bb0\u5f55\uff0c\u53ef\u4ee5\u5148\u8bf4\uff1a\u63a8\u8350\u8033\u673a\u3002", List.of());
            }
            StringBuilder sb = new StringBuilder("\u4f60\u4e0a\u4e00\u8f6e\u770b\u8fc7\u7684\u5546\u54c1\u6709\uff1a");
            for (int i = 0; i < Math.min(4, shown.size()); i++) {
                ProductVO p = shown.get(i);
                sb.append("\n").append(i + 1).append(". ").append(nvl(p.getName())).append(" (ID: ").append(p.getId()).append(")");
            }
            return new AgentReply(sb.toString(), shown.stream().limit(4).toList());
        }
        if (containsAny(lowered, "\u9884\u7b97", "\u4ef7\u683c")) {
            QueryConstraints c = memory.lastConstraints;
            if (c == null || (c.minPrice() == null && c.maxPrice() == null)) {
                return new AgentReply("\u4f60\u8fd9\u4e2a\u4f1a\u8bdd\u91cc\u8fd8\u6ca1\u6709\u8bbe\u5b9a\u660e\u786e\u9884\u7b97\u3002", List.of());
            }
            return new AgentReply("\u4f60\u4e0a\u4e00\u6b21\u7684\u9884\u7b97\u6761\u4ef6\u662f\uff1a\u6700\u4f4e " + nvl(String.valueOf(c.minPrice())) + "\uff0c\u6700\u9ad8 " + nvl(String.valueOf(c.maxPrice())) + "\u3002", List.of());
        }
        if (containsAny(lowered, "\u54c1\u724c")) {
            QueryConstraints c = memory.lastConstraints;
            if (c == null || (c.includeBrands().isEmpty() && c.excludeBrands().isEmpty())) {
                return new AgentReply("\u4f60\u8fd9\u4e2a\u4f1a\u8bdd\u91cc\u8fd8\u6ca1\u6709\u8bbe\u5b9a\u54c1\u724c\u7b5b\u9009\u3002", List.of());
            }
            return new AgentReply("\u4f60\u4e0a\u4e00\u6b21\u7684\u54c1\u724c\u6761\u4ef6\uff1a\u5305\u542b " + c.includeBrands() + "\uff0c\u6392\u9664 " + c.excludeBrands() + "\u3002", List.of());
        }
        if (!memory.userTurns.isEmpty()) {
            return new AgentReply("\u6211\u8bb0\u5f97\u4f60\u6700\u8fd1\u63d0\u8fc7\uff1a" + memory.userTurns.peekLast(), List.of());
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String compactForEmbedding(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String compact = sanitizeUtf16(text).replaceAll("\\s+", " ").trim();
        if (compact.length() <= maxChars) {
            return compact;
        }
        int end = safeUtf16EndIndex(compact, maxChars);
        if (end <= 0) {
            return "";
        }
        return compact.substring(0, end);
    }

    private int safeUtf16EndIndex(String text, int desiredEndExclusive) {
        int end = Math.max(0, Math.min(desiredEndExclusive, text.length()));
        if (end == 0) {
            return 0;
        }
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end -= 1;
        }
        return Math.max(0, end);
    }

    private String sanitizeUtf16(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char ch = text.charAt(i);
            if (Character.isHighSurrogate(ch)) {
                if (i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                    sb.append(ch).append(text.charAt(i + 1));
                    i += 2;
                } else {
                    i += 1;
                }
            } else if (Character.isLowSurrogate(ch)) {
                i += 1;
            } else {
                sb.append(ch);
                i += 1;
            }
        }
        return sb.toString();
    }

    private String sanitizeAgentMarkdown(String text) {
        String clean = sanitizeUtf16(text);
        if (clean.isBlank()) {
            return clean;
        }
        clean = clean.replace("\r\n", "\n");
        clean = clean.replaceAll("(?m)^\\*\\*(.+?)\\*\\*\\s*$", "$1");
        clean = clean.replaceAll("\\*\\*([^*\\n]+)\\*\\*", "$1");
        clean = clean.replaceAll("(?m)^(#{1,6})\\s*\\*\\*(.+?)\\*\\*\\s*$", "$1 $2");
        clean = clean.replaceAll("(?m)^(\\s*[-*]\\s+)\\*\\*(.+?)\\*\\*\\s*$", "$1$2");
        clean = clean.replaceAll("\\n{3,}", "\n\n");
        return clean.trim();
    }

    private enum IntentType {
        NONE, MOUSE, KEYBOARD, PET, BABY, BOOK, TOY, MAKEUP, HEADPHONE, SHOE, BAG, LIGHT, BIKE, OUTDOOR, COMPUTER, ELECTRONICS, BEDDING, DAILY
    }

    enum DialogAct {
        CHITCHAT, SEARCH, FOLLOW_UP, CONFIRM, ACTION
    }

    private enum ConversationStage {
        IDLE, BROWSING, DECIDING
    }

    private enum SortPref {
        DEFAULT, PRICE_ASC, PRICE_DESC, SALES_DESC, RATING_DESC
    }

    private enum ScopeType {
        NEW_SEARCH, LAST_RESULTS
    }

    static class ConversationMemory {
        private long userKey = 0L;
        private IntentType lastIntent = IntentType.NONE;
        private List<Long> lastShownProductIds = List.of();
        private List<Long> previousShownProductIds = List.of();
        private Long lastFocusedProductId;
        private Long lastCartAddProductId;
        private int lastCartAddQty = 0;
        private String lastAttributeKey = "";
        private List<Long> lastAttributeSourceIds = List.of();
        private String lastTopicHint = "";
        private QueryConstraints lastConstraints;
        private ConversationStage stage = ConversationStage.IDLE;
        private AgentActionRouter.FrameAction lastFrameAction = AgentActionRouter.FrameAction.SEARCH;
        private ScopeType lastScopeType = ScopeType.NEW_SEARCH;
        private boolean lastEatScenario = false;
        private final Deque<String> userTurns = new ArrayDeque<>();
        private final Deque<String> assistantTurns = new ArrayDeque<>();
        private final Deque<Long> recentShownIds = new ArrayDeque<>();

        private void pushUser(String text) {
            push(userTurns, text);
        }

        private void pushAssistant(String text) {
            push(assistantTurns, text);
        }

        private void push(Deque<String> q, String text) {
            q.addLast(text);
            while (q.size() > 12) {
                q.removeFirst();
            }
        }

        private void pushShown(List<Long> ids) {
            if (ids == null || ids.isEmpty()) {
                return;
            }
            for (Long id : ids) {
                if (id == null) {
                    continue;
                }
                recentShownIds.addLast(id);
            }
            while (recentShownIds.size() > 80) {
                recentShownIds.removeFirst();
            }
        }

        private Set<Long> recentShownSet() {
            return new LinkedHashSet<>(recentShownIds);
        }

        private void clearContext() {
            lastIntent = IntentType.NONE;
            lastShownProductIds = List.of();
            previousShownProductIds = List.of();
            lastFocusedProductId = null;
            lastCartAddProductId = null;
            lastCartAddQty = 0;
            lastAttributeKey = "";
            lastAttributeSourceIds = List.of();
            lastTopicHint = "";
            lastConstraints = null;
            stage = ConversationStage.IDLE;
            lastFrameAction = AgentActionRouter.FrameAction.SEARCH;
            lastScopeType = ScopeType.NEW_SEARCH;
            lastEatScenario = false;
            recentShownIds.clear();
        }
    }

    private record ScoredProduct(ProductVO product, int score) {}
    private record PreferenceSlots(
        boolean preferWarm,
        boolean preferLight,
        boolean preferQueen,
        boolean preferKing,
        boolean excludeSet,
        boolean onlyMainItem,
        boolean cheaper,
        boolean premium,
        boolean topRated
    ) {
        private boolean hasAny() {
            return preferWarm || preferLight || preferQueen || preferKing || excludeSet || onlyMainItem || cheaper || premium || topRated;
        }

        private String summaryLabel() {
            List<String> labels = new ArrayList<>();
            if (preferWarm) labels.add("\u504f\u539a/\u4fdd\u6696");
            if (preferLight) labels.add("\u504f\u8584/\u900f\u6c14");
            if (preferQueen) labels.add("\u5927\u53f7(Queen)");
            if (preferKing) labels.add("\u7279\u5927\u53f7(King)");
            if (excludeSet) labels.add("\u6392\u9664\u5957\u88c5");
            if (onlyMainItem) labels.add("\u53ea\u8981\u5355\u4ef6");
            if (cheaper) labels.add("\u66f4\u4fbf\u5b9c");
            if (premium) labels.add("\u66f4\u9ad8\u7aef");
            if (topRated) labels.add("\u9ad8\u8bc4\u5206\u4f18\u5148");
            if (labels.isEmpty()) {
                return "\u504f\u597d\u4f18\u5316";
            }
            return String.join(" + ", labels);
        }
    }
    private record SemanticFrame(IntentType topicIntent, ScopeType scopeType, boolean cheaperFollowUp, AgentActionRouter.FrameAction actionType) {}
    private record MetricCondition(String metric, boolean preferLow) {}
    private record RelaxationResult(List<ProductVO> candidates, String note) {}
    private record FreshResult(List<ProductVO> candidates, String note) {}
    private record LlmSemanticHint(IntentType intent, ScopeType scope, double confidence) {
        private static final LlmSemanticHint EMPTY = new LlmSemanticHint(IntentType.NONE, ScopeType.NEW_SEARCH, 0.0);
    }

    private record LlmContextDecision(ScopeType scope, String action, double confidence) {
        private static final LlmContextDecision EMPTY = new LlmContextDecision(ScopeType.NEW_SEARCH, "SEARCH", 0.0);
    }

    private record LlmPrimaryFrame(IntentType intent, ScopeType scope, AgentActionRouter.FrameAction action, double confidence) {}
    private record QueryConstraints(
        BigDecimal minPrice,
        BigDecimal maxPrice,
        int requestedCount,
        SortPref sortPref,
        List<String> includeBrands,
        List<String> excludeBrands,
        List<String> attributeTerms
    ) {}
    private record ShippingInfo(String name, String phone, String address) {
        private boolean complete() {
            return name != null && !name.isBlank()
                && phone != null && !phone.isBlank()
                && address != null && !address.isBlank();
        }
    }
}






