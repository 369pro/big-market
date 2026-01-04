package com.pro.domain.activity.service;

import com.pro.domain.activity.model.aggregate.CreateOrderAggregate;
import com.pro.domain.activity.model.entity.*;
import com.pro.domain.activity.repository.IActivityRepository;
import com.pro.domain.activity.service.rule.IActionChain;
import com.pro.domain.activity.service.rule.factory.DefaultActivityChainFactory;
import com.pro.types.enums.ResponseCode;
import com.pro.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 抽奖活动抽象类，定义标准的流程
 * @create 2024-03-16 08:42
 */
@Slf4j
public abstract class AbstractRaffleActivity extends RaffleActivitySupport implements IRaffleOrder {
//    protected IActivityRepository activityRepository;
//    protected DefaultActivityChainFactory defaultActivityChainFactory;
    public AbstractRaffleActivity(IActivityRepository activityRepository, DefaultActivityChainFactory defaultActivityChainFactory) {
        super(activityRepository, defaultActivityChainFactory);
    }

    @Override
    public String createSkuRechargeOrder(SkuRechargeEntity skuRechargeEntity) {
        // 1. 参数校验
        String userId = skuRechargeEntity.getUserId();
        Long sku = skuRechargeEntity.getSku();
        String outBusinessNo = skuRechargeEntity.getOutBusinessNo();
        if (null == sku || StringUtils.isBlank(userId) || StringUtils.isBlank(outBusinessNo)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }

        // 2. 查询基础信息，为后续执行责任链
        ActivitySkuEntity activitySkuEntity = queryActivitySku(sku);
        ActivityEntity activityEntity = queryRaffleActivityByActivityId(activitySkuEntity.getActivityId());
        ActivityCountEntity activityCountEntity = 
                queryRaffleActivityCountByActivityCountId(activitySkuEntity.getActivityCountId());
        IActionChain actionChain = defaultActivityChainFactory.openActionChain();
        // 3. 活动动作规则校验【责任链】
        actionChain.action(activitySkuEntity, activityEntity, activityCountEntity);
        // 4. 构建订单聚合对象
        CreateOrderAggregate createOrderAggregate = buildOrderAggregate(skuRechargeEntity, activitySkuEntity, activityEntity, activityCountEntity);
        // 5. 保存订单，更新用户活动次数
        doSaveOrder(createOrderAggregate);
        // 6. 返回单号
        return createOrderAggregate.getActivityOrderEntity().getOrderId();
    }

    protected abstract CreateOrderAggregate buildOrderAggregate(SkuRechargeEntity skuRechargeEntity,
                                                                ActivitySkuEntity activitySkuEntity,
                                                                ActivityEntity activityEntity,
                                                                ActivityCountEntity activityCountEntity);

    protected abstract void doSaveOrder(CreateOrderAggregate createOrderAggregate);
}
