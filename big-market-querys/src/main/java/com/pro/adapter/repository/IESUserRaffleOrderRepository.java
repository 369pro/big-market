package com.pro.adapter.repository;


import com.pro.model.valobj.ESUserRaffleOrderVO;

import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description ElasticSearch 用户抽奖订单表查询
 * @create 2024-09-21 10:10
 */
public interface IESUserRaffleOrderRepository {

    List<ESUserRaffleOrderVO> queryESUserRaffleOrderVOList();


}
