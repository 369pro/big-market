package com.pro.domain.activity.service.rule;

public interface IActionChainArmory {
    IActionChain next();
    IActionChain appentNext(IActionChain next);
}
