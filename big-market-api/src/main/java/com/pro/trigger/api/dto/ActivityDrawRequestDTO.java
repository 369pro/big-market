package com.pro.trigger.api.dto;

import java.io.Serializable;

import lombok.Data;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 活动抽奖请求对象
 * @create 2024-04-13 09:29
 */
@Data
public class ActivityDrawRequestDTO implements Serializable {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 活动ID
     */
    private Long activityId;

    /**
     * 客户端生成的幂等请求ID；同一用户重试同一抽奖时必须保持不变。
     */
    private String requestId;

}
