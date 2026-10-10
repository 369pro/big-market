package com.pro.infrastructure.dao.po;

import lombok.Data;

import java.util.Date;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 抽奖次数套餐配置表 持久化对象
 * @create 2024-03-02 13:13
 */
@Data
public class RaffleQuotaPlan {

    /**
     * 自增ID
     */
    private Long id;

    /**
     * 抽奖次数套餐业务编号
     */
    private Long quotaPlanId;

    /**
     * 总次数
     */
    private Integer totalCount;

    /**
     * 日次数
     */
    private Integer dayCount;

    /**
     * 月次数
     */
    private Integer monthCount;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

}


