package com.pro.test.domain.strategy;

import com.pro.domain.strategy.model.entity.StrategyAwardEntity;
import com.pro.domain.strategy.repository.IStrategyRepository;
import com.pro.domain.strategy.service.armory.StrategyArmoryDispatch;
import com.pro.domain.strategy.service.armory.algorithm.AbstractAlgorithm;
import com.pro.domain.strategy.service.armory.algorithm.IAlgorithm;
import com.pro.domain.strategy.service.armory.algorithm.impl.OLogNAlgorithm;
import com.pro.types.common.Constants;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AlgorithmRegressionTest {

    @Test
    public void shouldStoreOLogNRateRangeAsIntervalUpperBoundPlusOne() {
        IStrategyRepository repository = mock(IStrategyRepository.class);
        OLogNAlgorithm oLogNAlgorithm = new OLogNAlgorithm();
        ReflectionTestUtils.setField(oLogNAlgorithm, "repository", repository);
        ReflectionTestUtils.setField(oLogNAlgorithm, "threadPoolExecutor", mock(ThreadPoolExecutor.class));

        List<StrategyAwardEntity> awards = Arrays.asList(
                StrategyAwardEntity.builder().awardId(101).awardRate(new BigDecimal("0.5")).build(),
                StrategyAwardEntity.builder().awardId(102).awardRate(new BigDecimal("0.5")).build()
        );

        oLogNAlgorithm.armoryAlgorithm("100001", awards, new BigDecimal("100"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Map<Integer, Integer>, Integer>> tableCaptor = ArgumentCaptor.forClass(Map.class);
        verify(repository).storeStrategyAwardSearchRateTable(eq("100001"), eq(100), tableCaptor.capture());

        Assert.assertEquals(2, tableCaptor.getValue().size());
    }

    @Test
    public void shouldReadPrefixedRateTableKeyInOLogNDispatch() {
        IStrategyRepository repository = mock(IStrategyRepository.class);
        OLogNAlgorithm oLogNAlgorithm = new OLogNAlgorithm();
        ReflectionTestUtils.setField(oLogNAlgorithm, "repository", repository);
        ReflectionTestUtils.setField(oLogNAlgorithm, "threadPoolExecutor", mock(ThreadPoolExecutor.class));

        Map<Map<String, Integer>, Integer> rateTable = new LinkedHashMap<>();
        Map<String, Integer> range = new HashMap<>();
        range.put("0", 99);
        rateTable.put(range, 101);

        when(repository.getRateRange("100001")).thenReturn(100);
//        when(repository.getMap(Constants.RedisKey.STRATEGY_RATE_TABLE_KEY + "100001")).thenReturn(rateTable);

        Integer awardId = oLogNAlgorithm.dispatchAlgorithm("100001");

        Assert.assertEquals(Integer.valueOf(101), awardId);
        verify(repository).getMap(Constants.RedisKey.STRATEGY_RATE_TABLE_KEY + "100001");
        verify(repository, never()).getMap("100001");
    }

    @Test
    public void shouldHitLastIntervalInBinarySearch() {
        OLogNAlgorithm oLogNAlgorithm = new OLogNAlgorithm();

        // 大于16小于等于32，触发二分查找分支的典型区间结构
        Map<Map<String, Integer>, Integer> table = new LinkedHashMap<>();
        int start = 0;
        for (int i = 0; i < 20; i++) {
            Map<String, Integer> range = new HashMap<>();
            int end = start + 4;
            range.put(String.valueOf(start), end);
            table.put(range, 100 + i);
            start = end + 1;
        }

        // 命中最后一个区间，覆盖 left == right 边界
        Integer awardId = ReflectionTestUtils.invokeMethod(oLogNAlgorithm, "binarySearch", 99, table);

        Assert.assertEquals(Integer.valueOf(119), awardId);
    }

    @Test
    public void shouldSwitchAlgorithmByEstimatedTableSize() {
        IStrategyRepository repository = mock(IStrategyRepository.class);
        IAlgorithm o1Algorithm = mock(IAlgorithm.class);
        IAlgorithm oLogNAlgorithm = mock(IAlgorithm.class);

        Map<String, IAlgorithm> algorithmMap = new HashMap<>();
        algorithmMap.put(AbstractAlgorithm.Algorithm.O1.getKey(), o1Algorithm);
        algorithmMap.put(AbstractAlgorithm.Algorithm.OLogN.getKey(), oLogNAlgorithm);

        StrategyArmoryDispatch dispatch = new StrategyArmoryDispatch(algorithmMap);
        ReflectionTestUtils.setField(dispatch, "repository", repository);

        List<StrategyAwardEntity> smallTableAwards = Arrays.asList(
                StrategyAwardEntity.builder().awardId(101).awardRate(new BigDecimal("0.7")).build(),
                StrategyAwardEntity.builder().awardId(102).awardRate(new BigDecimal("0.3")).build()
        );

        ReflectionTestUtils.invokeMethod(dispatch, "armoryAlgorithm", "100001", smallTableAwards);
        verify(o1Algorithm).armoryAlgorithm(eq("100001"), eq(smallTableAwards), any(BigDecimal.class));

        List<StrategyAwardEntity> largeTableAwards = Arrays.asList(
                StrategyAwardEntity.builder().awardId(201).awardRate(new BigDecimal("0.999999")).build(),
                StrategyAwardEntity.builder().awardId(202).awardRate(new BigDecimal("0.000001")).build()
        );

        ReflectionTestUtils.invokeMethod(dispatch, "armoryAlgorithm", "100002", largeTableAwards);
        verify(oLogNAlgorithm).armoryAlgorithm(eq("100002"), eq(largeTableAwards), any(BigDecimal.class));
    }
}
