package com.pro.trigger.listener;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.pro.domain.activity.model.entity.DeliveryOrderEntity;
import com.pro.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.pro.domain.credit.event.CreditAdjustSuccessMessageEvent;
import com.pro.types.enums.ResponseCode;
import com.pro.types.event.BaseEvent;
import com.pro.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 积分调整成功消息
 * @create 2024-06-08 19:38
 */
@Slf4j
@Component
public class CreditAdjustSuccessCustomer {

    @Value("${rocketmq.topic.credit_adjust_success}")
    private String topic;
    @Resource
    private IRaffleActivityAccountQuotaService raffleActivityAccountQuotaService;

    public void listener(String message) {
        try {
            log.info("监听积分账户调整成功消息，进行交易商品发货 topic: {} message: {}", topic, message);
            BaseEvent.EventMessage<CreditAdjustSuccessMessageEvent.CreditAdjustSuccessMessage> eventMessage = JSON.parseObject(message, new TypeReference<BaseEvent.EventMessage<CreditAdjustSuccessMessageEvent.CreditAdjustSuccessMessage>>() {
            }.getType());
            CreditAdjustSuccessMessageEvent.CreditAdjustSuccessMessage creditAdjustSuccessMessage = eventMessage.getData();
            if (creditAdjustSuccessMessage == null
                    || creditAdjustSuccessMessage.getAmount() == null
                    || StringUtils.isBlank(creditAdjustSuccessMessage.getUserId())
                    || StringUtils.isBlank(creditAdjustSuccessMessage.getOutBusinessNo())) {
                throw new IllegalArgumentException("Missing credit adjustment payload");
            }
            // Positive rebate/award credits are not payments for an activity order.
            if (creditAdjustSuccessMessage.getAmount().signum() >= 0) return;

            // 积分发货
            DeliveryOrderEntity deliveryOrderEntity = new DeliveryOrderEntity();
            deliveryOrderEntity.setUserId(creditAdjustSuccessMessage.getUserId());
            deliveryOrderEntity.setOutBusinessNo(creditAdjustSuccessMessage.getOutBusinessNo());
            raffleActivityAccountQuotaService.updateOrder(deliveryOrderEntity);
        } catch (AppException e) {
            // An unchanged completed order already returns normally; other conflicts require retry.
            throw e;
        } catch (Exception e) {
            log.error("监听积分账户调整成功消息，进行交易商品发货失败 topic: {} message: {}", topic, message, e);
            throw e;
        }
    }

}
