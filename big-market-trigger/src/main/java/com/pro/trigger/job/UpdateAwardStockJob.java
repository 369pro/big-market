package com.pro.trigger.job;

import com.pro.domain.strategy.model.valobj.StrategyAwardStockKeyVO;
import com.pro.domain.strategy.service.IRaffleStock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component()
public class UpdateAwardStockJob {
    @Resource
    private IRaffleStock raffleStock;
//    private StrategyRepository strategyRepository;

    @Scheduled(cron = "0/5 * * * * ?")
    public void exec() {
        try {
            log.info("定时任务，更新奖品消耗库存【延迟队列获取，降低对数据库的更新频次，不要产生竞争】");
            StrategyAwardStockKeyVO strategyAwardStockKeyVO = raffleStock.takeQueueValue();
            if(null == strategyAwardStockKeyVO) {
                log.info("定时任务，获取队列数据为空");
                return;
            }
            Long strategyId = strategyAwardStockKeyVO.getStrategyId();
            Integer awardId = strategyAwardStockKeyVO.getAwardId();
            log.info("定时任务，更新奖品消耗库存 strategyId:{} awardId:{}", strategyId, awardId);
            raffleStock.updateStrategyAwardStock(
                    strategyId, awardId);
        } catch (InterruptedException e) {
            log.error("定时任务，更新奖品消耗库存失败", e);
        }
    }
}
