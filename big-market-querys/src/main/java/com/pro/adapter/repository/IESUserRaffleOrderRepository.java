package com.pro.adapter.repository;


import com.pro.model.valobj.ESUserRaffleOrderVO;
import com.pro.model.valobj.ESUserRaffleOrderPageVO;
import com.pro.model.valobj.ESUserRaffleOrderQueryVO;

import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description ElasticSearch 用户抽奖订单表查询
 * @create 2024-09-21 10:10
 */
public interface IESUserRaffleOrderRepository {

    List<ESUserRaffleOrderVO> queryESUserRaffleOrderVOList();

    /**
     * 按条件分页查询用户抽奖订单。
     *
     * @param query 查询条件和分页参数
     * @return 当前页数据及命中总数
     */
    ESUserRaffleOrderPageVO queryESUserRaffleOrderPage(ESUserRaffleOrderQueryVO query);


}
