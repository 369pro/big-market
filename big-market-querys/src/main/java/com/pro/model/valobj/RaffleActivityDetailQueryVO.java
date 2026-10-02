package com.pro.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * Read model for the ERP activity detail view.
 *
 * <p>The query module deliberately keeps this model independent of the
 * infrastructure persistence objects and of the HTTP API DTOs.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaffleActivityDetailQueryVO {

    private Long activityId;
    private String activityName;
    private String activityDesc;
    private Long strategyId;
    private String activityState;
    private Date beginDateTime;
    private Date endDateTime;
    private List<Prize> prizes;
    private List<Sku> skus;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Prize {
        private Integer awardId;
        private String awardTitle;
        private String awardSubtitle;
        private String awardKey;
        private String awardConfig;
        private BigDecimal awardRate;
        private Integer awardCount;
        private Integer awardCountSurplus;
        /** redis when the live counter exists, database when the DB snapshot is used. */
        private String awardCountSurplusSource;
        /** The persisted snapshot retained for transparency when Redis is unavailable. */
        private Integer awardCountSurplusFallbackDb;
        private Integer sort;
        private String ruleModels;
        private Integer ruleUnlockCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Sku {
        private Long sku;
        private Long activityId;
        private Long activityCountId;
        private Integer countQuota;
        private Integer dayCount;
        private Integer monthCount;
        private Integer stockCount;
        private Integer stockCountSurplus;
        /** redis when the live counter exists, database when the DB snapshot is used. */
        private String stockCountSurplusSource;
        /** The persisted snapshot retained for transparency when Redis is unavailable. */
        private Integer stockCountSurplusFallbackDb;
        private BigDecimal productAmount;
    }
}
