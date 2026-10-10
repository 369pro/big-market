package com.pro.trigger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Activity SKU and quota configuration for the ERP activity detail view.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaffleActivitySkuResponseDTO implements Serializable {

    private Long sku;
    private Long activityId;
    private Long quotaPlanId;
    /** Total raffle quota granted by this SKU. */
    private Integer countQuota;
    private Integer dayCount;
    private Integer monthCount;
    private Integer stockCount;
    private Integer stockCountSurplus;
    /** {@code redis} for the live counter, {@code database} for the persisted fallback. */
    private String stockCountSurplusSource;
    /** Persisted DB value retained so the frontend can identify a Redis fallback. */
    private Integer stockCountSurplusFallbackDb;
    private BigDecimal productAmount;
}
