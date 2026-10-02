package com.pro.trigger.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * ERP 用户抽奖订单分页查询条件。
 */
@Data
public class ESUserRaffleOrderQueryRequestDTO implements Serializable {

    private String userId;
    private Long activityId;
    private String activityName;
    private String orderId;
    private String orderState;
    private Long strategyId;
    private Integer pageNo;
    private Integer pageSize;
}
