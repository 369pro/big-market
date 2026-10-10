package com.pro.domain.activity.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 抽奖次数套餐实体对象
 * @create 2024-03-16 11:19
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QuotaPlanEntity {

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

}
