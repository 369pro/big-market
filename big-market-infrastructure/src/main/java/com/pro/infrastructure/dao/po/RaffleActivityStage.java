package com.pro.infrastructure.dao.po;

import lombok.Data;

import java.sql.Date;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 活动展台 - 上架活动
 * @create 2024-10-26 17:06
 */
@Data
public class RaffleActivityStage {

    /** ID */
    private Long id;
    /** 渠道 */
    private String channel;
    /** 来源 */
    private String source;
    /** 活动ID */
    private Long activityId;
    /** 活动名称 */
    private String activityName;
    /** 活动说明 */
    private String activityDesc;
    /** 当前策略 */
    private Long strategyId;
    /** 活动状态 */
    private String activityState;
    /** 活动开始时间 */
    private java.util.Date beginDateTime;
    /** 活动结束时间 */
    private java.util.Date endDateTime;

    /** 上架状态；create、active、inactive、expire */
    private String state;
    /** 创建时间 */
    private Date createTime;
    /** 更新时间 */
    private Date updateTime;

}
