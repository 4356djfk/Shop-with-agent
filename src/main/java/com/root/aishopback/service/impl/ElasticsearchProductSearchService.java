package com.root.aishopback.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.root.aishopback.vo.ProductVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ElasticsearchProductSearchService {

    private final boolean enabled;
    private final String host;
    private final int port;
    private final String index;
    private final String username;
    private final String password;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ElasticsearchProductSearchService(
        @Value("${app.ai.es.enabled:false}") boolean enabled,
        @Value("${app.ai.es.host:localhost}") String host,
        @Value("${app.ai.es.port:9200}") int port,
        @Value("${app.ai.es.index:products_search}") String index,
        @Value("${app.ai.es.username:}") String username,
        @Value("${app.ai.es.password:}") String password
    ) {
        this.enabled = enabled;
        this.host = host;
        this.port = port;
        this.index = index;
        this.username = username;
        this.password = password;
    }

    public boolean isEnabled() {
        return enabled && host != null && !host.isBlank() && index != null && !index.isBlank();
    }

    public void reindexAll(List<ProductVO> products) {
        if (!isEnabled() || products == null) {
            return;
        }
        try {
            ensureIndex();
            StringBuilder bulk = new StringBuilder();
            for (ProductVO p : products) {
                if (p.getId() == null) {
                    continue;
                }
                bulk.append("{\"index\":{\"_index\":\"").append(index).append("\",\"_id\":\"").append(p.getId()).append("\"}}\n");
                String doc = objectMapper.writeValueAsString(Map.of(
                    "id", p.getId(),
                    "name", nvl(p.getName()),
                    "category", nvl(p.getCategory()),
                    "categoryPath", nvl(p.getCategoryPath()),
                    "categoryLevel1", nvl(p.getCategoryLevel1()),
                    "categoryLevel2", nvl(p.getCategoryLevel2()),
                    "categoryLevel3", nvl(p.getCategoryLevel3()),
                    "brand", nvl(p.getBrand()),
                    "description", compact(nvl(p.getDescription()), 1000),
                    "tags", p.getTags() == null ? List.of() : p.getTags()
                ));
                bulk.append(doc).append("\n");
            }
            if (bulk.isEmpty()) {
                return;
            }
            request("POST", "/" + index + "/_bulk?refresh=true", bulk.toString(), "application/x-ndjson");
        } catch (Exception ignored) {
            // Keep service non-blocking for the chat path.
        }
    }

    public List<Long> searchProductIds(String query, int size) {
        if (!isEnabled() || query == null || query.isBlank()) {
            return List.of();
        }
        try {
            String escaped = query.replace("\"", "\\\"");
            String body = """
                {
                  "size": %d,
                  "_source": false,
                  "query": {
                    "bool": {
                      "should": [
                        {
                          "multi_match": {
                            "query": "%s",
                            "fields": ["name^5", "categoryPath^5", "categoryLevel3^5", "categoryLevel2^4", "categoryLevel1^3", "category^3", "brand^3", "tags^3", "description^1"],
                            "fuzziness": "AUTO"
                          }
                        },
                        {
                          "simple_query_string": {
                            "query": "%s",
                            "fields": ["name^5", "categoryPath^5", "categoryLevel3^5", "categoryLevel2^4", "categoryLevel1^3", "category^3", "brand^3", "tags^3", "description^1"],
                            "default_operator": "and"
                          }
                        }
                      ],
                      "minimum_should_match": 1
                    }
                  }
                }
                """.formatted(Math.max(1, size), escaped, escaped);
            String response = request("POST", "/" + index + "/_search", body, "application/json");
            if (response == null || response.isBlank()) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(response);
            JsonNode hits = root.path("hits").path("hits");
            if (!hits.isArray()) {
                return List.of();
            }
            List<Long> ids = new ArrayList<>();
            for (JsonNode hit : hits) {
                String idText = hit.path("_id").asText("");
                if (idText.isBlank()) {
                    continue;
                }
                try {
                    ids.add(Long.parseLong(idText));
                } catch (NumberFormatException ignored) {
                }
            }
            return ids;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private void ensureIndex() throws Exception {
        String mapping = """
            {
              "mappings": {
                "properties": {
                  "id": {"type": "long"},
                  "name": {"type": "text"},
                  "category": {"type": "text"},
                  "categoryPath": {"type": "text"},
                  "categoryLevel1": {"type": "text"},
                  "categoryLevel2": {"type": "text"},
                  "categoryLevel3": {"type": "text"},
                  "brand": {"type": "text"},
                  "description": {"type": "text"},
                  "tags": {"type": "text"}
                }
              }
            }
            """;
        request("PUT", "/" + index, mapping, "application/json");
    }

    private String request(String method, String path, String body, String contentType) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create("http://" + host + ":" + port + path))
            .timeout(Duration.ofSeconds(4))
            .header("Content-Type", contentType);

        if (username != null && !username.isBlank()) {
            String raw = username + ":" + (password == null ? "" : password);
            String auth = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + auth);
        }

        HttpRequest request = builder.method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body)).build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return response.body();
        }
        if ("PUT".equals(method) && status == 400 && response.body() != null && response.body().toLowerCase(Locale.ROOT).contains("resource_already_exists_exception")) {
            return response.body();
        }
        throw new IllegalStateException("Elasticsearch request failed: " + status);
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private String compact(String text, int max) {
        String t = nvl(text).replaceAll("\\s+", " ").trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max);
    }
}
