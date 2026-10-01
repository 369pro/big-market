package com.pro.domain.strategy.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A durable strategy-award stock writeback command.
 *
 * <p>The event id comes from the message envelope and is persisted before the
 * stock snapshot is written, so a broker redelivery can be ignored safely.</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StrategyAwardStockDeductEvent {

    private String eventId;
    private Long strategyId;
    private Integer awardId;
    private Integer surplus;
}
