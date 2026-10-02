package com.pro.test.domain.strategy;

import com.pro.domain.strategy.model.entity.StrategyAwardEntity;
import com.pro.domain.strategy.model.entity.StrategyEntity;
import com.pro.domain.strategy.repository.IStrategyRepository;
import com.pro.domain.strategy.service.armory.AbstractStrategyAlgorithm;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;

import static org.mockito.Mockito.*;

public class StrategyArmoryStockWarmupTest {

    @Test
    public void repeatedArmoryUsesRemainingStockAndDoesNotRestoreRedisCounter() {
        IStrategyRepository repository = mock(IStrategyRepository.class);
        TestArmory armory = new TestArmory();
        ReflectionTestUtils.setField(armory, "repository", repository);
        StrategyAwardEntity award = StrategyAwardEntity.builder()
                .strategyId(2001L)
                .awardId(101)
                .awardCount(100)
                .awardCountSurplus(37)
                .awardRate(BigDecimal.ONE)
                .build();
        when(repository.queryStrategyAwardList(2001L)).thenReturn(Collections.singletonList(award));
        when(repository.queryStrategyEntityByStrategyId(2001L)).thenReturn(StrategyEntity.builder().build());

        armory.assembleLotteryStrategy(2001L);
        armory.assembleLotteryStrategy(2001L);

        verify(repository, times(2)).cacheStrategyAwardCount(anyString(), eq(37));
        verify(repository, never()).cacheStrategyAwardCount(anyString(), eq(100));
    }

    private static class TestArmory extends AbstractStrategyAlgorithm {
        @Override
        protected void armoryAlgorithm(String key, java.util.List<StrategyAwardEntity> strategyAwardEntities) {
        }

        @Override
        protected Integer dispatchAlgorithm(String key) {
            return 0;
        }
    }
}
