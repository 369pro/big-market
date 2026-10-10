package com.pro.infrastructure.dao;

import com.pro.infrastructure.dao.po.RaffleQuotaPlan;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 抽奖次数套餐配置表Dao
 * @create 2024-03-09 10:07
 */
@Mapper
public interface IRaffleQuotaPlanDao {

    RaffleQuotaPlan queryRaffleQuotaPlanByQuotaPlanId(Long quotaPlanId);

}
