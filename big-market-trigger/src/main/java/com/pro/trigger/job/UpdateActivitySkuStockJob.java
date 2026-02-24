package com.pro.trigger.job;

import com.pro.domain.activity.model.valobj.ActivitySkuStockKeyVO;
import com.pro.domain.activity.service.IRaffleActivitySkuStockService;
import com.pro.infrastructure.dao.IRaffleActivitySkuDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author 369pro
 * @description 更新活动sku库存任务
 * @create 2024-03-30 09:52
 */
@Slf4j
@Component()
public class UpdateActivitySkuStockJob {
    @Resource
    private IRaffleActivitySkuStockService skuStock;
    @Resource
    private IRaffleActivitySkuDao raffleActivitySkuDao;

    @Scheduled(cron = "0/2 * * * * ?")
    public void exec() {
        try {
            // log.info("定时任务，更新活动sku库存【延迟队列获取，降低对数据库的更新频次，不要产生竞争】");
            List<Long> skuList = raffleActivitySkuDao.listSku();
            for(Long sku : skuList){
                ActivitySkuStockKeyVO activitySkuStockKeyVO = skuStock.takeQueueValue(sku);
                if (null == activitySkuStockKeyVO) {
                    continue;
                }
                log.info("UpdateActivitySkuStockJob，更新活动sku库存 sku:{} activityId:{}",
                        activitySkuStockKeyVO.getSku(), activitySkuStockKeyVO.getActivityId());
                skuStock.updateActivitySkuStock(activitySkuStockKeyVO.getSku());
            }
        }catch (Exception e) {
            log.error("UpdateActivitySkuStockJob，更新活动sku库存失败", e);
        }
    }
}
