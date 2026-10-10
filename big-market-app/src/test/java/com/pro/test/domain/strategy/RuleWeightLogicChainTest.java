package com.pro.test.domain.strategy;

import com.pro.domain.strategy.repository.IStrategyRepository;
import com.pro.domain.strategy.service.armory.IStrategyDispatch;
import com.pro.domain.strategy.service.rule.chain.impl.DefaultLogicChain;
import com.pro.domain.strategy.service.rule.chain.impl.RuleWeightLogicChain;
import com.pro.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class RuleWeightLogicChainTest {

    private static final Long STRATEGY_ID = 10008L;
    // Intentionally out of order: explicit intervals must be selected by their bounds.
    private static final String EXPLICIT_RULE_VALUE = "[100,+∞):1111,1112,1116 [0,20):101,1110,1111,1112,1113,1114,1115,1116 [20,100):1110,1111,1112,1116";
    private static final String EXPLICIT_RULE_0 = "[0,20):101,1110,1111,1112,1113,1114,1115,1116";
    private static final String EXPLICIT_RULE_20 = "[20,100):1110,1111,1112,1116";
    private static final String EXPLICIT_RULE_100 = "[100,+∞):1111,1112,1116";
    private static final String LEGACY_RULE_VALUE = "100:1111,1112,1116 0:101,1110,1111,1112,1113,1114,1115,1116 20:1110,1111,1112,1116";
    private static final String LEGACY_RULE_0 = "0:101,1110,1111,1112,1113,1114,1115,1116";
    private static final String LEGACY_RULE_20 = "20:1110,1111,1112,1116";
    private static final String LEGACY_RULE_100 = "100:1111,1112,1116";

    @Test
    public void explicitIntervalsUseLeftClosedRightOpenBoundaries() {
        IStrategyRepository repository = mock(IStrategyRepository.class);
        IStrategyDispatch strategyDispatch = mock(IStrategyDispatch.class);
        DefaultLogicChain defaultChain = mock(DefaultLogicChain.class);
        RuleWeightLogicChain weightChain = weightChain(repository, strategyDispatch, defaultChain);

        when(repository.queryStrategyRuleValue(STRATEGY_ID, DefaultChainFactory.LogicModel.RULE_WEIGHT.getCode()))
                .thenReturn(EXPLICIT_RULE_VALUE);
        when(strategyDispatch.getRandomAwardId(STRATEGY_ID, EXPLICIT_RULE_0)).thenReturn(101);
        when(strategyDispatch.getRandomAwardId(STRATEGY_ID, EXPLICIT_RULE_20)).thenReturn(1110);
        when(strategyDispatch.getRandomAwardId(STRATEGY_ID, EXPLICIT_RULE_100)).thenReturn(1111);

        assertAward(weightChain.logic("user-1", STRATEGY_ID, 0), 101);
        assertAward(weightChain.logic("user-1", STRATEGY_ID, 19), 101);
        assertAward(weightChain.logic("user-1", STRATEGY_ID, 20), 1110);
        assertAward(weightChain.logic("user-1", STRATEGY_ID, 21), 1110);
        assertAward(weightChain.logic("user-1", STRATEGY_ID, 99), 1110);
        assertAward(weightChain.logic("user-1", STRATEGY_ID, 100), 1111);
        assertAward(weightChain.logic("user-1", STRATEGY_ID, 101), 1111);
        assertAward(weightChain.logic("user-1", STRATEGY_ID, Integer.MAX_VALUE), 1111);
        assertAward(weightChain.logic("user-1", STRATEGY_ID, null), 101);

        verify(strategyDispatch, times(3)).getRandomAwardId(STRATEGY_ID, EXPLICIT_RULE_0);
        verify(strategyDispatch, times(3)).getRandomAwardId(STRATEGY_ID, EXPLICIT_RULE_20);
        verify(strategyDispatch, times(3)).getRandomAwardId(STRATEGY_ID, EXPLICIT_RULE_100);
        verifyNoInteractions(defaultChain);
    }

    @Test
    public void explicitGapAndBelowMinimumContinueToDefaultChain() {
        IStrategyRepository repository = mock(IStrategyRepository.class);
        IStrategyDispatch strategyDispatch = mock(IStrategyDispatch.class);
        DefaultLogicChain defaultChain = mock(DefaultLogicChain.class);
        RuleWeightLogicChain weightChain = weightChain(repository, strategyDispatch, defaultChain);
        DefaultChainFactory.StrategyAwardVO defaultAward = DefaultChainFactory.StrategyAwardVO.builder()
                .awardId(1199)
                .logicModel(DefaultChainFactory.LogicModel.RULE_DEFAULT.getCode())
                .build();

        when(repository.queryStrategyRuleValue(STRATEGY_ID, DefaultChainFactory.LogicModel.RULE_WEIGHT.getCode()))
                .thenReturn("[20,100):1110,1111,1112,1116");
        when(defaultChain.logic(anyString(), eq(STRATEGY_ID), anyInt())).thenReturn(defaultAward);

        for (Integer score : new Integer[]{19, 100, -1}) {
            DefaultChainFactory.StrategyAwardVO result = weightChain.logic("user-1", STRATEGY_ID, score);
            assertEquals(defaultAward, result);
        }

        verify(defaultChain).logic("user-1", STRATEGY_ID, 19);
        verify(defaultChain).logic("user-1", STRATEGY_ID, 100);
        verify(defaultChain).logic("user-1", STRATEGY_ID, -1);
        verify(strategyDispatch, never()).getRandomAwardId(eq(STRATEGY_ID), anyString());
    }

    @Test
    public void legacyThresholdsRemainSupported() {
        IStrategyRepository repository = mock(IStrategyRepository.class);
        IStrategyDispatch strategyDispatch = mock(IStrategyDispatch.class);
        DefaultLogicChain defaultChain = mock(DefaultLogicChain.class);
        RuleWeightLogicChain weightChain = weightChain(repository, strategyDispatch, defaultChain);

        when(repository.queryStrategyRuleValue(STRATEGY_ID, DefaultChainFactory.LogicModel.RULE_WEIGHT.getCode()))
                .thenReturn(LEGACY_RULE_VALUE);
        when(strategyDispatch.getRandomAwardId(STRATEGY_ID, LEGACY_RULE_0)).thenReturn(101);
        when(strategyDispatch.getRandomAwardId(STRATEGY_ID, LEGACY_RULE_20)).thenReturn(1110);
        when(strategyDispatch.getRandomAwardId(STRATEGY_ID, LEGACY_RULE_100)).thenReturn(1111);

        assertAward(weightChain.logic("user-1", STRATEGY_ID, 19), 101);
        assertAward(weightChain.logic("user-1", STRATEGY_ID, 20), 1110);
        assertAward(weightChain.logic("user-1", STRATEGY_ID, 99), 1110);
        assertAward(weightChain.logic("user-1", STRATEGY_ID, 100), 1111);
        assertAward(weightChain.logic("user-1", STRATEGY_ID, null), 101);

        verify(strategyDispatch, times(2)).getRandomAwardId(STRATEGY_ID, LEGACY_RULE_0);
        verify(strategyDispatch, times(2)).getRandomAwardId(STRATEGY_ID, LEGACY_RULE_20);
        verify(strategyDispatch).getRandomAwardId(STRATEGY_ID, LEGACY_RULE_100);
        verifyNoInteractions(defaultChain);
    }

    private void assertAward(DefaultChainFactory.StrategyAwardVO result, int awardId) {
        assertEquals(Integer.valueOf(awardId), result.getAwardId());
        assertEquals(DefaultChainFactory.LogicModel.RULE_WEIGHT.getCode(), result.getLogicModel());
    }

    private RuleWeightLogicChain weightChain(IStrategyRepository repository,
                                             IStrategyDispatch strategyDispatch,
                                             DefaultLogicChain defaultChain) {
        RuleWeightLogicChain weightChain = new RuleWeightLogicChain();
        ReflectionTestUtils.setField(weightChain, "repository", repository);
        ReflectionTestUtils.setField(weightChain, "strategyDispatch", strategyDispatch);
        weightChain.appendNext(defaultChain);
        return weightChain;
    }
}
