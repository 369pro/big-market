package com.pro.trigger.job;

import com.pro.infrastructure.event.StrategyAwardStockMessagePublisher;
import com.pro.infrastructure.redis.IRedisService;
import com.pro.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** 补偿投递 Redis outbox 中尚未确认发送的奖品库存 MQ 消息。 */
@Slf4j
@Component
public class UpdateAwardStockJob {

    @Resource
    private IRedisService redisService;
    @Resource
    private StrategyAwardStockMessagePublisher stockMessagePublisher;
    @Resource
    private RedissonClient redissonClient;

    @Scheduled(cron = "0/5 * * * * ?")
    public void exec() {
        RLock lock = redissonClient.getLock("big-market-strategyAwardStockMqOutboxJob");
        boolean locked = false;
        try {
            locked = lock.tryLock(3, 0, TimeUnit.SECONDS);
            if (!locked) return;

            Set<String> members = redisService.randomMembersFromSet(
                    Constants.RedisKey.STRATEGY_AWARD_MQ_OUTBOX_SET, 1000);
            for (String member : members) {
                try {
                    stockMessagePublisher.publish(member);
                } catch (Exception e) {
                    log.error("奖品库存MQ outbox补偿发送失败 member={}", member, e);
                }
            }
        } catch (Exception e) {
            log.error("奖品库存MQ outbox补偿任务执行失败", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
