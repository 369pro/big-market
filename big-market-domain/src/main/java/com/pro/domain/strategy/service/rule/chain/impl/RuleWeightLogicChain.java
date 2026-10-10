package com.pro.domain.strategy.service.rule.chain.impl;

import com.pro.domain.strategy.repository.IStrategyRepository;
import com.pro.domain.strategy.model.valobj.WeightRuleConfig;
import com.pro.domain.strategy.service.armory.IStrategyDispatch;
import com.pro.domain.strategy.service.rule.chain.AbstractLogicChain;
import com.pro.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Comparator;
import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 权重抽奖责任链
 * @create 2024-01-20 10:38
 */
@Slf4j
@Component("rule_weight")
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RuleWeightLogicChain extends AbstractLogicChain {

    @Resource
    private IStrategyRepository repository;
    @Resource
    protected IStrategyDispatch strategyDispatch;
    /**
     * 权重规则支持显式区间和 legacy 门槛格式。
     * 用户分值从应用层传入，避免循环依赖。
     */
    @Override
    public DefaultChainFactory.StrategyAwardVO logic(String userId, Long strategyId, Integer userScore){
        log.info("抽奖责任链-权重开始 userId:{} strategyId:{} userScore:{} ruleModel:{}", userId, strategyId, userScore, ruleModel());

        String ruleValue = repository.queryStrategyRuleValue(strategyId, ruleModel());

        // 1. 解析并校验权重规则值
        List<WeightRuleConfig> ruleGroups = WeightRuleConfig.parse(ruleValue);
        if (ruleGroups.isEmpty()) {
            log.warn("抽奖责任链-权重告警【策略配置权重，但ruleValue未配置相应值】 userId:{} strategyId:{} ruleModel:{}", userId, strategyId, ruleModel());
            return next().logic(userId, strategyId, userScore);
        }
        // 2. 用户分值（从应用层传入，避免循环依赖）
        if (userScore == null) {
            userScore = 0;
        }
        int normalizedUserScore = userScore;
        // 3. 获取命中的区间或最高门槛
        WeightRuleConfig matchedGroup = ruleGroups.stream()
                .filter(group -> group.matches(normalizedUserScore))
                .max(Comparator.comparingInt(WeightRuleConfig::getLowerBound))
                .orElse(null);

        // 4. 权重抽奖
        if (matchedGroup != null) {
            Integer awardId = strategyDispatch.getRandomAwardId(strategyId, matchedGroup.getRawGroup());
            log.info("抽奖责任链-权重接管 userId:{} strategyId:{} userScore:{} ruleModel:{} awardId:{}", userId, strategyId, userScore, ruleModel(), awardId);
            return DefaultChainFactory.StrategyAwardVO.builder()
                    .awardId(awardId)
                    .logicModel(ruleModel())
                    .build();
        }

        // 5. 过滤其他责任链
        log.info("抽奖责任链-权重放行 userId:{} strategyId:{} userScore:{} ruleModel:{}", userId, strategyId, userScore, ruleModel());
        return next().logic(userId, strategyId, userScore);
    }

    @Override
    protected String ruleModel(){
        return DefaultChainFactory.LogicModel.RULE_WEIGHT.getCode();
    }

}
