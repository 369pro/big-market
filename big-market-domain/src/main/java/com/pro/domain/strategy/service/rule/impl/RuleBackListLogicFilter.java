package com.pro.domain.strategy.service.rule.impl;


import com.pro.domain.strategy.model.entity.RuleActionEntity;
import com.pro.domain.strategy.model.entity.RuleMatterEntity;
import com.pro.domain.strategy.model.valobj.RuleLogicCheckTypeVO;
import com.pro.domain.strategy.repository.IStrategyRepository;
import com.pro.domain.strategy.service.annotation.LogicStrategy;
import com.pro.domain.strategy.service.rule.ILogicFilter;
import com.pro.domain.strategy.service.rule.factory.DefaultLogicFactory;
import com.pro.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @Author: 369pro
 * @CreateTime: 2025-11-04
 * @Version: 1.0
 * @description 【抽奖前规则】黑名单用户过滤规则
 */
@Slf4j
@Component
@LogicStrategy(logicMode = DefaultLogicFactory.LogicModel.RULE_BLACKLIST)
public class RuleBackListLogicFilter implements ILogicFilter<RuleActionEntity.RaffleBeforeEntity> {
    @Resource
    private IStrategyRepository repository;

    @Override
    public RuleActionEntity<RuleActionEntity.RaffleBeforeEntity> filter(RuleMatterEntity ruleMatterEntity) {
        String userId = ruleMatterEntity.getUserId();
        Long strategyId = ruleMatterEntity.getStrategyId();
        String ruleModel = ruleMatterEntity.getRuleModel();
        log.info("规则过滤-黑名单 userId:{} strategyId:{} ruleModel:{}",
                userId, strategyId, ruleModel);
        // 这边awardId为空
        String ruleValue = repository.queryStrategyRuleValue(strategyId, ruleMatterEntity.getAwardId(), ruleModel);
        String[] splitRuleValue = ruleValue.split(Constants.COLON);
        Integer awardId = Integer.parseInt(splitRuleValue[0]);
        String[] userBlackIds = splitRuleValue[1].split(Constants.SPLIT);
        for(String userBlackId : userBlackIds){
            if(userId.equals(userBlackId)){
                return RuleActionEntity.<RuleActionEntity.RaffleBeforeEntity>builder()
                        .ruleModel(DefaultLogicFactory.LogicModel.RULE_BLACKLIST.getCode())
                        .data(RuleActionEntity.RaffleBeforeEntity.builder()
                                .strategyId(ruleMatterEntity.getStrategyId())
                                .awardId(awardId)
                                .build())
                        .code(RuleLogicCheckTypeVO.TAKE_OVER.getCode())
                        .info(RuleLogicCheckTypeVO.TAKE_OVER.getInfo())
                        .build();
            }
        }
        return RuleActionEntity.<RuleActionEntity.RaffleBeforeEntity>builder()
                .code(RuleLogicCheckTypeVO.ALLOW.getCode())
                .info(RuleLogicCheckTypeVO.ALLOW.getInfo())
                .build();
    }
}
