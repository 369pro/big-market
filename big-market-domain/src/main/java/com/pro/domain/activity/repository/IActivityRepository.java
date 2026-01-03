package com.pro.domain.activity.repository;


import com.pro.domain.activity.model.aggregate.CreateOrderAggregate;
import com.pro.domain.activity.model.entity.ActivityCountEntity;
import com.pro.domain.activity.model.entity.ActivityEntity;
import com.pro.domain.activity.model.entity.ActivitySkuEntity;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 活动仓储接口
 * @create 2024-03-16 10:31
 */
public interface IActivityRepository {

    ActivitySkuEntity queryActivitySku(Long sku);

    ActivityEntity queryRaffleActivityByActivityId(Long activityId);

    ActivityCountEntity queryRaffleActivityCountByActivityCountId(Long activityCountId);

    void doSaveOrder(CreateOrderAggregate createOrderAggregate);
}
