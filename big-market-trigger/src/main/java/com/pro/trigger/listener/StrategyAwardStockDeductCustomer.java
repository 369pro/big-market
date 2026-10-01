package com.pro.trigger.listener;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.pro.domain.strategy.event.StrategyAwardStockDeductMessageEvent.StockDeductMessage;
import com.pro.domain.strategy.model.valobj.StrategyAwardStockDeductEvent;
import com.pro.domain.strategy.service.IRaffleStock;
import com.pro.types.event.BaseEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 批量消费奖品库存扣减事件，并幂等同步数据库库存。 */
@Slf4j
@Component
public class StrategyAwardStockDeductCustomer {

    @Resource
    private IRaffleStock raffleStock;

    public void listener(List<MessageExt> messages) {
        Map<String, StrategyAwardStockDeductEvent> eventsById = new LinkedHashMap<>();

        for (MessageExt message : messages) {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            BaseEvent.EventMessage<StockDeductMessage> eventMessage = JSON.parseObject(
                    json,
                    new TypeReference<BaseEvent.EventMessage<StockDeductMessage>>() { }.getType());
            if (eventMessage == null || eventMessage.getId() == null
                    || eventMessage.getId().trim().isEmpty()) {
                throw new IllegalArgumentException("缺少稳定事件 ID 的库存扣减消息: " + json);
            }
            StockDeductMessage data = eventMessage.getData();
            if (data == null || data.getStrategyId() == null || data.getAwardId() == null
                    || data.getSurplus() == null || data.getSurplus() < 0) {
                throw new IllegalArgumentException("非法奖品库存扣减消息: " + json);
            }

            StrategyAwardStockDeductEvent event = StrategyAwardStockDeductEvent.builder()
                    .eventId(eventMessage.getId())
                    .strategyId(data.getStrategyId())
                    .awardId(data.getAwardId())
                    .surplus(data.getSurplus())
                    .build();
            StrategyAwardStockDeductEvent previous = eventsById.putIfAbsent(event.getEventId(), event);
            if (previous != null && !previous.equals(event)) {
                throw new IllegalArgumentException("库存事件 ID 载荷冲突: " + event.getEventId());
            }
        }

        List<StrategyAwardStockDeductEvent> events = new ArrayList<>(eventsById.values());
        raffleStock.syncStrategyAwardStock(events);

        log.info("批量消费奖品库存扣减MQ完成 messageCount:{} awardCount:{}",
                messages.size(), events.size());
    }
}
