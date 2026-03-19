package com.root.aishopback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.root.aishopback.entity.Product;
import com.root.aishopback.mapper.ProductMapper;
import com.root.aishopback.service.ShopProductService;
import com.root.aishopback.vo.ProductVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
public class ShopProductServiceImpl implements ShopProductService {
    private static final Pattern AMAZON_RENDERING_PATH =
        Pattern.compile("https?://m\\.media-amazon\\.com/images/W/[^/]+/images/", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEARCH_SPLIT_PATTERN = Pattern.compile("[\\s,，;；|/+]+");
    private static final Pattern CN_CONNECTOR_PATTERN = Pattern.compile("[的了和与及]");
    private static final Set<String> SEARCH_STOP_WORDS = Set.of(
        "商品", "推荐", "我要", "我想买", "买", "一下", "来点", "给我", "热销", "好吃", "的", "了"
    );
    private static final Map<String, List<String>> SEARCH_ALIASES = createSearchAliases();

    private final ProductMapper productMapper;

    public ShopProductServiceImpl(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    @Override
    public List<ProductVO> listProducts(String category, String keyword) {
        return (List<ProductVO>) listProducts(category, keyword, null, 1, 20000).get("list");
    }

    @Override
    public Map<String, Object> listProducts(String category, String keyword, String sortBy, int page, int size) {
        LambdaQueryWrapper<Product> query = new LambdaQueryWrapper<>();
        LambdaQueryWrapper<Product> countQuery = new LambdaQueryWrapper<>();
        applyFilters(query, category, keyword);
        applyFilters(countQuery, category, keyword);

        applySort(query, sortBy);

        int safePage = Math.max(page, 1);
        int safeSize = Math.max(Math.min(size, 100), 1);
        long total = productMapper.selectCount(countQuery);
        query.last("LIMIT " + safeSize + " OFFSET " + ((safePage - 1) * safeSize));

        List<Product> products = productMapper.selectList(query);
        if (products.isEmpty() && keyword != null && !keyword.isBlank()) {
            // Fallback: retry with expanded/splitted terms to improve recall.
            products = fallbackSearchProducts(category, keyword, sortBy, safePage, safeSize);
            total = products.size();
        }
        if (products.isEmpty()) {
            return Map.of(
                "list", Collections.emptyList(),
                "total", total,
                "page", safePage,
                "size", safeSize
            );
        }

        List<Long> ids = products.stream().map(Product::getId).filter(Objects::nonNull).toList();
        Map<Long, List<String>> imageMap = buildImageMap(ids);
        Map<Long, List<String>> tagMap = buildTagMap(ids);

        List<ProductVO> result = products.stream()
            .map(product -> toProductVO(product, imageMap.get(product.getId()), tagMap.get(product.getId())))
            .toList();
        return Map.of(
            "list", result,
            "total", total,
            "page", safePage,
            "size", safeSize
        );
    }

    @Override
    public List<Map<String, String>> listCategoryOptions() {
        QueryWrapper<Product> query = new QueryWrapper<>();
        query.select("category")
            .eq("is_active", true)
            .groupBy("category")
            .orderByAsc("category");
        List<Map<String, Object>> rows = productMapper.selectMaps(query);
        Set<String> seen = new LinkedHashSet<>();
        List<Map<String, String>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String rawCategory = stringify(row.get("category"));
            String[] parts = rawCategory.split("\\s*>\\s*");
            String l1 = parts.length > 0 ? parts[0].trim() : "";
            String l2 = parts.length > 1 ? parts[1].trim() : "";
            String l3 = parts.length > 2 ? parts[2].trim() : "";
            if (l1.isBlank() && !rawCategory.isBlank()) {
                l1 = rawCategory.trim();
            }
            if (l1.isBlank() && l2.isBlank() && l3.isBlank()) {
                continue;
            }
            String dedupKey = String.join("||", l1, l2, l3);
            if (!seen.add(dedupKey)) {
                continue;
            }
            result.add(Map.of(
                "level1", l1,
                "level2", l2,
                "level3", l3
            ));
        }
        return result;
    }

    @Override
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
        return Collections.emptyMap();
    }

    private Map<Long, List<String>> buildTagMap(List<Long> ids) {
        return Collections.emptyMap();
    }

    private ProductVO toProductVO(Product product, List<String> images, List<String> tags) {
        ProductVO vo = new ProductVO();
        vo.setId(product.getId());
        vo.setName(product.getName());
        vo.setPrice(product.getPrice());
        vo.setOriginalPrice(product.getOriginalPrice());
        CategoryProfile profile = inferCategory(product, tags);
        vo.setCategoryLevel1(profile.level1());
        vo.setCategoryLevel2(profile.level2());
        vo.setCategoryLevel3(profile.level3());
        vo.setCategoryPath(profile.path());
        vo.setCategory(profile.path());
        vo.setSales(product.getSales());
        vo.setRating(product.getRating());
        vo.setStock(product.getStock());
        vo.setDescription(product.getDescription());
        vo.setBrand(product.getBrand());
        vo.setMerchantName(product.getMerchantName());
        vo.setCurrency(product.getCurrency());
        vo.setBuyerCount(product.getBuyerCount());
        List<String> normalizedImages = images == null ? new ArrayList<>() : new ArrayList<>(images);
        String normalizedPrimary = normalizeImageUrl(product.getImageUrl());
        if (normalizedImages.isEmpty() && normalizedPrimary != null && !normalizedPrimary.isBlank()) {
            normalizedImages.add(normalizedPrimary);
        }
        vo.setImages(normalizedImages);
        vo.setImage(normalizedPrimary != null && !normalizedPrimary.isBlank()
            ? normalizedPrimary
            : (normalizedImages.isEmpty() ? "" : normalizedImages.get(0)));
        vo.setTags(tags == null ? Collections.emptyList() : tags);
        return vo;
    }

    private String normalizeImageUrl(String raw) {
        if (raw == null) {
            return "";
        }
        String url = raw.trim();
        if (url.isBlank()) {
            return "";
        }
        if (url.startsWith("//")) {
            url = "https:" + url;
        }
        // Normalize legacy Amazon rendering URLs that now return HTTP 400.
        // Example:
        // https://m.media-amazon.com/images/W/IMAGERENDERING_xxx/images/I/abc.jpg
        // -> https://m.media-amazon.com/images/I/abc.jpg
        url = AMAZON_RENDERING_PATH.matcher(url).replaceFirst("https://m.media-amazon.com/images/");
        return url;
    }

    private void applySort(LambdaQueryWrapper<Product> query, String sortBy) {
        if ("price_asc".equals(sortBy)) {
            query.orderByAsc(Product::getPrice).orderByDesc(Product::getSales);
        } else if ("price_desc".equals(sortBy)) {
            query.orderByDesc(Product::getPrice).orderByDesc(Product::getSales);
        } else if ("rating".equals(sortBy)) {
            query.orderByDesc(Product::getRating).orderByDesc(Product::getSales);
        } else {
            query.orderByDesc(Product::getSales).orderByDesc(Product::getRating);
        }
    }

    private void applyFilters(LambdaQueryWrapper<Product> query, String category, String keyword) {
        query.eq(Product::getIsActive, true);
        List<String> keywordTerms = expandSearchTerms(keyword);
        if (!keywordTerms.isEmpty()) {
            query.and(w -> {
                boolean first = true;
                for (String term : keywordTerms) {
                    if (!first) {
                        w.or();
                    }
                    w.like(Product::getName, term)
                        .or().like(Product::getCategory, term)
                        .or().like(Product::getBrand, term)
                        .or().like(Product::getDescription, term);
                    first = false;
                }
            });
        }
        List<String> categoryTerms = expandSearchTerms(category);
        if (!categoryTerms.isEmpty()) {
            query.and(w -> {
                boolean first = true;
                for (String term : categoryTerms) {
                    if (!first) {
                        w.or();
                    }
                    w.like(Product::getCategory, term)
                        .or().like(Product::getDescription, term);
                    first = false;
                }
            });
        }
    }

    private List<Product> fallbackSearchProducts(String category, String keyword, String sortBy, int page, int size) {
        LinkedHashMap<Long, Product> merged = new LinkedHashMap<>();
        List<String> terms = expandSearchTerms(keyword);
        int offset = (page - 1) * size;

        for (String term : terms) {
            LambdaQueryWrapper<Product> retry = new LambdaQueryWrapper<>();
            applyFilters(retry, category, term);
            applySort(retry, sortBy);
            retry.last("LIMIT " + Math.max(size * 2, size + offset) + " OFFSET 0");
            List<Product> batch = productMapper.selectList(retry);
            for (Product p : batch) {
                if (p.getId() != null) {
                    merged.putIfAbsent(p.getId(), p);
                }
            }
            if (merged.size() >= offset + size) {
                break;
            }
        }

        if (merged.isEmpty() && category != null && !category.isBlank()) {
            for (String term : terms) {
                LambdaQueryWrapper<Product> retry = new LambdaQueryWrapper<>();
                applyFilters(retry, null, term);
                applySort(retry, sortBy);
                retry.last("LIMIT " + Math.max(size * 2, size + offset) + " OFFSET 0");
                List<Product> batch = productMapper.selectList(retry);
                for (Product p : batch) {
                    if (p.getId() != null) {
                        merged.putIfAbsent(p.getId(), p);
                    }
                }
                if (merged.size() >= offset + size) {
                    break;
                }
            }
        }

        List<Product> out = new ArrayList<>(merged.values());
        if (out.isEmpty()) {
            return out;
        }
        int from = Math.min(offset, out.size());
        int to = Math.min(from + size, out.size());
        return out.subList(from, to);
    }

    private List<String> expandSearchTerms(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String raw = text.trim();
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        terms.add(raw);

        String compact = raw.replaceAll("\\s+", "");
        if (!compact.isBlank()) {
            terms.add(compact);
        }

        String lower = compact.toLowerCase(Locale.ROOT);
        List<String> aliases = SEARCH_ALIASES.get(lower);
        if (aliases != null) {
            terms.addAll(aliases);
        }

        for (String part : SEARCH_SPLIT_PATTERN.split(raw)) {
            addUsefulTerm(terms, part);
        }
        for (String part : CN_CONNECTOR_PATTERN.split(raw)) {
            addUsefulTerm(terms, part);
        }
        return new ArrayList<>(terms);
    }

    private void addUsefulTerm(Set<String> collector, String token) {
        if (token == null) {
            return;
        }
        String t = token.trim();
        if (t.length() <= 1) {
            return;
        }
        String lower = t.toLowerCase(Locale.ROOT);
        if (SEARCH_STOP_WORDS.contains(lower)) {
            return;
        }
        collector.add(t);
        List<String> aliases = SEARCH_ALIASES.get(lower);
        if (aliases != null && !aliases.isEmpty()) {
            collector.addAll(aliases);
        }
    }

    private static Map<String, List<String>> createSearchAliases() {
        Map<String, List<String>> m = new HashMap<>();
        m.put("snack", List.of("零食", "辣条", "魔芋", "豆干", "卤味"));
        m.put("snacks", List.of("零食", "辣条", "魔芋", "豆干", "卤味"));
        m.put("headphone", List.of("耳机", "蓝牙耳机", "降噪耳机"));
        m.put("headphones", List.of("耳机", "蓝牙耳机", "降噪耳机"));
        m.put("shoe", List.of("鞋", "鞋子", "运动鞋", "跑鞋"));
        m.put("shoes", List.of("鞋", "鞋子", "运动鞋", "跑鞋"));
        m.put("dress", List.of("连衣裙", "裙子"));
        m.put("shirt", List.of("衬衫"));
        m.put("shirts", List.of("衬衫"));
        return m;
    }

    private String stringify(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private CategoryProfile inferCategory(Product product, List<String> tags) {
        String source = (
            nvl(product.getName()) + " "
                + nvl(product.getBrand()) + " "
                + nvl(product.getDescription()) + " "
                + String.join(" ", tags == null ? List.of() : tags)
        ).toLowerCase(Locale.ROOT);

        // Priority: classify specific sub-types first, then broader parent classes.
        if (containsAny(source, Set.of("desk mat", "mouse pad", "桌垫", "鼠标垫", "写字垫"))) {
            return new CategoryProfile("办公文具", "办公设备", "桌垫/鼠标垫");
        }
        if (containsAny(source, Set.of("keyboard", "mechanical keyboard", "gaming keyboard", "键盘", "机械键盘"))) {
            return new CategoryProfile("数码电子", "电脑外设", "键盘");
        }
        if (containsAny(source, Set.of("mouse", "wireless mouse", "bluetooth mouse", "gaming mouse", "鼠标"))) {
            return new CategoryProfile("数码电子", "电脑外设", "鼠标");
        }
        if (containsAny(source, Set.of("monitor", "gaming monitor", "显示器", "电竞屏"))) {
            return new CategoryProfile("数码电子", "电脑外设", "显示器");
        }
        if (containsAny(source, Set.of("hdmi cable", "bnc to hdmi", "video cable", "视频线", "hdmi转接"))) {
            return new CategoryProfile("数码电子", "数码配件", "视频线/转换器");
        }
        if (containsAny(source, Set.of("stylus", "touch pen", "手写笔", "触控笔"))) {
            return new CategoryProfile("数码电子", "数码配件", "手写笔/触控笔");
        }
        if (containsAny(source, Set.of("ssd", "hdd", "hard drive", "u disk", "usb drive", "memory card", "tf card", "存储卡", "硬盘", "固态", "u盘"))) {
            return new CategoryProfile("数码电子", "数码配件", "存储设备");
        }
        if (containsAny(source, Set.of("router", "modem", "mesh", "wifi", "wi-fi", "路由器", "网卡"))) {
            return new CategoryProfile("数码电子", "网络设备", "路由器/网络设备");
        }
        if (containsAny(source, Set.of("kvm", "switcher", "dock", "docking station", "usb hub", "hub", "扩展坞", "切换器", "分线器"))) {
            return new CategoryProfile("数码电子", "数码配件", "扩展坞/切换器");
        }
        if (containsAny(source, Set.of("charger", "cable", "adapter", "type-c", "lightning", "充电器", "数据线", "转接头"))) {
            return new CategoryProfile("数码电子", "数码配件", "充电器/数据线");
        }
        if (containsAny(source, Set.of("headphone", "headset", "earphone", "earbud", "airpods", "耳机", "蓝牙耳机"))) {
            return new CategoryProfile("数码电子", "音频设备", "耳机");
        }
        if (containsAny(source, Set.of("speaker", "soundbar", "音箱", "音响"))) {
            return new CategoryProfile("数码电子", "音频设备", "音箱");
        }
        if (containsAny(source, Set.of("console", "playstation", "ps5", "ps4", "xbox", "nintendo switch", "steam deck", "game console", "游戏机", "主机游戏", "游戏主机"))) {
            return new CategoryProfile("游戏娱乐", "电子游戏", "游戏主机");
        }
        if (containsAny(source, Set.of("gamepad", "controller", "joystick", "gaming chair", "电竞椅", "手柄", "方向盘", "摇杆"))) {
            return new CategoryProfile("游戏娱乐", "电子游戏", "游戏外设");
        }
        if (containsAny(source, Set.of("video game", "game cd", "game card", "dlc", "游戏卡", "游戏光盘", "电子游戏"))) {
            return new CategoryProfile("游戏娱乐", "电子游戏", "游戏软件");
        }

        if (containsAny(source, Set.of("phone", "smartphone", "iphone", "galaxy", "手机"))) {
            return new CategoryProfile("数码电子", "手机通讯", "智能手机");
        }
        if (containsAny(source, Set.of("tablet", "ipad", "平板"))) {
            return new CategoryProfile("数码电子", "平板设备", "平板电脑");
        }
        if (containsAny(source, Set.of("smart watch", "watch", "wearable", "手表", "智能穿戴", "手环"))) {
            return new CategoryProfile("数码电子", "智能穿戴", "智能手表/手环");
        }
        if (containsAny(source, Set.of("laptop", "notebook", "macbook", "desktop", "all in one", "mini pc", "computer", "笔记本", "电脑", "台式", "主机"))) {
            return new CategoryProfile("数码电子", "电脑整机", "笔记本/台式机");
        }

        if (containsAny(source, Set.of("printer", "shredder", "scanner", "binder", "label", "paper", "stapler", "办公", "打印机", "碎纸机", "扫描仪", "活页夹", "标签纸", "订书机"))) {
            return new CategoryProfile("办公文具", "办公设备", "打印/装订/纸品");
        }
        if (containsAny(source, Set.of("pen", "pencil", "marker", "notebook", "journal", "文具", "笔记本", "中性笔", "铅笔", "记号笔"))) {
            return new CategoryProfile("办公文具", "文具耗材", "书写/本册");
        }

        if (containsAny(source, Set.of("sofa", "mattress", "bed frame", "wardrobe", "desk", "table", "chair", "futon", "沙发", "床垫", "床架", "衣柜", "书桌", "餐桌", "椅子"))) {
            return new CategoryProfile("家居生活", "家具", "客厅/卧室家具");
        }
        if (containsAny(source, Set.of("lamp", "light", "lighting", "ceiling fan", "台灯", "吊灯", "灯带", "照明", "风扇灯"))) {
            return new CategoryProfile("家居生活", "家装灯具", "灯具照明");
        }
        if (containsAny(source, Set.of("curtain", "carpet", "rug", "mat", "窗帘", "地毯", "地垫"))) {
            return new CategoryProfile("家居生活", "家纺布艺", "窗帘/地毯");
        }

        if (containsAny(source, Set.of("baby", "infant", "newborn", "crib", "stroller", "diaper", "婴儿", "宝宝", "新生儿", "婴儿床", "推车", "尿布"))) {
            return new CategoryProfile("母婴用品", "婴童用品", "喂养/出行/寝居");
        }
        if (containsAny(source, Set.of("toy", "lego", "puzzle", "doll", "party", "mask", "玩具", "拼图", "积木", "娃娃", "派对用品", "面具"))) {
            return new CategoryProfile("母婴玩具", "玩具乐器", "益智/派对玩具");
        }
        if (containsAny(source, Set.of("card game", "trumps", "board game", "桌游", "卡牌游戏", "棋牌"))) {
            return new CategoryProfile("母婴玩具", "玩具乐器", "桌游/卡牌");
        }
        if (containsAny(source, Set.of("racket", "golf", "fishing", "skateboard", "scooter", "tennis", "baseball", "helmet", "网球", "高尔夫", "钓鱼", "滑板", "滑板车", "棒球", "运动头盔"))) {
            return new CategoryProfile("运动户外", "户外运动", "球类/骑行/垂钓");
        }
        if (containsAny(source, Set.of("bracelet", "necklace", "earring", "ring", "anklet", "手链", "项链", "耳环", "戒指", "脚链"))) {
            return new CategoryProfile("时尚服饰", "珠宝配饰", "首饰/饰品");
        }
        if (containsAny(source, Set.of("perfume", "fragrance", "deodorant", "body spray", "香水", "止汗", "体香喷雾"))) {
            return new CategoryProfile("美妆个护", "香氛个护", "香水/止汗");
        }
        if (containsAny(source, Set.of("garden", "patio", "trellis", "outdoor canopy", "plant", "花园", "庭院", "格架", "遮阳篷", "园艺"))) {
            return new CategoryProfile("家居生活", "园艺户外", "庭院/园艺用品");
        }
        if (containsAny(source, Set.of("faucet", "shower valve", "door knob", "dimmer", "wire", "wiring", "waterproof connector", "水龙头", "淋浴阀", "门把手", "调光器", "接线", "连接器"))) {
            return new CategoryProfile("家居生活", "家装建材", "五金/电工/卫浴");
        }
        if (containsAny(source, Set.of("shower hose", "flange", "tile", "淋浴软管", "法兰", "地砖"))) {
            return new CategoryProfile("家居生活", "家装建材", "卫浴/地面材料");
        }
        if (containsAny(source, Set.of("boat", "dock line", "marine", "rv", "trailer", "船", "码头", "拖车", "房车"))) {
            return new CategoryProfile("汽车用品", "车船配件", "船舶/拖车配件");
        }
        if (containsAny(source, Set.of("sunshade", "ac cover", "windshield", "防晒挡", "空调罩", "车窗遮阳"))) {
            return new CategoryProfile("汽车用品", "车载配件", "车罩/遮阳");
        }
        if (containsAny(source, Set.of("belt", "oxygen sensor", "wiper", "fuel tank", "engine", "皮带", "氧传感器", "雨刷", "油箱", "发动机"))) {
            return new CategoryProfile("汽车用品", "汽车配件", "维修保养件");
        }
        if (containsAny(source, Set.of("rack case", "server rack", "network rack", "机架", "机柜"))) {
            return new CategoryProfile("数码电子", "网络设备", "机柜/机架");
        }
        if (containsAny(source, Set.of("surge protector", "power tap", "插排", "浪涌保护"))) {
            return new CategoryProfile("数码电子", "数码配件", "电源/插座");
        }
        if (containsAny(source, Set.of("speaker", "audio isolator", "drum head", "扬声器", "音频隔离", "鼓皮"))) {
            return new CategoryProfile("数码电子", "音频设备", "音响配件");
        }
        if (containsAny(source, Set.of("knife sharpener", "pocket knife", "磨刀器", "小刀"))) {
            return new CategoryProfile("家居生活", "厨房用品", "刀具/磨刀");
        }
        if (containsAny(source, Set.of("adhesive", "resin finish", "wood filler", "paint additive", "胶粘剂", "树脂", "木材填料"))) {
            return new CategoryProfile("工业用品", "化工辅料", "胶黏/修补材料");
        }
        if (containsAny(source, Set.of("wig", "hat", "costume", "假发", "帽子", "服装道具"))) {
            return new CategoryProfile("时尚服饰", "配饰", "帽子/假发");
        }
        if (containsAny(source, Set.of("sock", "袜"))) {
            return new CategoryProfile("时尚服饰", "服装", "袜子");
        }

        if (containsAny(source, Set.of("vitamin", "supplement", "probiotic", "fish oil", "capsule", "维生素", "补充剂", "益生菌", "鱼油", "胶囊"))) {
            return new CategoryProfile("食品保健", "营养保健", "维矿/功能补充");
        }
        if (containsAny(source, Set.of("coffee", "tea", "snack", "chocolate", "cookie", "almond butter", "零食", "咖啡", "茶", "巧克力", "饼干"))) {
            return new CategoryProfile("食品保健", "休闲食品", "零食/冲饮");
        }

        if (containsAny(source, Set.of("book", "novel", "textbook", "kindle", "图书", "小说", "教材", "电子书"))) {
            return new CategoryProfile("图书音像", "图书", "文学/教育");
        }
        if (containsAny(source, Set.of("cd", "vinyl", "album", "blu-ray", "music", "唱片", "专辑", "蓝光", "音乐"))) {
            return new CategoryProfile("图书音像", "音像制品", "CD/黑胶/影碟");
        }
        if (containsAny(source, Set.of("frame", "canvas", "tapestry", "wall art", "plaque", "相框", "帆布画", "挂毯", "壁画", "牌匾"))) {
            return new CategoryProfile("家居生活", "家居装饰", "装饰画/摆件");
        }
        if (containsAny(source, Set.of("paint", "pigment", "resin mold", "stamp carving", "drawing kit", "canvas panel", "颜料", "树脂模具", "雕刻块", "数字油画", "画布"))) {
            return new CategoryProfile("家居生活", "文创手作", "绘画/手工");
        }
        if (containsAny(source, Set.of("bra", "bralette", "underwire", "lingerie", "swim bra", "文胸", "内衣", "钢托", "比基尼上衣"))) {
            return new CategoryProfile("时尚服饰", "内衣", "文胸/内衣");
        }
        if (containsAny(source, Set.of("boot", "chelsea", "work boot", "靴子", "工装靴"))) {
            return new CategoryProfile("时尚服饰", "鞋靴", "靴子");
        }
        if (containsAny(source, Set.of("t-shirt", "tee", "dress", "hoodie", "polo", "jacket", "背心", "t恤", "连衣裙", "polo衫", "夹克"))) {
            return new CategoryProfile("时尚服饰", "服装", "上装/连衣裙");
        }
        if (containsAny(source, Set.of("eyeliner", "serum", "bronzer", "cosmetics", "古铜", "精华液", "眼线笔"))) {
            return new CategoryProfile("美妆个护", "彩妆护肤", "彩妆/精华");
        }

        if (containsAny(source, Set.of("shoe", "sneaker", "boots", "sandals", "heel", "跑鞋", "靴", "凉鞋", "运动鞋"))) {
            return new CategoryProfile("时尚服饰", "鞋靴", "休闲/运动鞋");
        }
        if (containsAny(source, Set.of("bag", "backpack", "handbag", "tote", "luggage", "包", "背包", "手提包", "行李箱"))) {
            return new CategoryProfile("时尚服饰", "箱包", "日常/旅行包");
        }
        if (containsAny(source, Set.of("jacket", "pants", "shirt", "dress", "coat", "t-shirt", "hoodie", "jeans", "外套", "连衣裙", "卫衣", "t恤", "牛仔裤"))) {
            return new CategoryProfile("时尚服饰", "服装", "服饰");
        }

        if (containsAny(source, Set.of("kitchen", "cookware", "pan", "knife", "餐具", "厨房", "锅", "刀具"))) {
            return new CategoryProfile("家居生活", "厨房用品", "厨房工具");
        }
        if (containsAny(source, Set.of("quilt", "duvet", "comforter", "blanket", "bedding", "bed sheet", "bed set", "被子", "棉被", "羽绒被", "床品", "四件套", "床单", "被套"))) {
            return new CategoryProfile("家居生活", "家纺床品", "被子/床上用品");
        }
        if (containsAny(source, Set.of("bath", "bathroom", "shower", "toilet", "浴室", "沐浴", "马桶"))) {
            return new CategoryProfile("家居生活", "卫浴用品", "卫浴清洁");
        }
        if (containsAny(source, Set.of("shower curtain", "浴帘"))) {
            return new CategoryProfile("家居生活", "卫浴用品", "浴帘/浴室配件");
        }
        if (containsAny(source, Set.of("storage", "organizer", "box", "收纳", "整理"))) {
            return new CategoryProfile("家居生活", "收纳整理", "收纳用品");
        }
        if (containsAny(source, Set.of("clean", "detergent", "cleaner", "soap", "清洁", "洗涤", "消毒"))) {
            return new CategoryProfile("家居生活", "清洁用品", "清洁用品");
        }

        if (containsAny(source, Set.of("dog", "cat", "pet", "宠物", "狗", "猫"))) {
            return new CategoryProfile("宠物用品", "宠物用品", "食品/玩具/健康");
        }

        if (containsAny(source, Set.of("car", "auto", "vehicle", "tire", "汽车", "车载", "轮胎"))) {
            return new CategoryProfile("汽车用品", "汽车配件", "车载电子/工具");
        }
        if (containsAny(source, Set.of("tpms", "tire pressure", "胎压监测"))) {
            return new CategoryProfile("汽车用品", "汽车配件", "车载电子/工具");
        }

        if (containsAny(source, Set.of("makeup", "skincare", "hair", "cosmetic", "lipstick", "eyeliner", "美容", "护肤", "洗发", "护发", "口红", "眼线"))) {
            return new CategoryProfile("美妆个护", "个人护理", "护肤/护发");
        }

        if (containsAny(source, Set.of("dumbbell", "fitness", "gym", "treadmill", "yoga", "健身", "瑜伽", "跑步机"))) {
            return new CategoryProfile("运动户外", "健身器材", "健身装备");
        }

        if (containsAny(source, Set.of("industrial", "tool", "safety", "vest", "helmet", "wrench", "drill", "工具", "安全", "反光", "扳手", "电钻"))) {
            return new CategoryProfile("工业用品", "工具与安防", "作业装备");
        }

        String fallback = nvl(product.getCategory());
        if (fallback.isBlank()) {
            return new CategoryProfile("综合", "通用商品", "未分类");
        }
        return new CategoryProfile("综合", fallback, "通用商品");
    }

    private boolean containsAny(String source, Set<String> words) {
        for (String w : words) {
            if (source.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private record CategoryProfile(String level1, String level2, String level3) {
        private String path() {
            return level1 + " > " + level2 + " > " + level3;
        }
    }
}
