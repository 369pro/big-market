package com.pro.infrastructure.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.model.valobj.ESUserRaffleOrderQueryVO;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 验证订单 REST 查询体的分页、过滤和最新排序契约，不依赖在线 Elasticsearch。
 */
public class ElasticSearchUserRaffleOrderDaoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void shouldBuildFilteredPagedSearchRequest() throws Exception {
        ESUserRaffleOrderQueryVO query = ESUserRaffleOrderQueryVO.builder()
                .userId("u-100")
                .activityId(42L)
                .activityName("春季活动")
                .orderId("order-1")
                .orderState("create")
                .strategyId(7L)
                .pageNo(3)
                .pageSize(20)
                .build();

        JsonNode body = objectMapper.readTree(ElasticSearchUserRaffleOrderDao.buildSearchBody(query));

        assertEquals(40, body.get("from").asInt());
        assertEquals(20, body.get("size").asInt());
        assertTrue(body.get("track_total_hits").asBoolean());
        assertEquals(6, body.path("query").path("bool").path("filter").size());
        assertEquals("desc", body.path("sort").get(0).path("_order_time").path("order").asText());
        assertEquals("desc", body.path("sort").get(1).path("_update_time").path("order").asText());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectDeepPagination() {
        ElasticSearchUserRaffleOrderDao.buildSearchBody(ESUserRaffleOrderQueryVO.builder()
                .pageNo(101)
                .pageSize(100)
                .build());
    }

    @Test
    public void shouldAllowPaginationAtResultWindowBoundary() throws Exception {
        JsonNode body = objectMapper.readTree(ElasticSearchUserRaffleOrderDao.buildSearchBody(
                ESUserRaffleOrderQueryVO.builder().pageNo(100).pageSize(100).build()));

        assertEquals(9_900, body.get("from").asInt());
        assertEquals(100, body.get("size").asInt());
    }

    @Test
    public void shouldRejectTimedOutSearchResponse() {
        String response = "{\"timed_out\":true,\"_shards\":{\"failed\":0},"
                + "\"hits\":{\"total\":{\"value\":1,\"relation\":\"eq\"},\"hits\":[]}}";

        try {
            ElasticSearchUserRaffleOrderDao.parseSearchResponse(response, ESUserRaffleOrderQueryVO.builder()
                    .pageNo(1)
                    .pageSize(20)
                    .build());
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("timed out"));
            return;
        }
        throw new AssertionError("timed_out search response must be rejected");
    }

    @Test
    public void shouldRejectFailedShardSearchResponse() {
        String response = "{\"timed_out\":false,\"_shards\":{\"failed\":1},"
                + "\"hits\":{\"total\":{\"value\":1,\"relation\":\"eq\"},\"hits\":[]}}";

        try {
            ElasticSearchUserRaffleOrderDao.parseSearchResponse(response, ESUserRaffleOrderQueryVO.builder()
                    .pageNo(1)
                    .pageSize(20)
                    .build());
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("failed shards"));
            return;
        }
        throw new AssertionError("failed-shard search response must be rejected");
    }
}
