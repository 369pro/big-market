package com.pro.infrastructure.persistent.dao;

import cn.bugstack.middleware.db.router.annotation.DBRouter;
import cn.bugstack.middleware.db.router.annotation.DBRouterStrategy;
import com.pro.infrastructure.persistent.po.RaffleActivityOrder;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 抽奖活动单Dao
 * @create 2024-03-09 10:08
 */
@Mapper
@DBRouterStrategy(splitTable = true)
public interface IRaffleActivityOrderDao {

    @DBRouter(key = "userId")
    void insert(RaffleActivityOrder raffleActivityOrder);

    @DBRouter(key = "userId")
    List<RaffleActivityOrder> queryRaffleActivityOrderByUserId(String userId);

}
