package com.pro.domain.activity.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 活动上架实体对象
 * @create 2024-10-26 18:31
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RaffleActivityStageEntity {

    /** 自增ID */
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

    /** 上架状态；create、active、inactive、expire；未上架活动为 null */
    private String state;

}
