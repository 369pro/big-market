package com.pro.trigger.listener;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.pro.domain.strategy.event.StrategyAwardStockDeductMessageEvent.StockDeductMessage;
import com.pro.domain.strategy.service.IRaffleStock;
import com.pro.types.event.BaseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 批量消费奖品库存扣减事件，并幂等同步数据库库存。 */
@Slf4j
@Component
public class StrategyAwardStockDeductCustomer {

    @Resource
    private IRaffleStock raffleStock;

    @RabbitListener(
            queues = "${spring.rabbitmq.topic.strategy_award_stock_deduct}",
            containerFactory = "strategyAwardStockBatchContainerFactory")
    public void listener(List<Message> messages) {
        Map<String, StockDeductMessage> latestStockMap = new HashMap<>();

        for (Message message : messages) {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            BaseEvent.EventMessage<StockDeductMessage> eventMessage = JSON.parseObject(
                    json,
                    new TypeReference<BaseEvent.EventMessage<StockDeductMessage>>() { }.getType());
            StockDeductMessage data = eventMessage.getData();
            if (data == null || data.getStrategyId() == null || data.getAwardId() == null
                    || data.getSurplus() == null || data.getSurplus() < 0) {
                throw new IllegalArgumentException("非法奖品库存扣减消息: " + json);
            }

            String key = data.getStrategyId() + ":" + data.getAwardId();
            latestStockMap.merge(key, data,
                    (left, right) -> left.getSurplus() <= right.getSurplus() ? left : right);
        }

        for (StockDeductMessage data : latestStockMap.values()) {
            raffleStock.syncStrategyAwardStock(
                    data.getStrategyId(), data.getAwardId(), data.getSurplus());
        }

        log.info("批量消费奖品库存扣减MQ完成 messageCount:{} awardCount:{}",
                messages.size(), latestStockMap.size());
    }
}
