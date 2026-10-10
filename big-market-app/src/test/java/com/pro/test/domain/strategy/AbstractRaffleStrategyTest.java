package com.pro.test.domain.strategy;

import com.pro.domain.strategy.model.entity.RaffleAwardEntity;
import com.pro.domain.strategy.model.entity.RaffleFactorEntity;
import com.pro.domain.strategy.model.entity.StrategyAwardEntity;
import com.pro.domain.strategy.repository.IStrategyRepository;
import com.pro.domain.strategy.service.AbstractRaffleStrategy;
import com.pro.domain.strategy.service.armory.IStrategyDispatch;
import com.pro.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import com.pro.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AbstractRaffleStrategyTest {

    private static final Long STRATEGY_ID = 10008L;

    private IStrategyRepository repository;
    private TestRaffleStrategy raffleStrategy;

    @Before
    public void setUp() {
        repository = mock(IStrategyRepository.class);
        when(repository.queryStrategyAwardEntity(anyLong(), anyInt()))
                .thenReturn(StrategyAwardEntity.builder()
                        .awardTitle("test award")
                        .sort(1)
                        .build());

        raffleStrategy = new TestRaffleStrategy(
                repository,
                mock(IStrategyDispatch.class),
                mock(DefaultChainFactory.class),
                mock(DefaultTreeFactory.class));
    }

    @Test
    public void weightResultMustPassThroughRuleTree() {
        raffleStrategy.chainAward = chainAward(101, "weight-config", DefaultChainFactory.LogicModel.RULE_WEIGHT);
        raffleStrategy.treeAward = treeAward(102, "tree-config");

        RaffleAwardEntity result = raffleStrategy.performRaffle(factor());

        assertEquals(Integer.valueOf(102), result.getAwardId());
        assertEquals("tree-config", result.getAwardConfig());
        assertEquals(1, raffleStrategy.treeCalls);
    }

    @Test
    public void weightRuleTreeStockExceptionMustPropagate() {
        IllegalStateException expected = new IllegalStateException("stock unavailable");
        raffleStrategy.chainAward = chainAward(101, "weight-config", DefaultChainFactory.LogicModel.RULE_WEIGHT);
        raffleStrategy.treeFailure = expected;

        try {
            raffleStrategy.performRaffle(factor());
            fail("Expected the stock exception from the rule tree");
        } catch (IllegalStateException actual) {
            assertSame(expected, actual);
        }

        assertEquals(1, raffleStrategy.treeCalls);
    }

    @Test
    public void defaultResultMustPassThroughRuleTree() {
        raffleStrategy.chainAward = chainAward(101, "default-config", DefaultChainFactory.LogicModel.RULE_DEFAULT);
        raffleStrategy.treeAward = treeAward(103, "default-tree-config");

        RaffleAwardEntity result = raffleStrategy.performRaffle(factor());

        assertEquals(Integer.valueOf(103), result.getAwardId());
        assertEquals("default-tree-config", result.getAwardConfig());
        assertEquals(1, raffleStrategy.treeCalls);
    }

    @Test
    public void blacklistResultKeepsDirectReturnSemantics() {
        raffleStrategy.chainAward = chainAward(104, "blacklist-config", DefaultChainFactory.LogicModel.RULE_BLACKLIST);

        RaffleAwardEntity result = raffleStrategy.performRaffle(factor());

        assertEquals(Integer.valueOf(104), result.getAwardId());
        assertEquals("blacklist-config", result.getAwardConfig());
        assertEquals(0, raffleStrategy.treeCalls);
    }

    private RaffleFactorEntity factor() {
        return RaffleFactorEntity.builder()
                .userId("user-1")
                .strategyId(STRATEGY_ID)
                .userScore(1)
                .endDateTime(new Date())
                .build();
    }

    private DefaultChainFactory.StrategyAwardVO chainAward(
            Integer awardId,
            String awardRuleValue,
            DefaultChainFactory.LogicModel logicModel) {
        return DefaultChainFactory.StrategyAwardVO.builder()
                .awardId(awardId)
                .awardRuleValue(awardRuleValue)
                .logicModel(logicModel.getCode())
                .build();
    }

    private DefaultTreeFactory.StrategyAwardVO treeAward(Integer awardId, String awardRuleValue) {
        return DefaultTreeFactory.StrategyAwardVO.builder()
                .awardId(awardId)
                .awardRuleValue(awardRuleValue)
                .build();
    }

    private static class TestRaffleStrategy extends AbstractRaffleStrategy {
        private DefaultChainFactory.StrategyAwardVO chainAward;
        private DefaultTreeFactory.StrategyAwardVO treeAward;
        private RuntimeException treeFailure;
        private int treeCalls;

        private TestRaffleStrategy(IStrategyRepository repository,
                                   IStrategyDispatch strategyDispatch,
                                   DefaultChainFactory defaultChainFactory,
                                   DefaultTreeFactory defaultTreeFactory) {
            super(repository, strategyDispatch, defaultChainFactory, defaultTreeFactory);
        }

        @Override
        public DefaultChainFactory.StrategyAwardVO raffleLogicChain(String userId, Long strategyId, Integer userScore) {
            return chainAward;
        }

        @Override
        public DefaultTreeFactory.StrategyAwardVO raffleLogicTree(String userId, Long strategyId,
                                                                   Integer awardId, Date endDateTime) {
            treeCalls++;
            if (treeFailure != null) {
                throw treeFailure;
            }
            return treeAward;
        }
    }
}
