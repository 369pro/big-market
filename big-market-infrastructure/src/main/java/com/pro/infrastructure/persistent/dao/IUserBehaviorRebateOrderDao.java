package com.pro.infrastructure.persistent.dao;


import cn.bugstack.middleware.db.router.annotation.DBRouterStrategy;
import com.pro.domain.rebate.model.entity.BehaviorRebateOrderEntity;
import com.pro.infrastructure.persistent.po.UserBehaviorRebateOrder;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 用户行为返利流水订单表
 * @create 2024-04-30 13:48
 */
@Mapper
@DBRouterStrategy(splitTable = true)
public interface IUserBehaviorRebateOrderDao {

    void insert(UserBehaviorRebateOrder userBehaviorRebateOrder);

    List<BehaviorRebateOrderEntity> queryOrderByOutBusinessNo(String userId, String outBusinessNo);
}
