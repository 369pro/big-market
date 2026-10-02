package com.pro.test.infrastructure.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pro.infrastructure.elasticsearch.ElasticSearchUserRaffleOrderDao;
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
                .pageNo(102)
                .pageSize(100)
                .build());
    }
}
