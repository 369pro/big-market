package com.pro.domain.activity.service.quota.rule;

public abstract class AbstractActionChain implements IActionChain{
    // 错飞了这样写，你就当成是一个链表就行
//    private IActionChain actionChain;
    private IActionChain next;

    @Override
    public IActionChain next() {
        return this.next;
    }

    @Override
    public IActionChain appentNext(IActionChain next) {
        this.next = next;
        return next;
    }
}
