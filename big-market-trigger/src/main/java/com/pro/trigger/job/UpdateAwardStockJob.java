package com.pro.trigger.job;

import com.pro.domain.strategy.model.valobj.StrategyAwardStockKeyVO;
import com.pro.domain.strategy.service.IRaffleAward;
import com.pro.domain.strategy.service.IRaffleStock;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component()
public class UpdateAwardStockJob {
    @Resource
    private IRaffleStock raffleStock;
    @Resource
    private IRaffleAward raffleAward;
    @Resource
    private ThreadPoolExecutor executor;
    @Resource
    private RedissonClient redissonClient;

    /**
     * 本地化任务注解；@Scheduled(cron = "0/5 * * * * ?")
     * 分布式任务注解； @XxlJob("updateAwardStockJob")
     */
//    @XxlJob("updateAwardStockJob")
    @Scheduled(cron = "0 * * * * ?")
    public void exec() {
        // 为什么加锁？分布式应用N台机器部署互备，任务调度会有N个同时执行，那么这里需要增加抢占机制，谁抢占到谁就执行。完毕后，下一轮继续抢占。
        RLock lock = redissonClient.getLock("big-market-updateAwardStockJob");
        try {
            boolean isLocked = lock.tryLock(3, 0, TimeUnit.SECONDS);
            if (!isLocked) return;
            // log.info("定时任务，更新奖品消耗库存【延迟队列获取，降低对数据库的更新频次，不要产生竞争】");
            List<StrategyAwardStockKeyVO> strategyAwardStockKeyVOS = raffleAward.queryOpenActivityStrategyAwardList();
            if (null == strategyAwardStockKeyVOS) return;
            for (StrategyAwardStockKeyVO strategyAwardStockKeyVO : strategyAwardStockKeyVOS) {
                executor.execute(() -> {
                    try {
                        StrategyAwardStockKeyVO queueStrategyAwardStockKeyVO = raffleStock.takeQueueValue(
                                strategyAwardStockKeyVO.getStrategyId(), strategyAwardStockKeyVO.getAwardId());
                        if (null == queueStrategyAwardStockKeyVO) return;
                        log.info("定时任务，更新奖品消耗库存 strategyId:{} awardId:{}",
                                queueStrategyAwardStockKeyVO.getStrategyId(), queueStrategyAwardStockKeyVO.getAwardId());
                        raffleStock.updateStrategyAwardStock(queueStrategyAwardStockKeyVO.getStrategyId(), queueStrategyAwardStockKeyVO.getAwardId());
                    } catch (InterruptedException e) {
                        log.error("定时任务，更新奖品消耗库存失败 strategyId:{} awardId:{}",
                                strategyAwardStockKeyVO.getStrategyId(), strategyAwardStockKeyVO.getAwardId());
                    }
                });
            }
        } catch (Exception e) {
            log.error("定时任务，更新奖品消耗库存失败", e);
        }finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
