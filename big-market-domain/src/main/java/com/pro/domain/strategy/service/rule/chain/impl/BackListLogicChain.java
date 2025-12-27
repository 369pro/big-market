package com.pro.domain.strategy.service.rule.chain.impl;

import com.pro.domain.strategy.repository.IStrategyRepository;
import com.pro.domain.strategy.service.rule.chain.AbstractLogicChain;
import com.pro.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import com.pro.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;

@Slf4j
@Component("rule_blacklist")
public class BackListLogicChain extends AbstractLogicChain {
    @Resource
    private IStrategyRepository repository;

    //  logic 方法是责任链模式中的一个具体逻辑节点
    /**
     * return awardId
     * */
    @Override
    public DefaultChainFactory.StrategyAwardVO logic(String userId, Long strategyId) {
        log.info("规则过滤-黑名单 userId:{} strategyId:{} ruleModel:{}",
                userId, strategyId, ruleModel());
        // 这边awardId为空
        String ruleValue = repository.queryStrategyRuleValue(strategyId,  ruleModel());

        String[] splitRuleValue = ruleValue.split(Constants.COLON);
        Integer awardId = Integer.parseInt(splitRuleValue[0]);
        String[] userBlackIds = splitRuleValue[1].split(Constants.SPLIT);
        for(String userBlackId : userBlackIds) {
            if (userId.equals(userBlackId)) {
                log.info("抽奖责任链-黑名单接管 userId: {} strategyId: {} ruleModel: {} awardId: {}", userId, strategyId, ruleModel(), awardId);
                return DefaultChainFactory.StrategyAwardVO.builder()
                        .awardId(awardId)
                        .logicModel(ruleModel())
                        .build();
            }
        }
        // 转交其他责任链
        log.info("抽奖责任链-黑名单放行 userId: {} strategyId: {} ruleModel: {}", userId, strategyId, ruleModel());
        return next().logic(userId, strategyId);
    }

    @Override
    protected String ruleModel() {
        return DefaultChainFactory.LogicModel.RULE_BLACKLIST.getCode();
    }
}
