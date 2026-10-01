package com.pro.domain.activity.service.quota;

import com.pro.domain.activity.model.aggregate.CreateQuotaOrderAggregate;
import com.pro.domain.activity.model.entity.*;
import com.pro.domain.activity.model.valobj.OrderTradeTypeVO;
import com.pro.domain.activity.repository.IActivityRepository;
import com.pro.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.pro.domain.activity.service.quota.policy.ITradePolicy;
import com.pro.domain.activity.service.quota.rule.IActionChain;
import com.pro.domain.activity.service.quota.rule.factory.DefaultActivityChainFactory;
import com.pro.types.enums.ResponseCode;
import com.pro.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 抽奖活动抽象类，定义标准的流程
 * @create 2024-03-16 08:42
 */
@Slf4j
public abstract class AbstractRaffleActivityAccountQuota extends RaffleActivityAccountQuotaSupport implements IRaffleActivityAccountQuotaService {
    // 不同类型的交易策略实现类，通过构造函数注入到 Map 中，
    // 教程；https://bugstack.cn/md/road-map/spring-dependency-injection.html
    private final Map<String, ITradePolicy> tradePolicyGroup;

    public AbstractRaffleActivityAccountQuota(IActivityRepository activityRepository,
                                              DefaultActivityChainFactory defaultActivityChainFactory,
                                              Map<String, ITradePolicy> tradePolicyGroup) {
        super(activityRepository, defaultActivityChainFactory);
        this.tradePolicyGroup = tradePolicyGroup;
    }

    /**
     * 返利consumer类中 处理的
     * @param skuRechargeEntity 活动商品充值实体对象
     * @return 返回 【活动抽奖单订单ID】
     */
    @Override
    public UnpaidActivityOrderEntity createOrder(SkuRechargeEntity skuRechargeEntity) {
        // 1. 参数校验
        String userId = skuRechargeEntity.getUserId();
        Long sku = skuRechargeEntity.getSku();
        String outBusinessNo = skuRechargeEntity.getOutBusinessNo();
        if (null == sku || StringUtils.isBlank(userId) || StringUtils.isBlank(outBusinessNo)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }

        UnpaidActivityOrderEntity existing = activityRepository.queryActivityOrder(skuRechargeEntity);
        if (existing != null) return existing;
        // 2. 查询基础信息
        // 3.1 查询基础信息「sku、活动、次数」
        ActivitySkuEntity activitySkuEntity = queryActivitySku(sku);
        // 3.2 查询活动信息
        ActivityEntity activityEntity = queryRaffleActivityByActivityId(activitySkuEntity.getActivityId());
        // 3.3 查询次数信息（用户在活动上可参与的次数）
        ActivityCountEntity activityCountEntity = queryRaffleActivityCountByActivityCountId(activitySkuEntity.getActivityCountId());

        // 4. 活动动作规则校验 「过滤失败则直接抛异常」
        IActionChain actionChain = defaultActivityChainFactory.openActionChain();
        String reservationId = org.apache.commons.codec.digest.DigestUtils.sha256Hex(userId + "\0" + outBusinessNo);
        actionChain.action(activitySkuEntity, activityEntity, activityCountEntity, reservationId);

        // 5. 构建订单聚合对象
        CreateQuotaOrderAggregate createOrderAggregate = buildOrderAggregate(skuRechargeEntity, activitySkuEntity, activityEntity, activityCountEntity);

        // 6. 保存订单
        // doSaveOrder(createOrderAggregate);
        ITradePolicy tradePolicy = tradePolicyGroup.get(skuRechargeEntity.getOrderTradeType().getCode());
        try {
            tradePolicy.trade(createOrderAggregate);
        } catch (AppException e) {
            if (!ResponseCode.INDEX_DUP.getCode().equals(e.getCode())) throw e;
            existing = activityRepository.queryActivityOrder(skuRechargeEntity);
            if (existing == null) throw e;
            return existing;
        }
        // 7. 返回订单信息
        ActivityOrderEntity activityOrderEntity = createOrderAggregate.getActivityOrderEntity();
        return UnpaidActivityOrderEntity.builder()
                .userId(userId)
                .orderId(activityOrderEntity.getOrderId())
                .outBusinessNo(activityOrderEntity.getOutBusinessNo())
                .payAmount(activityOrderEntity.getPayAmount())
                .build();
    }

    protected abstract CreateQuotaOrderAggregate buildOrderAggregate(SkuRechargeEntity skuRechargeEntity, ActivitySkuEntity
            activitySkuEntity, ActivityEntity activityEntity, ActivityCountEntity activityCountEntity);

}
