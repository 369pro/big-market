package com.pro.domain.activity.service.quota.rule.impl;

import com.pro.domain.activity.model.entity.QuotaPlanEntity;
import com.pro.domain.activity.model.entity.ActivityEntity;
import com.pro.domain.activity.model.entity.ActivitySkuEntity;
import com.pro.domain.activity.model.valobj.ActivitySkuStockKeyVO;
import com.pro.domain.activity.repository.IActivityRepository;
import com.pro.domain.activity.service.armory.IActivityDispatch;
import com.pro.domain.activity.service.quota.rule.AbstractActionChain;
import com.pro.types.enums.ResponseCode;
import com.pro.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 商品库存规则节点
 * @create 2024-03-23 10:25
 */
@Slf4j
@Component("activity_sku_stock_action")
public class ActivitySkuStockActionChain extends AbstractActionChain {
    @Resource
    private IActivityDispatch activityDispatch;
    @Resource
    private IActivityRepository activityRepository;

    @Override
    public boolean action(ActivitySkuEntity sku, ActivityEntity activity, QuotaPlanEntity count, String reservationId) {
        if (reservationId == null) return action(sku, activity, count);
        if (!activityRepository.reserveActivitySkuStock(sku.getSku(), reservationId, activity.getEndDateTime())) {
            throw new AppException(ResponseCode.ACTIVITY_SKU_STOCK_ERROR.getCode(), ResponseCode.ACTIVITY_SKU_STOCK_ERROR.getInfo());
        }
        return true;
    }

    /**
     * 用户参与签到，就可以领取活动（需要扣减活动库存），之后通过活动抽奖
     */
    @Override
    public boolean action(ActivitySkuEntity activitySkuEntity, ActivityEntity activityEntity,
                          QuotaPlanEntity quotaPlanEntity) {
        log.info("活动责任链-商品库存处理【有效期、状态、库存(sku)】开始。sku:{} activityId:{}",
                activitySkuEntity.getSku(),
                activityEntity.getActivityId());
        // 扣减【活动库存】 raffle_quota_product, note RDS扣减
        boolean ststus = activityDispatch.subtractionActivitySkuStock(activitySkuEntity.getSku(), activityEntity.getEndDateTime());
        if(!ststus){
            throw new AppException(ResponseCode.ACTIVITY_SKU_STOCK_ERROR.getCode(), ResponseCode.ACTIVITY_SKU_STOCK_ERROR.getInfo());
        }
        log.info("活动责任链-商品库存扣减处理【有效期、状态、库存(sku)】成功。sku:{} activityId:{}", activitySkuEntity.getSku(),
                activityEntity.getActivityId());
        // 发送库存延迟消息，由定时任务 drainTo 聚合后批量 UPDATE DB
        ActivitySkuStockKeyVO activitySkuStockKeyVO = ActivitySkuStockKeyVO.builder()
                .activityId(activityEntity.getActivityId())
                .sku(activitySkuEntity.getSku()).build();
        activityRepository.activitySkuStockConsumeSendQueue(activitySkuStockKeyVO);
        return true;
    }

}
