package com.pro.infrastructure.dao.po;

import lombok.Data;

import java.util.Date;

/** Stored payload for a strategy-award stock event ID. */
@Data
public class StrategyAwardStockInbox {

    private String eventId;
    private Long strategyId;
    private Integer awardId;
    private Integer surplus;
    private Date createdAt;
}
