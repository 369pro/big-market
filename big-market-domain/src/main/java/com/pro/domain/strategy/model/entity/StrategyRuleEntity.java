package com.pro.domain.strategy.model.entity;


import com.pro.domain.strategy.model.valobj.WeightRuleConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: 369pro
 * @CreateTime: 2025-10-28
 * @Version: 1.0
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StrategyRuleEntity {
    /** 抽奖策略ID */
    private Long strategyId;
    /** 抽奖奖品ID【规则类型为策略，则不需要奖品ID】 */
    private Integer awardId;
    /** 抽象规则类型；1-策略规则、2-奖品规则 */
    private Integer ruleType;
    /** 抽奖规则类型【rule_random - 随机值计算、rule_lock - 抽奖几次后解锁、rule_luck_award - 幸运奖(兜底奖品)】 */
    private String ruleModel;
    /** 抽奖规则比值 */
    private String ruleValue;
    /** 抽奖规则描述 */
    private String ruleDesc;

    /**
     * 获取权重奖品池，键保留原始 group 文本供抽奖装配和派发使用。
     */
    public Map<String, List<Integer>> getRuleWeightValues() {
        if (!"rule_weight".equals(ruleModel)) {
            return null;
        }
        Map<String, List<Integer>> result = new LinkedHashMap<>();
        for (WeightRuleConfig group : WeightRuleConfig.parse(ruleValue)) {
            result.put(group.getRawGroup(), new ArrayList<>(group.getAwardIds()));
        }
        return result;
    }
}
