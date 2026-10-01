package com.pro.test.infrastructure;

import cn.bugstack.middleware.db.router.DBContextHolder;
import cn.bugstack.middleware.db.router.DBRouterConfig;
import cn.bugstack.middleware.db.router.strategy.IDBRouterStrategy;
import cn.bugstack.middleware.db.router.strategy.impl.DBRouterStrategyHashCode;
import com.pro.infrastructure.adapter.repository.AwardFeedRepository;
import com.pro.infrastructure.dao.IAwardFeedDao;
import com.pro.infrastructure.dao.po.UserAwardRecord;
import com.pro.model.valobj.AwardFeedRecordVO;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AwardFeedRepositoryTest {

    private static final Long ACTIVITY_ID = 100401L;

    private DBRouterConfig routerConfig;
    private IDBRouterStrategy dbRouter;
    private IAwardFeedDao awardFeedDao;

    @Before
    public void setUp() {
        routerConfig = new DBRouterConfig(2, 2, "userId");
        dbRouter = spy(new DBRouterStrategyHashCode(routerConfig));
        awardFeedDao = mock(IAwardFeedDao.class);
    }

    @After
    public void clearRoutingContext() {
        DBContextHolder.clearDBKey();
        DBContextHolder.clearTBKey();
    }

    @Test
    public void mergesShardsDeduplicatesCapsAndCachesTheSnapshot() {
        List<UserAwardRecord> records = new ArrayList<>();
        for (int i = 0; i < 125; i++) {
            records.add(record("user-" + i, "order-" + i, 1_700_000_000_000L + i * 1_000L));
        }
        // The same logical record appearing on another shard must be emitted once.
        records.add(record("user-124", "order-124", 1_700_000_999_000L));
        when(awardFeedDao.queryRecentAwards(anyLong(), anyInt())).thenReturn(records);

        AwardFeedRepository repository = repository();
        DBContextHolder.setDBKey("prior-db");
        DBContextHolder.setTBKey("prior-tb");

        List<AwardFeedRecordVO> first = repository.queryRecentAwards(ACTIVITY_ID);
        List<AwardFeedRecordVO> second = repository.queryRecentAwards(ACTIVITY_ID);

        assertEquals(100, first.size());
        assertEquals("order-124", first.get(0).getOrderId());
        assertEquals("order-123", first.get(1).getOrderId());
        assertEquals(100, first.stream().map(AwardFeedRecordVO::getOrderId).distinct().count());
        assertEquals(first, second);
        assertEquals("user-124", first.get(0).getUserId());
        assertEquals("prior-db", DBContextHolder.getDBKey());
        assertEquals("prior-tb", DBContextHolder.getTBKey());
        verify(awardFeedDao, times(4)).queryRecentAwards(ACTIVITY_ID, 100);
        verify(dbRouter, times(2)).setDBKey(org.mockito.ArgumentMatchers.intThat(value -> value >= 1 && value <= 2));
        verify(dbRouter, times(4)).setTBKey(org.mockito.ArgumentMatchers.intThat(value -> value >= 0 && value <= 1));
        verify(dbRouter).clear();
    }

    @Test
    public void shardFailureClearsAndRestoresRoutingWithoutReturningPartialData() {
        when(awardFeedDao.queryRecentAwards(anyLong(), anyInt()))
                .thenThrow(new IllegalStateException("shard unavailable"));
        AwardFeedRepository repository = repository();
        DBContextHolder.setDBKey("prior-db");
        DBContextHolder.setTBKey("prior-tb");

        try {
            repository.queryRecentAwards(ACTIVITY_ID + 1);
            fail("expected shard failure");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("award feed")
                    || expected.getMessage().contains("shard unavailable")
                    || expected.getCause() != null);
        }

        assertEquals("prior-db", DBContextHolder.getDBKey());
        assertEquals("prior-tb", DBContextHolder.getTBKey());
        verify(dbRouter).clear();
    }

    private AwardFeedRepository repository() {
        AwardFeedRepository repository = new AwardFeedRepository();
        ReflectionTestUtils.setField(repository, "routerConfig", routerConfig);
        ReflectionTestUtils.setField(repository, "dbRouter", dbRouter);
        ReflectionTestUtils.setField(repository, "awardFeedDao", awardFeedDao);
        return repository;
    }

    private UserAwardRecord record(String userId, String orderId, long awardTime) {
        UserAwardRecord record = new UserAwardRecord();
        record.setUserId(userId);
        record.setOrderId(orderId);
        record.setActivityId(ACTIVITY_ID);
        record.setAwardId(101);
        record.setAwardTitle("award");
        record.setAwardTime(new Date(awardTime));
        record.setAwardState("completed");
        return record;
    }
}
