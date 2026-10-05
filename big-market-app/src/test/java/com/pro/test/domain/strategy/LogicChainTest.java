package com.pro.test.domain.strategy;

import com.alibaba.fastjson.JSON;
import com.pro.domain.strategy.service.armory.IStrategyArmory;
import com.pro.domain.strategy.service.rule.chain.ILogicChain;
import com.pro.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 抽奖责任链测试，验证不同的规则走不同的责任链
 * @create 2024-01-20 11:20
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class LogicChainTest {

    @Resource
    private IStrategyArmory strategyArmory;
    @Resource
    private DefaultChainFactory defaultChainFactory;

    @Before
    public void setUp() {
        // 策略装配 100001、100002、100003
        log.info("测试结果：{}", strategyArmory.assembleLotteryStrategy(100001L));
        log.info("测试结果：{}", strategyArmory.assembleLotteryStrategy(100002L));
        log.info("测试结果：{}", strategyArmory.assembleLotteryStrategy(100003L));
    }

    @Test
    public void test_LogicChain_rule_blacklist() {
        ILogicChain logicChain = defaultChainFactory.openLogicChain(100003L);
        DefaultChainFactory.StrategyAwardVO strategyAwardVO = logicChain.logic("user001", 100003L, 0);
        log.info("测试结果：{}", JSON.toJSONString(strategyAwardVO));
    }

    @Test
    public void test_LogicChain_rule_weight() {
        ILogicChain logicChain = defaultChainFactory.openLogicChain(100001L);
        DefaultChainFactory.StrategyAwardVO strategyAwardVO = logicChain.logic("xiaofuge", 100001L, 4900);
        log.info("测试结果：{}", JSON.toJSONString(strategyAwardVO));
    }

    @Test
    public void test_LogicChain_rule_default() {
        ILogicChain logicChain = defaultChainFactory.openLogicChain(100001L);
        DefaultChainFactory.StrategyAwardVO strategyAwardVO = logicChain.logic("xiaofuge", 100001L, 0);
        log.info("测试结果：{}", JSON.toJSONString(strategyAwardVO));
    }

    @Test
    public void test_LogicChain() {
        // rule_blacklist,rule_weight
        ILogicChain logicChain1 = defaultChainFactory.openLogicChain(100001L);
        // rule_blacklist
        // 单例模式下 logicChain2 覆盖 logicChain1
        // 因为 private Map<String, ILogicChain> logicChainGroup 是公用的
        ILogicChain logicChain2 = defaultChainFactory.openLogicChain(100003L);
    }

}
