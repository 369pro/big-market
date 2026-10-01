package com.pro.infrastructure.event;

import com.pro.domain.strategy.event.StrategyAwardStockDeductMessageEvent;
import com.pro.domain.strategy.event.StrategyAwardStockDeductMessageEvent.StockDeductMessage;
import com.pro.infrastructure.redis.IRedisService;
import com.pro.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/** 发送奖品库存扣减消息，并在 broker confirm 后清理 Redis outbox。 */
@Slf4j
@Component
public class StrategyAwardStockMessagePublisher {

    @Resource
    private EventPublisher eventPublisher;
    @Resource
    private StrategyAwardStockDeductMessageEvent stockDeductMessageEvent;
    @Resource
    private IRedisService redisService;

    public void publish(String outboxMember) {
        StockDeductMessage message = StockDeductMessage.fromOutboxMember(outboxMember);
        eventPublisher.publishConfirm(
                stockDeductMessageEvent.topic(),
                stockDeductMessageEvent.buildEventMessage(outboxMember, message));
        redisService.removeFromSet(Constants.RedisKey.STRATEGY_AWARD_MQ_OUTBOX_SET, outboxMember);
    }
}
