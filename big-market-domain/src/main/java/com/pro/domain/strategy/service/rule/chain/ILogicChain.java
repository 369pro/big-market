package com.pro.domain.strategy.service.rule.chain;

import com.pro.domain.strategy.service.rule.chain.factory.DefaultChainFactory;

public interface ILogicChain extends ILogicChainArmory, Cloneable{
    DefaultChainFactory.StrategyAwardVO logic(String userId, Long strategyId, Integer userScore);
}
