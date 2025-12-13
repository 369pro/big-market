package com.pro.domain.strategy.service.rule;

import com.pro.domain.strategy.model.entity.RuleActionEntity;
import com.pro.domain.strategy.model.entity.RuleMatterEntity;

public interface ILogicFilter<T extends RuleActionEntity.RaffleEntity> {
    RuleActionEntity<T> filter(RuleMatterEntity ruleMatterEntity);
}
