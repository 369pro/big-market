package com.pro.domain.activity.service.quota.policy.impl;


import com.pro.domain.activity.model.aggregate.CreateQuotaOrderAggregate;
import com.pro.domain.activity.model.valobj.OrderStateVO;
import com.pro.domain.activity.repository.IActivityRepository;
import com.pro.domain.activity.service.quota.policy.ITradePolicy;
import org.springframework.stereotype.Service;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 积分兑换，支付类订单
 * @create 2024-06-08 18:12
 */
@Service("credit_pay_trade")
public class CreditPayTradePolicy implements ITradePolicy {

    private final IActivityRepository activityRepository;

    public CreditPayTradePolicy(IActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    @Override
    public void trade(CreateQuotaOrderAggregate createQuotaOrderAggregate) {
        createQuotaOrderAggregate.setOrderState(OrderStateVO.wait_pay);
        activityRepository.doSaveCreditPayOrder(createQuotaOrderAggregate);
    }

}
