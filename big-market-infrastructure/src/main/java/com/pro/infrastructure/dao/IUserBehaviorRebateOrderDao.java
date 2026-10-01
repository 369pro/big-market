package com.pro.infrastructure.dao;


import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.pro.infrastructure.dao.po.UserBehaviorRebateOrder;

import cn.bugstack.middleware.db.router.annotation.DBRouterStrategy;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 用户行为返利流水订单表
 * @create 2024-04-30 13:48
 */
@Mapper
@DBRouterStrategy(splitTable = true)
public interface IUserBehaviorRebateOrderDao {

    void insert(UserBehaviorRebateOrder userBehaviorRebateOrder);

    List<UserBehaviorRebateOrder> queryOrderByOutBusinessNo(@Param("userId") String userId, @Param("outBusinessNo") String outBusinessNo);

    List<String> querySignOutBusinessNoByMonth(@Param("userId") String userId, @Param("monthPrefix") String monthPrefix);
}
