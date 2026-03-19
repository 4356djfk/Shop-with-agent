package com.root.aishopback.controller;

import com.alibaba.fastjson2.JSON;
import com.root.aishopback.service.ChatHistoryService;
import com.root.aishopback.service.ShopCartService;
import com.root.aishopback.service.ShopOrderService;
import com.root.aishopback.service.ShopProductService;
import com.root.aishopback.vo.CartItemVO;
import com.root.aishopback.vo.ProductVO;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/agent")
@CrossOrigin(origins = "*")
public class AgentCompatController {

    private final ShopProductService shopProductService;
    private final ShopCartService shopCartService;
    private final ShopOrderService shopOrderService;
    private final ChatHistoryService chatHistoryService;

    public AgentCompatController(
        ShopProductService shopProductService,
        ShopCartService shopCartService,
        ShopOrderService shopOrderService,
        ChatHistoryService chatHistoryService
    ) {
        this.shopProductService = shopProductService;
        this.shopCartService = shopCartService;
        this.shopOrderService = shopOrderService;
        this.chatHistoryService = chatHistoryService;
    }

    @GetMapping("/products")
    public Map<String, Object> products(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String brand,
        @RequestParam(name = "min_price", required = false) BigDecimal minPrice,
        @RequestParam(name = "max_price", required = false) BigDecimal maxPrice,
        @RequestParam(name = "sort_by", required = false) String sortBy,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(name = "page_size", defaultValue = "10") Integer pageSize
    ) {
        String normalizedSort = normalizeSort(sortBy);
        int safePage = safePage(page);
        int safeSize = safePageSize(pageSize);
        // Over-fetch to reduce duplicate SKU variants (same product, different image/stock rows).
        int fetchSize = Math.min(100, Math.max(safeSize * 4, safeSize));
        Map<String, Object> data = shopProductService.listProducts(category, keyword, normalizedSort, safePage, fetchSize);
        List<ProductVO> list = castProducts(data.get("list"));
        list = deduplicateProducts(list);
        list = list.stream()
            .filter(p -> brand == null || brand.isBlank() || text(p.getBrand()).toLowerCase(Locale.ROOT).contains(brand.toLowerCase(Locale.ROOT)))
            .filter(p -> minPrice == null || p.getPrice() == null || p.getPrice().compareTo(minPrice) >= 0)
            .filter(p -> maxPrice == null || p.getPrice() == null || p.getPrice().compareTo(maxPrice) <= 0)
            .toList();

        int from = Math.min((safePage - 1) * safeSize, list.size());
        int to = Math.min(from + safeSize, list.size());
        List<ProductVO> paged = list.subList(from, to);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", paged.stream().map(this::toAgentProduct).toList());
        out.put("total", list.size());
        out.put("page", safePage);
        out.put("page_size", safeSize);
        return out;
    }

    @GetMapping("/products/{id}")
    public Map<String, Object> productDetail(@PathVariable Long id) {
        ProductVO product = shopProductService.getProductDetail(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", product == null ? null : toAgentProduct(product));
        return out;
    }

    @GetMapping("/products/categories")
    public Map<String, Object> categories() {
        List<Map<String, String>> categories = shopProductService.listCategoryOptions();
        List<Map<String, Object>> out = categories.stream()
            .map(item -> {
                String name = List.of(text(item.get("level1")), text(item.get("level2")), text(item.get("level3"))).stream()
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.joining(" > "));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", name);
                row.put("count", 0);
                return row;
            })
            .toList();
        return Map.of("data", out);
    }

    @GetMapping("/products/{id}/reviews")
    public Map<String, Object> reviews(
        @PathVariable Long id,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(name = "page_size", defaultValue = "10") Integer pageSize
    ) {
        return Map.of("data", List.of(), "page", safePage(page), "page_size", safePageSize(pageSize));
    }

    @GetMapping("/products/{id}/similar")
    public Map<String, Object> similar(@PathVariable Long id, @RequestParam(defaultValue = "5") Integer limit) {
        ProductVO base = shopProductService.getProductDetail(id);
        if (base == null) {
            return Map.of("data", List.of());
        }
        List<ProductVO> all = shopProductService.listProducts(base.getCategory(), null);
        List<Map<String, Object>> data = all.stream()
            .filter(p -> p.getId() != null && !p.getId().equals(id))
            .sorted(Comparator.comparing(ProductVO::getSales, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(Math.max(1, limit))
            .map(this::toAgentProduct)
            .toList();
        return Map.of("data", data);
    }

    @GetMapping("/products/{id}/bought-together")
    public Map<String, Object> boughtTogether(@PathVariable Long id, @RequestParam(defaultValue = "5") Integer limit) {
        return similar(id, limit);
    }

    @GetMapping("/products/hot")
    public Map<String, Object> hot(
        @RequestParam(required = false) String category,
        @RequestParam(defaultValue = "10") Integer limit
    ) {
        List<ProductVO> products = shopProductService.listProducts(category, null);
        List<Map<String, Object>> data = products.stream()
            .sorted(Comparator.comparing(ProductVO::getSales, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(Math.max(1, limit))
            .map(this::toAgentProduct)
            .toList();
        return Map.of("data", data);
    }

    @GetMapping("/products/new")
    public Map<String, Object> newest(
        @RequestParam(required = false) String category,
        @RequestParam(defaultValue = "10") Integer limit
    ) {
        List<ProductVO> products = shopProductService.listProducts(category, null);
        List<Map<String, Object>> data = products.stream()
            .sorted(Comparator.comparing(ProductVO::getId, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(Math.max(1, limit))
            .map(this::toAgentProduct)
            .toList();
        return Map.of("data", data);
    }

    @GetMapping("/products/deals")
    public Map<String, Object> deals(@RequestParam(defaultValue = "10") Integer limit) {
        List<ProductVO> products = shopProductService.listProducts(null, null);
        List<Map<String, Object>> data = products.stream()
            .sorted(Comparator.comparing(this::discountRate, Comparator.reverseOrder()))
            .limit(Math.max(1, limit))
            .map(this::toAgentProduct)
            .toList();
        return Map.of("data", data);
    }

    @GetMapping("/cart/{userId}")
    public Map<String, Object> cart(@PathVariable Long userId) {
        List<CartItemVO> items = shopCartService.listCart(userId);
        List<Map<String, Object>> outItems = items.stream().map(item -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("cart_id", item.getId());
            m.put("product_id", item.getProductId());
            m.put("product_name", item.getName());
            m.put("quantity", item.getQuantity());
            m.put("unit_price", item.getPrice());
            m.put("total_price", item.getPrice() == null ? null : item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity() == null ? 1 : item.getQuantity())));
            m.put("selected", true);
            return m;
        }).toList();

        BigDecimal total = items.stream()
            .filter(Objects::nonNull)
            .map(i -> i.getPrice() == null ? BigDecimal.ZERO : i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity() == null ? 1 : i.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of(
            "data", Map.of(
                "user_id", userId,
                "items", outItems,
                "total_amount", total
            )
        );
    }

    @PostMapping("/cart/add")
    public Map<String, Object> cartAdd(@RequestBody Map<String, Object> body) {
        long userId = toLong(body.get("user_id"));
        long productId = toLong(body.get("product_id"));
        int quantity = Math.max(1, toInt(body.get("quantity"), 1));
        shopCartService.addToCart(userId, productId, quantity);
        return Map.of("success", true, "message", "添加成功");
    }

    @PutMapping("/cart/update")
    public Map<String, Object> cartUpdate(@RequestBody Map<String, Object> body) {
        long userId = toLong(body.get("user_id"));
        long productId = toLong(body.get("product_id"));
        int quantity = Math.max(0, toInt(body.get("quantity"), 1));
        if (quantity <= 0) {
            shopCartService.removeCart(userId, null, productId);
        } else {
            shopCartService.updateCart(userId, null, productId, quantity);
        }
        return Map.of("success", true, "message", "更新成功");
    }

    @DeleteMapping("/cart/{userId}/item/{productId}")
    public Map<String, Object> cartRemove(@PathVariable Long userId, @PathVariable Long productId) {
        shopCartService.removeCart(userId, null, productId);
        return Map.of("success", true, "message", "删除成功");
    }

    @DeleteMapping("/cart/{userId}/clear")
    public Map<String, Object> cartClear(@PathVariable Long userId) {
        List<CartItemVO> items = shopCartService.listCart(userId);
        for (CartItemVO item : items) {
            if (item == null || item.getId() == null) {
                continue;
            }
            shopCartService.removeCart(userId, item.getId(), null);
        }
        return Map.of("success", true, "message", "清空成功");
    }

    @PutMapping("/cart/select")
    public Map<String, Object> cartSelect(@RequestBody Map<String, Object> body) {
        return Map.of("success", true, "message", "操作成功");
    }

    @GetMapping("/orders")
    public Map<String, Object> orders(
        @RequestParam(name = "user_id") Long userId,
        @RequestParam(name = "order_status", required = false) String orderStatus,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(name = "page_size", defaultValue = "10") Integer pageSize
    ) {
        List<Map<String, Object>> all = shopOrderService.listOrders(userId);
        List<Map<String, Object>> filtered = all.stream()
            .filter(m -> orderStatus == null || orderStatus.isBlank() || orderStatus.equalsIgnoreCase(text(m.get("orderStatus"))))
            .toList();
        int p = safePage(page);
        int s = safePageSize(pageSize);
        int from = Math.min((p - 1) * s, filtered.size());
        int to = Math.min(from + s, filtered.size());
        List<Map<String, Object>> data = filtered.subList(from, to).stream().map(this::toAgentOrder).toList();
        return Map.of("data", data, "total", filtered.size(), "page", p, "page_size", s);
    }

    @GetMapping("/orders/{id}")
    public Map<String, Object> orderDetail(@PathVariable String id, @RequestParam(name = "user_id", required = false) Long userId) {
        Map<String, Object> order = null;
        try {
            order = shopOrderService.getOrderByNo(userId == null ? 0L : userId, id);
        } catch (Exception ignored) {
            order = null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", order == null ? null : toAgentOrder(order));
        return out;
    }

    @GetMapping("/orders/{id}/status")
    public Map<String, Object> orderStatus(@PathVariable String id, @RequestParam(name = "user_id", required = false) Long userId) {
        Map<String, Object> order = null;
        try {
            order = shopOrderService.getOrderByNo(userId == null ? 0L : userId, id);
        } catch (Exception ignored) {
            order = null;
        }
        if (order == null) {
            return Map.of("data", Map.of("order_id", id, "order_status", "unknown", "payment_status", "unknown"));
        }
        return Map.of("data", Map.of(
            "order_id", id,
            "order_status", text(order.get("orderStatus")),
            "payment_status", text(order.get("paymentStatus"))
        ));
    }

    @GetMapping("/orders/{id}/tracking")
    public Map<String, Object> orderTracking(@PathVariable String id) {
        Map<String, Object> tracking = new LinkedHashMap<>();
        tracking.put("order_id", id);
        tracking.put("tracking_no", "SIM-" + id);
        tracking.put("status", "processing");
        tracking.put("traces", List.of(
            Map.of("time", LocalDateTime.now().minusHours(2).toString(), "location", "warehouse", "status", "订单已创建"),
            Map.of("time", LocalDateTime.now().minusHours(1).toString(), "location", "warehouse", "status", "拣货中")
        ));
        return Map.of("data", tracking);
    }

    @GetMapping("/chat/{userId}/history")
    public Map<String, Object> chatHistory(@PathVariable Long userId, @RequestParam(defaultValue = "50") Integer limit) {
        List<Map<String, Object>> data = chatHistoryService.listRecent(userId, Math.max(1, limit));
        return Map.of("data", data);
    }

    @PostMapping("/chat/save")
    public Map<String, Object> chatSave(@RequestBody Map<String, Object> body) {
        Long userId = toLong(body.get("user_id"));
        String role = text(body.get("role"));
        String content = text(body.get("content"));
        List<ProductVO> products = parseProducts(text(body.get("products_json")));

        if ("user".equalsIgnoreCase(role)) {
            chatHistoryService.appendConversation(userId, content, null, List.of());
        } else {
            chatHistoryService.appendConversation(userId, null, content, products);
        }
        return Map.of("success", true, "message", "保存成功");
    }

    @GetMapping("/recommendations/{userId}")
    public Map<String, Object> recommendations(@PathVariable Long userId, @RequestParam(defaultValue = "10") Integer limit) {
        List<Map<String, Object>> history = chatHistoryService.listRecent(userId, 20);
        String latestUserNeed = history.stream()
            .filter(item -> "user".equalsIgnoreCase(text(item.get("role"))))
            .map(item -> text(item.get("content")))
            .filter(s -> !s.isBlank())
            .reduce((a, b) -> b)
            .orElse("");
        List<ProductVO> candidates = latestUserNeed.isBlank()
            ? shopProductService.listProducts(null, null)
            : shopProductService.listProducts(null, latestUserNeed);
        List<Map<String, Object>> data = candidates.stream()
            .sorted(Comparator.comparing(ProductVO::getSales, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(Math.max(1, limit))
            .map(p -> {
                Map<String, Object> m = new LinkedHashMap<>(toAgentProduct(p));
                m.put("recommend_reason", latestUserNeed.isBlank() ? "基于全站热销" : "基于你的最近咨询偏好");
                return m;
            })
            .toList();
        return Map.of("data", data);
    }

    private int safePage(Integer page) {
        return page == null ? 1 : Math.max(1, page);
    }

    private int safePageSize(Integer size) {
        return size == null ? 10 : Math.max(1, Math.min(100, size));
    }

    private String normalizeSort(String sortBy) {
        if (sortBy == null) {
            return null;
        }
        String s = sortBy.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "price_asc" -> "price_asc";
            case "price_desc" -> "price_desc";
            case "rating_desc", "rating" -> "rating";
            case "sales_desc" -> null;
            default -> null;
        };
    }

    private List<ProductVO> castProducts(Object obj) {
        if (obj instanceof List<?> list) {
            List<ProductVO> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof ProductVO p) {
                    out.add(p);
                }
            }
            return out;
        }
        return List.of();
    }

    private List<ProductVO> deduplicateProducts(List<ProductVO> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        Map<String, ProductVO> merged = new LinkedHashMap<>();
        for (ProductVO p : input) {
            if (p == null) {
                continue;
            }
            String key = normalizeName(p.getName()) + "|" + normalizeName(p.getBrand()) + "|" + String.valueOf(p.getPrice());
            ProductVO existing = merged.get(key);
            if (existing == null) {
                merged.put(key, p);
                continue;
            }
            int mergedStock = safeInt(existing.getStock()) + safeInt(p.getStock());
            existing.setStock(mergedStock);
            if (safeInt(p.getRating()) > safeInt(existing.getRating())) {
                existing.setRating(p.getRating());
            }
            if (safeInt(p.getSales()) > safeInt(existing.getSales())) {
                existing.setSales(p.getSales());
            }
            String existingImage = text(existing.getImage());
            if (existingImage.isBlank() && !text(p.getImage()).isBlank()) {
                existing.setImage(p.getImage());
            }
        }
        return new ArrayList<>(merged.values());
    }

    private String normalizeName(String value) {
        return text(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private int safeInt(Number n) {
        return n == null ? 0 : n.intValue();
    }

    private Map<String, Object> toAgentProduct(ProductVO p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("brand", text(p.getBrand()));
        m.put("price", p.getPrice());
        m.put("original_price", p.getOriginalPrice());
        m.put("stock", p.getStock());
        m.put("sales", p.getSales());
        m.put("rating", p.getRating());
        m.put("image_url", p.getImage());
        m.put("category", text(p.getCategory()));
        m.put("description", text(p.getDescription()));
        return m;
    }

    private Map<String, Object> toAgentOrder(Map<String, Object> source) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", text(source.get("orderId")));
        m.put("order_no", text(source.get("orderId")));
        m.put("order_status", text(source.get("orderStatus")));
        m.put("payment_status", text(source.get("paymentStatus")));
        m.put("total_amount", source.get("totalAmount"));
        m.put("pay_amount", source.get("payAmount"));
        m.put("items", source.getOrDefault("items", List.of()));
        return m;
    }

    private double discountRate(ProductVO p) {
        if (p == null || p.getPrice() == null || p.getOriginalPrice() == null || p.getOriginalPrice().signum() <= 0) {
            return 0.0;
        }
        BigDecimal diff = p.getOriginalPrice().subtract(p.getPrice());
        if (diff.signum() <= 0) {
            return 0.0;
        }
        return diff.divide(p.getOriginalPrice(), 4, RoundingMode.HALF_UP).doubleValue();
    }

    private String text(Object obj) {
        return obj == null ? "" : String.valueOf(obj);
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private int toInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        String s = String.valueOf(value).trim();
        if (s.isBlank()) {
            return fallback;
        }
        return Integer.parseInt(s);
    }

    private List<ProductVO> parseProducts(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.parseArray(json, ProductVO.class);
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
