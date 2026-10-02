package com.pro.trigger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * ERP read-only activity detail response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaffleActivityDetailResponseDTO implements Serializable {

    private Long activityId;
    private String activityName;
    private String activityDesc;
    private Long strategyId;
    private String activityState;
    private Date beginDateTime;
    private Date endDateTime;
    private List<RaffleActivityPrizeResponseDTO> prizes;
    private List<RaffleActivitySkuResponseDTO> skus;
}
