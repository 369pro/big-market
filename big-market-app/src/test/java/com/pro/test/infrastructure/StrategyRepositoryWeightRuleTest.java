package com.pro.test.infrastructure;

import com.pro.domain.strategy.model.valobj.RuleWeightVO;
import com.pro.infrastructure.adapter.repository.StrategyRepository;
import com.pro.infrastructure.dao.IStrategyAwardDao;
import com.pro.infrastructure.dao.IStrategyRuleDao;
import com.pro.infrastructure.dao.po.StrategyAward;
import com.pro.infrastructure.redis.IRedisService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StrategyRepositoryWeightRuleTest {

    @Test
    public void queryAwardRuleWeightUsesExplicitLowerBounds() {
        String ruleValue = "[100,+∞):1111,1112 [0,20):101,1110 [20,100):1110,1111";
        StrategyRepository repository = repository(ruleValue);

        List<RuleWeightVO> result = repository.queryAwardRuleWeight(10008L);

        assertEquals(3, result.size());
        assertEquals(Integer.valueOf(100), result.get(0).getWeight());
        assertEquals(Integer.valueOf(0), result.get(1).getWeight());
        assertEquals(Integer.valueOf(20), result.get(2).getWeight());
        assertEquals(List.of(1111, 1112), result.get(0).getAwardIds());
        assertEquals(List.of(101, 1110), result.get(1).getAwardIds());
        assertEquals(List.of(1110, 1111), result.get(2).getAwardIds());
    }

    private StrategyRepository repository(String ruleValue) {
        StrategyRepository repository = new StrategyRepository();
        IRedisService redisService = mock(IRedisService.class);
        IStrategyRuleDao strategyRuleDao = mock(IStrategyRuleDao.class);
        IStrategyAwardDao strategyAwardDao = mock(IStrategyAwardDao.class);

        when(redisService.getValue(anyString())).thenReturn(null);
        when(strategyRuleDao.queryStrategyRuleValue(any())).thenReturn(ruleValue);
        when(strategyAwardDao.queryStrategyAward(any())).thenAnswer(invocation -> {
            com.pro.infrastructure.dao.po.StrategyAward request = invocation.getArgument(0);
            StrategyAward award = new StrategyAward();
            award.setStrategyId(request.getStrategyId());
            award.setAwardId(request.getAwardId());
            award.setAwardTitle("award-" + request.getAwardId());
            return award;
        });

        ReflectionTestUtils.setField(repository, "redisService", redisService);
        ReflectionTestUtils.setField(repository, "strategyRuleDao", strategyRuleDao);
        ReflectionTestUtils.setField(repository, "strategyAwardDao", strategyAwardDao);
        return repository;
    }
}
