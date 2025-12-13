package com.pro.domain.strategy.service.rule.factory;


import com.pro.domain.strategy.model.entity.RuleActionEntity;
import com.pro.domain.strategy.service.annotation.LogicStrategy;
import com.pro.domain.strategy.service.rule.ILogicFilter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: 369pro
 * @CreateTime: 2025-11-04
 * @Version: 1.0
 * @description 规则工厂
 */
@Service
public class DefaultLogicFactory {
    public Map<String, ILogicFilter<?>> logicFilterMap = new ConcurrentHashMap<>();

    /**
     * 构造函数注入
     */
    public DefaultLogicFactory(List<ILogicFilter<?>> logicFilters){
        logicFilters.forEach(logic->{
            Class<?> target = AopUtils.getTargetClass(logic);
            LogicStrategy strategy = AnnotationUtils.findAnnotation(target, LogicStrategy.class);
            if(null != strategy){
                logicFilterMap.put(strategy.logicMode().getCode(), logic);
            }
        });
    }

    public <T extends RuleActionEntity.RaffleEntity> Map<String, ILogicFilter<T>> openLogicFilter() {
        return (Map<String, ILogicFilter<T>>) (Map<?, ?>) logicFilterMap;
    }

    @Getter
    @AllArgsConstructor
    public enum LogicModel{
        RULE_WIGHT("rule_weight","【抽奖前规则】根据抽奖权重返回可抽奖范围KEY"),
        RULE_BLACKLIST("rule_blacklist","【抽奖前规则】黑名单规则过滤，命中黑名单则直接返回"),

        ;
        private final String code;
        private final String info;
    }
}
