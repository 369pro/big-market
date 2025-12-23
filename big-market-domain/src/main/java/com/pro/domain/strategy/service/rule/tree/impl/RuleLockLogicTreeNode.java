package com.pro.domain.strategy.service.rule.tree.impl;

import com.pro.domain.strategy.model.valobj.RuleLogicCheckTypeVO;
import com.pro.domain.strategy.service.rule.tree.ILogicTreeNode;
import com.pro.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 次数锁节点
 * @create 2024-01-27 11:22
 */
@Slf4j
@Component("rule_lock")
public class RuleLockLogicTreeNode implements ILogicTreeNode {
    @Override
    public DefaultTreeFactory.TreeActionEntity logic(String userId, Long strategyId, Integer awardId) {
        return DefaultTreeFactory.TreeActionEntity.builder()
                .ruleLogicCheckType(RuleLogicCheckTypeVO.ALLOW)
                .build();
    }

}
