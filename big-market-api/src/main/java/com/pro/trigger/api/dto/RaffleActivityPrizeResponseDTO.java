package com.pro.trigger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Current strategy prize configuration for the ERP activity detail view.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaffleActivityPrizeResponseDTO implements Serializable {

    private Integer awardId;
    private String awardTitle;
    private String awardSubtitle;
    private String awardKey;
    private String awardConfig;
    private BigDecimal awardRate;
    private Integer awardCount;
    private Integer awardCountSurplus;
    /** {@code redis} for the live counter, {@code database} for the persisted fallback. */
    private String awardCountSurplusSource;
    /** Persisted DB value retained so the frontend can identify a Redis fallback. */
    private Integer awardCountSurplusFallbackDb;
    private Integer sort;
    private String ruleModels;
    private Integer ruleUnlockCount;
}
