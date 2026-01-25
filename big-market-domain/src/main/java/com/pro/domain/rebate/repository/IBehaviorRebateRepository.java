package com.pro.domain.rebate.repository;

import com.pro.domain.rebate.model.aggregate.BehaviorRebateAggregate;
import com.pro.domain.rebate.model.entity.BehaviorRebateOrderEntity;
import com.pro.domain.rebate.model.valobj.DailyBehaviorRebateVO;

import java.util.List;

public interface IBehaviorRebateRepository {
    List<DailyBehaviorRebateVO> queryDailyBehaviorRebateByBehaviorType(String behaviorType);

    void saveUserRebateRecord(String userId, List<BehaviorRebateAggregate> behaviorRebateAggregates);

    List<BehaviorRebateOrderEntity> queryOrderByOutBusinessNo(String userId, String outBusinessNo);
}
