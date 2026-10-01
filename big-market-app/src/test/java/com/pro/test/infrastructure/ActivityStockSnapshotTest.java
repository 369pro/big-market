package com.pro.test.infrastructure;

import com.pro.infrastructure.adapter.repository.ActivityRepository;
import com.pro.infrastructure.dao.IRaffleActivitySkuDao;
import org.junit.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class ActivityStockSnapshotTest {
    @Test
    @SuppressWarnings("unchecked")
    public void repeatedSyncWritesAbsoluteStockAndMissingCacheDoesNotZeroDatabase() {
        RedissonClient redis = mock(RedissonClient.class);
        RBucket<String> bucket = mock(RBucket.class);
        IRaffleActivitySkuDao dao = mock(IRaffleActivitySkuDao.class);
        when(redis.<String>getBucket(anyString(), eq(StringCodec.INSTANCE))).thenReturn(bucket);
        when(bucket.get()).thenReturn("9", "9", null);
        ActivityRepository repository = new ActivityRepository();
        ReflectionTestUtils.setField(repository, "redissonClient", redis);
        ReflectionTestUtils.setField(repository, "raffleActivitySkuDao", dao);
        repository.updateActivitySkuStock(9901L);
        repository.updateActivitySkuStock(9901L);
        repository.updateActivitySkuStock(9901L);
        verify(dao, times(2)).syncActivitySkuStock(9901L, 9);
        verifyNoMoreInteractions(dao);
    }
}
