package com.pro.domain.strategy.service.rule.chain.impl;

import com.pro.domain.strategy.repository.IStrategyRepository;
import com.pro.domain.strategy.service.armory.IStrategyDispatch;
import com.pro.domain.strategy.service.rule.chain.AbstractLogicChain;
import com.pro.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import com.pro.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@Component("rule_weight")
public class RuleWeightLogicChain extends AbstractLogicChain {
    @Resource
    private IStrategyRepository repository;
    @Resource
    private IStrategyDispatch strategyDispatch;

    public Long userScore = 0L;

    /**
     * 虽然 AbstractLogicChain 是抽象类,但它实现了 ILogicChain 接口却没有实现 logic 方法。这意味着:
     * AbstractLogicChain 隐式地将 logic 方法变成了抽象方法
     * 所有继承 AbstractLogicChain 的子类都必须实现 logic 方法
     * 如果不实现,编译器会报错
     */
    @Override
    public DefaultChainFactory.StrategyAwardVO logic(String userId, Long strategyId) {
        log.info("抽奖责任链-权重开始 userId: {} strategyId: {} ruleModel: {}",
                userId, strategyId, ruleModel());
        String ruleValue = repository.
                queryStrategyRuleValue(strategyId, ruleModel());
//        Map<Long, String> analyticalValueGroup = getWeightMap(ruleValue);
        // 1. 解析权重规则值 4000:102,103,104,105 拆解为；4000 -> 4000:102,103,104,105 便于比对判断
        Map<Long, String> analyticalValueGroup = getWeightMap(ruleValue);
        if (null == analyticalValueGroup || analyticalValueGroup.isEmpty()) {
            log.warn("抽奖责任链-权重告警【策略配置权重，但ruleValue未配置相应值】 userId: {} strategyId: {} ruleModel: {}",
                    userId, strategyId, ruleModel());
            return next().logic(userId, strategyId);
        }
        // 2. 转换Keys值，并默认排序
        List<Long> analyticalSortedKeys = new ArrayList<>(analyticalValueGroup.keySet());
        Collections.sort(analyticalSortedKeys);
        Long nextValue = analyticalSortedKeys.stream()
                .sorted(Comparator.reverseOrder())
                .filter(sc -> userScore >= sc)
                .findFirst()
                .orElse(null);
        log.info("规则过滤-权重范围 userId:{} strategyId:{} ruleModel:{} userScore:{} nextValue:{}",
                userId, strategyId, ruleModel(), userScore, nextValue);
        if(null != nextValue){
            Integer awardId = strategyDispatch.getRandomAwardId(strategyId, analyticalValueGroup.get(nextValue));
            log.info("抽奖责任链-权重接管 userId: {} strategyId: {} ruleModel: {} awardId: {}", userId, strategyId, ruleModel(), awardId);
            return DefaultChainFactory.StrategyAwardVO.builder()
                    .awardId(awardId)
                    .logicModel(ruleModel())
                    .build();
        }
        return next().logic(userId, strategyId);
    }

    @Override
    protected String ruleModel() {
        return DefaultChainFactory.LogicModel.RULE_WEIGHT.getCode();
    }

    private Map<Long, String> getWeightMap(String ruleValue) {
        String[] ruleValueGroups = ruleValue.split(Constants.SPACE);
        Map<Long, String> ruleValueMap = new HashMap<>();
        for (String ruleValueKey : ruleValueGroups) {
            // 检查输入是否为空
            if (ruleValueKey == null || ruleValueKey.isEmpty()) {
                return ruleValueMap;
            }
            // 分割字符串以获取键和值
            String[] parts = ruleValueKey.split(Constants.COLON);
            if (parts.length != 2) {
                throw new IllegalArgumentException("rule_weight rule_rule invalid input format" + ruleValueKey);
            }
            ruleValueMap.put(Long.parseLong(parts[0]), ruleValueKey);
        }
        return ruleValueMap;
    }


}
