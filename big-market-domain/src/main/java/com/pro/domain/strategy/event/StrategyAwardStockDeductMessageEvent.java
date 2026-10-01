package com.pro.domain.strategy.event;

import com.pro.types.event.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

/** 奖品库存扣减消息。surplus 是 Redis 扣减后的库存快照。 */
@Component
public class StrategyAwardStockDeductMessageEvent
        extends BaseEvent<StrategyAwardStockDeductMessageEvent.StockDeductMessage> {

    @Value("${spring.rabbitmq.topic.strategy_award_stock_deduct}")
    private String topic;

    @Override
    public EventMessage<StockDeductMessage> buildEventMessage(StockDeductMessage data) {
        return buildEventMessage(data.outboxMember(), data);
    }

    public EventMessage<StockDeductMessage> buildEventMessage(String eventId, StockDeductMessage data) {
        return EventMessage.<StockDeductMessage>builder()
                .id(eventId)
                .timestamp(new Date())
                .data(data)
                .build();
    }

    @Override
    public String topic() {
        return topic;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StockDeductMessage {
        private Long strategyId;
        private Integer awardId;
        private Integer surplus;

        public String outboxMember() {
            return strategyId + ":" + awardId + ":" + java.util.UUID.randomUUID() + ":" + surplus;
        }

        public static StockDeductMessage fromOutboxMember(String member) {
            String[] parts = member.split(":");
            if (parts.length != 4) {
                throw new IllegalArgumentException("非法奖品库存 MQ outbox 成员: " + member);
            }
            return StockDeductMessage.builder()
                    .strategyId(Long.valueOf(parts[0]))
                    .awardId(Integer.valueOf(parts[1]))
                    .surplus(Integer.valueOf(parts[3]))
                    .build();
        }
    }
}
