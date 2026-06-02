package com.pro.domain.activity.service.quota.rule;

public interface IActionChainArmory {
    IActionChain next();
    IActionChain appentNext(IActionChain next);
}
