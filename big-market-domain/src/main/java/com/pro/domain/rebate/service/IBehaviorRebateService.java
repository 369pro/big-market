package com.pro.domain.rebate.service;

import com.pro.domain.rebate.model.entity.BehaviorEntity;
import com.pro.domain.rebate.model.entity.BehaviorRebateOrderEntity;

import java.util.List;

public interface IBehaviorRebateService{
    List<String> createOrder(BehaviorEntity behaviorEntity);

    List<BehaviorRebateOrderEntity> queryOrderByOutBusinessNo(String userId, String outBusinessNo);

    /**
     * 查询用户指定月份的签到业务日期。
     *
     * @param userId 用户ID
     * @param monthPrefix 月份前缀，格式 yyyyMM
     * @return 签到业务日期，格式 yyyyMMdd
     */
    List<String> querySignOutBusinessNoByMonth(String userId, String monthPrefix);
}
