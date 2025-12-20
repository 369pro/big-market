package com.pro.domain.strategy.service.rule.chain;

public interface ILogicChainArmory {
    ILogicChain next();
    ILogicChain appendNext(ILogicChain next);
}
