package com.pro.domain.activity.service.armory;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 活动装配预热到缓存
 * @create 2024-03-30 09:09
 */
public interface IActivityArmory {

    boolean assembleActivitySku(Long sku);

    boolean assembleActivitySkuByActivityId(Long activityId);
}
