package com.pro.trigger.job;

import com.pro.domain.strategy.service.IRaffleStock;
import com.pro.infrastructure.redis.IRedisService;
import com.pro.types.common.Constants;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 奖品库存批量回写DB任务
 * <p>从 Redis Set 中 SPOP 弹出扣减记录，分组聚合后批量 UPDATE。
 * 替代了旧的延迟队列 poll 方案，实现真正的批量聚合写入。
 */
@Slf4j
@Component()
public class UpdateAwardStockJob {
    @Resource
    private IRaffleStock raffleStock;
    @Resource
    private IRedisService redisService;
    @Resource
    private ThreadPoolExecutor executor;
    @Resource
    private RedissonClient redissonClient;

    /**
     * 本地化任务注解；@Scheduled(cron = "0/5 * * * * ?")
     * 分布式任务注解；@XxlJob("updateAwardStockJob")
     */
//    @XxlJob("updateAwardStockJob")
    @Scheduled(cron = "0 * * * * ?")
    public void exec() {
        RLock lock = redissonClient.getLock("big-market-updateAwardStockJob");
        try {
            boolean isLocked = lock.tryLock(3, 0, TimeUnit.SECONDS);
            if (!isLocked) return;

            // 从追踪 Set 中 SPOP 弹出最多 1000 条扣减记录（原子操作，弹出即消费）
            Set<String> members = redisService.popFromSet(
                    Constants.RedisKey.STRATEGY_AWARD_TRACK_SET, 1000);
            if (members == null || members.isEmpty()) return;

            // 分组计数: {"100001:101"×5, "100001:102"×2, ...} → Map<(sid,aid), count>
            Map<String, Long> countMap = members.stream()
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

            log.info("定时任务，批量更新奖品消耗库存 涉及{}个奖品 总扣减{}次",
                    countMap.size(), members.size());

            for (Map.Entry<String, Long> entry : countMap.entrySet()) {
                String[] parts = entry.getKey().split(Constants.COLON);
                Long strategyId = Long.valueOf(parts[0]);
                Integer awardId = Integer.valueOf(parts[1]);
                int count = entry.getValue().intValue();

                executor.execute(() -> {
                    log.info("批量更新奖品库存 strategyId:{} awardId:{} count:{}",
                            strategyId, awardId, count);
                    raffleStock.updateStrategyAwardStock(strategyId, awardId, count);
                });
            }
        } catch (Exception e) {
            log.error("定时任务，批量更新奖品消耗库存失败", e);
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
