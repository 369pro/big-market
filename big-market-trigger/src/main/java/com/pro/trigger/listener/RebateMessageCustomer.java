package com.pro.trigger.listener;

import java.math.BigDecimal;

import javax.annotation.Resource;

import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.pro.domain.activity.model.entity.SkuRechargeEntity;
import com.pro.domain.activity.model.valobj.OrderTradeTypeVO;
import com.pro.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.pro.domain.credit.model.entity.TradeEntity;
import com.pro.domain.credit.model.valobj.TradeNameVO;
import com.pro.domain.credit.model.valobj.TradeTypeVO;
import com.pro.domain.credit.service.ICreditAdjustService;
import com.pro.domain.rebate.event.SendRebateMessageEvent;
import com.pro.types.enums.ResponseCode;
import com.pro.types.event.BaseEvent;
import com.pro.types.exception.AppException;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 监听；行为返利消息
 * @create 2024-05-01 13:58
 */
@Slf4j
@Component
public class RebateMessageCustomer {
    @Value("${spring.rabbitmq.topic.send_rebate}")
    private String topic;

    @Resource
    private IRaffleActivityAccountQuotaService raffleActivityAccountQuotaService;
    @Resource
    private ICreditAdjustService creditAdjustService;

    @RabbitListener(queuesToDeclare = @Queue(value = "${spring.rabbitmq.topic.send_rebate}"))
    public void listener(String message) {
        try {
            log.info("监听用户行为返利发送消息 topic: {} message: {}", topic, message);
            // 1. 转换对象
            BaseEvent.EventMessage<SendRebateMessageEvent.RebateMessage> eventMessage =
                    JSON.parseObject(message, new TypeReference<BaseEvent.EventMessage<SendRebateMessageEvent.RebateMessage>>() {}.getType());
            SendRebateMessageEvent.RebateMessage rebateMessage = eventMessage.getData();
            if (rebateMessage == null || rebateMessage.getUserId() == null || "null".equals(rebateMessage.getUserId())
                    || rebateMessage.getBizId() == null || rebateMessage.getBizId().trim().isEmpty()) {
                throw new IllegalArgumentException("Invalid rebate message identity");
            }
            switch (rebateMessage.getRebateType()){
                case "sku":
                    SkuRechargeEntity skuRechargeEntity = new SkuRechargeEntity();
                    skuRechargeEntity.setSku(Long.valueOf(rebateMessage.getRebateConfig()));
                    skuRechargeEntity.setUserId(rebateMessage.getUserId());
                    skuRechargeEntity.setOutBusinessNo(rebateMessage.getBizId());
                    skuRechargeEntity.setOrderTradeType(OrderTradeTypeVO.rebate_no_pay_trade);
                    raffleActivityAccountQuotaService.createOrder(skuRechargeEntity);
                    break;
                case "integral":
                    TradeEntity tradeEntity = TradeEntity.builder()
                            .userId(rebateMessage.getUserId())
                            .tradeName(TradeNameVO.REBATE)
                            .tradeType(TradeTypeVO.FORWARD)
                            .amount(new BigDecimal(rebateMessage.getRebateConfig()))
                            .outBusinessNo(rebateMessage.getBizId()).build();
                    creditAdjustService.createOrder(tradeEntity);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported rebate type: " + rebateMessage.getRebateType());
            }
        } catch (AppException e) {
            // Services return normally only for verified duplicates; failed grants must be retried.
            throw e;
        }catch (Exception e) {
            log.error("监听用户行为返利发送消息，消费失败 topic: {} message: {}", topic, message);
            throw e;
        }
    }
}
