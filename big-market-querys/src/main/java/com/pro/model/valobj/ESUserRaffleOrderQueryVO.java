package com.pro.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户抽奖订单查询条件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ESUserRaffleOrderQueryVO {

    public static final int DEFAULT_PAGE_NO = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_OFFSET = 10_000;
    public static final int MAX_FILTER_LENGTH = 128;

    private String userId;
    private Long activityId;
    private String activityName;
    private String orderId;
    private String orderState;
    private Long strategyId;
    private Integer pageNo;
    private Integer pageSize;

    public int effectivePageNo() {
        return pageNo == null ? DEFAULT_PAGE_NO : pageNo;
    }

    public int effectivePageSize() {
        return pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
    }

    public int offset() {
        return Math.multiplyExact(effectivePageNo() - 1, effectivePageSize());
    }

    /**
     * 校验边界，避免深分页和超大文本条件把 ES 查询拖垮。
     */
    public void validate() {
        int actualPageNo = effectivePageNo();
        int actualPageSize = effectivePageSize();
        if (actualPageNo < 1) {
            throw new IllegalArgumentException("pageNo must be greater than 0");
        }
        if (actualPageSize < 1 || actualPageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
        long actualOffset = (long) (actualPageNo - 1) * actualPageSize;
        if (actualOffset > MAX_OFFSET) {
            throw new IllegalArgumentException("page offset must be no greater than " + MAX_OFFSET);
        }
        validateId("activityId", activityId);
        validateId("strategyId", strategyId);
        validateText("userId", userId);
        validateText("activityName", activityName);
        validateText("orderId", orderId);
        validateText("orderState", orderState);
    }

    private static void validateText(String name, String value) {
        if (value != null && value.length() > MAX_FILTER_LENGTH) {
            throw new IllegalArgumentException(name + " must be no longer than " + MAX_FILTER_LENGTH);
        }
    }

    private static void validateId(String name, Long value) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }
}
