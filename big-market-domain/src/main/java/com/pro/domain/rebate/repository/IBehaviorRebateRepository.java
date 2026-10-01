package com.pro.domain.rebate.repository;

import com.pro.domain.rebate.model.aggregate.BehaviorRebateAggregate;
import com.pro.domain.rebate.model.entity.BehaviorRebateOrderEntity;
import com.pro.domain.rebate.model.valobj.DailyBehaviorRebateVO;

import java.util.List;

public interface IBehaviorRebateRepository {
    List<DailyBehaviorRebateVO> queryDailyBehaviorRebateByBehaviorType(String behaviorType);

    void saveUserRebateRecord(String userId, List<BehaviorRebateAggregate> behaviorRebateAggregates);

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
