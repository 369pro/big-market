package com.pro.infrastructure.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pro.model.valobj.ESUserRaffleOrderPageVO;
import com.pro.model.valobj.ESUserRaffleOrderQueryVO;
import com.pro.model.valobj.ESUserRaffleOrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * 通过 Elasticsearch REST Search API 查询用户抽奖订单。
 *
 * <p>这里不使用 x-pack SQL JDBC。SQL JDBC 在当前 ES 集群授权下不可用，
 * 而 REST _search 是订单读模型需要的基础查询接口。</p>
 */
@Slf4j
@Repository
public class ElasticSearchUserRaffleOrderDao implements IElasticSearchUserRaffleOrderDao {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_INDEX = "big_market.user_raffle_order";
    private static final DateTimeFormatter LOCAL_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter LOCAL_DATE_TIME_MILLIS_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

    @Value("${spring.elasticsearch.rest.uris:}")
    private String restUri;
    @Value("${spring.elasticsearch.datasource.url:}")
    private String jdbcUri;
    @Value("${spring.elasticsearch.order-index:big_market.user_raffle_order}")
    private String orderIndex;
    @Value("${spring.elasticsearch.rest.username:}")
    private String restUsername;
    @Value("${spring.elasticsearch.rest.password:}")
    private String restPassword;
    @Value("${spring.elasticsearch.datasource.username:}")
    private String jdbcUsername;
    @Value("${spring.elasticsearch.datasource.password:}")
    private String jdbcPassword;

    @Override
    public ESUserRaffleOrderPageVO queryUserRaffleOrderPage(ESUserRaffleOrderQueryVO query) {
        ESUserRaffleOrderQueryVO actualQuery = query == null ? defaultQuery() : query;
        actualQuery.validate();

        String requestBody = buildSearchBody(actualQuery);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(resolveSearchUri()))
                .timeout(java.time.Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));

        String username = firstNonBlank(restUsername, jdbcUsername);
        String password = firstNonBlank(restPassword, jdbcPassword);
        if (!username.isEmpty() && !password.isEmpty()) {
            String credentials = username + ":" + password;
            requestBuilder.header("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }

        final HttpResponse<String> response;
        try {
            response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Elasticsearch order search request failed", e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Elasticsearch order search returned HTTP " + response.statusCode());
        }

        return parseSearchResponse(response.body(), actualQuery);
    }

    /**
     * 构造 REST _search 请求体，单独暴露给单元测试以验证过滤、分页和排序契约。
     */
    public static String buildSearchBody(ESUserRaffleOrderQueryVO query) {
        ESUserRaffleOrderQueryVO actualQuery = query == null ? defaultQuery() : query;
        actualQuery.validate();

        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        ObjectNode bool = root.putObject("query").putObject("bool");
        ArrayNode filters = bool.putArray("filter");
        addTermFilter(filters, "_user_id", actualQuery.getUserId());
        addTermFilter(filters, "_activity_id", actualQuery.getActivityId());
        if (hasText(actualQuery.getActivityName())) {
            ObjectNode matchPhrase = filters.addObject().putObject("match_phrase");
            matchPhrase.put("_activity_name", actualQuery.getActivityName());
        }
        addTermFilter(filters, "_order_id", actualQuery.getOrderId());
        addTermFilter(filters, "_order_state", actualQuery.getOrderState());
        addTermFilter(filters, "_strategy_id", actualQuery.getStrategyId());

        root.put("from", actualQuery.offset());
        root.put("size", actualQuery.effectivePageSize());
        root.put("track_total_hits", true);

        ArrayNode sort = root.putArray("sort");
        sort.addObject().putObject("_order_time").put("order", "desc");
        sort.addObject().putObject("_update_time").put("order", "desc");
        sort.addObject().putObject("_order_id").put("order", "desc");

        ObjectNode source = root.putObject("_source");
        ArrayNode includes = source.putArray("includes");
        includes.add("_user_id");
        includes.add("_activity_id");
        includes.add("_activity_name");
        includes.add("_strategy_id");
        includes.add("_order_id");
        includes.add("_order_time");
        includes.add("_order_state");
        includes.add("_create_time");
        includes.add("_update_time");

        try {
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to build Elasticsearch order search request", e);
        }
    }

    static ESUserRaffleOrderPageVO parseSearchResponse(String responseBody, ESUserRaffleOrderQueryVO query) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            JsonNode timedOut = root.get("timed_out");
            if (timedOut != null && timedOut.isBoolean() && timedOut.booleanValue()) {
                throw new IllegalStateException("Elasticsearch order search timed out");
            }
            JsonNode shards = root.get("_shards");
            JsonNode failedShards = shards == null ? null : shards.get("failed");
            if (failedShards != null && failedShards.isNumber() && failedShards.intValue() > 0) {
                throw new IllegalStateException("Elasticsearch order search returned failed shards");
            }
            JsonNode hits = root.path("hits");
            JsonNode totalNode = hits.path("total");
            if (hits.isMissingNode() || totalNode.isMissingNode()) {
                throw new IllegalStateException("Elasticsearch order search response has no hits.total");
            }

            JsonNode totalValue = totalNode.isNumber() ? totalNode : totalNode.path("value");
            long total = totalValue.isNumber() ? totalValue.longValue() : -1L;
            if (total < 0) {
                throw new IllegalStateException("Elasticsearch order search response has invalid hits.total");
            }

            List<ESUserRaffleOrderVO> list;
            JsonNode hitNodes = hits.path("hits");
            if (!hitNodes.isArray()) {
                throw new IllegalStateException("Elasticsearch order search response has no hits array");
            }
            java.util.ArrayList<ESUserRaffleOrderVO> orders = new java.util.ArrayList<>();
            for (JsonNode hit : hitNodes) {
                JsonNode source = hit.path("_source");
                if (!source.isObject()) {
                    throw new IllegalStateException("Elasticsearch order search hit has no _source");
                }
                orders.add(toOrder(source));
            }
            list = orders;

            return ESUserRaffleOrderPageVO.builder()
                    .list(list)
                    .total(total)
                    .pageNo(query.effectivePageNo())
                    .pageSize(query.effectivePageSize())
                    .build();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse Elasticsearch order search response", e);
        }
    }

    private static ESUserRaffleOrderVO toOrder(JsonNode source) {
        return ESUserRaffleOrderVO.builder()
                .userId(textValue(source, "_user_id"))
                .activityId(longValue(source, "_activity_id"))
                .activityName(textValue(source, "_activity_name"))
                .strategyId(longValue(source, "_strategy_id"))
                .orderId(textValue(source, "_order_id"))
                .orderTime(dateValue(source, "_order_time"))
                .orderState(textValue(source, "_order_state"))
                .createTime(dateValue(source, "_create_time"))
                .updateTime(dateValue(source, "_update_time"))
                .build();
    }

    private String resolveSearchUri() {
        String base = firstNonBlank(restUri, jdbcUri, "http://localhost:9200");
        if (base.startsWith("jdbc:es://")) {
            base = base.substring("jdbc:es://".length());
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String index = firstNonBlank(orderIndex, DEFAULT_INDEX);
        return base + "/" + index + "/_search";
    }

    private static ESUserRaffleOrderQueryVO defaultQuery() {
        return ESUserRaffleOrderQueryVO.builder()
                .pageNo(ESUserRaffleOrderQueryVO.DEFAULT_PAGE_NO)
                .pageSize(ESUserRaffleOrderQueryVO.DEFAULT_PAGE_SIZE)
                .build();
    }

    private static void addTermFilter(ArrayNode filters, String field, Object value) {
        if (value == null || (value instanceof String && !hasText((String) value))) {
            return;
        }
        ObjectNode term = filters.addObject().putObject("term");
        if (value instanceof Number) {
            term.put(field, ((Number) value).longValue());
        } else {
            term.put(field, value.toString());
        }
    }

    private static String textValue(JsonNode source, String field) {
        JsonNode value = source.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Long longValue(JsonNode source, String field) {
        JsonNode value = source.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.longValue();
        }
        try {
            return Long.valueOf(value.asText());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Elasticsearch order field " + field + " is not numeric", e);
        }
    }

    private static Date dateValue(JsonNode source, String field) {
        JsonNode value = source.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            long epoch = value.longValue();
            return new Date(epoch < 100_000_000_000L ? epoch * 1_000L : epoch);
        }

        String text = value.asText();
        try {
            return Date.from(Instant.parse(text));
        } catch (DateTimeParseException ignored) {
            // Continue with the common MySQL date formats used by the Canal document.
        }
        try {
            return Date.from(OffsetDateTime.parse(text).toInstant());
        } catch (DateTimeParseException ignored) {
            // Continue with a timezone-less local date-time.
        }
        try {
            return Date.from(LocalDateTime.parse(text, LOCAL_DATE_TIME_FORMATTER)
                    .atZone(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException ignored) {
            // Continue with the millisecond-precision local date-time format.
        }
        try {
            return Date.from(LocalDateTime.parse(text, LOCAL_DATE_TIME_MILLIS_FORMATTER)
                    .atZone(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException ignored) {
            // Continue with a date-only value.
        }
        try {
            return Date.from(LocalDate.parse(text).atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException e) {
            throw new IllegalStateException("Elasticsearch order field " + field + " is not a supported date", e);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (hasText(first)) {
            return first.trim();
        }
        if (hasText(second)) {
            return second.trim();
        }
        return fallback;
    }

    private static String firstNonBlank(String first, String second) {
        return firstNonBlank(first, second, "");
    }
}
