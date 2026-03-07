package com.pro.trigger.listener;

import javax.annotation.Resource;

import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.pro.domain.award.adapter.event.SendAwardMessageEvent;
import com.pro.domain.award.model.entity.DistributeAwardEntity;
import com.pro.domain.award.service.IAwardService;
import com.pro.types.event.BaseEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 用户奖品记录消息消费者
 * @create 2024-04-06 12:09
 */
@Slf4j
@Component
public class SendAwardCustomer {
    @Resource
    private IAwardService awardService;

    @Value("${spring.rabbitmq.topic.send_award}")
    private String topic;

    @RabbitListener(queuesToDeclare = @Queue(value = "${spring.rabbitmq.topic.send_award}"))
    public void listener(String message) throws Exception {
        try {
            log.info("监听用户奖品发送消息，发奖开始 topic: {} message: {}", topic, message);
            BaseEvent.EventMessage<SendAwardMessageEvent.SendAwardMessage> eventMessage = JSON.parseObject(message, new TypeReference<BaseEvent.EventMessage<SendAwardMessageEvent.SendAwardMessage>>() {
            }.getType());
            SendAwardMessageEvent.SendAwardMessage sendAwardMessage = eventMessage.getData();

            // 发放奖品, 给哪个用户的哪笔抽奖订单发奖励
            DistributeAwardEntity distributeAwardEntity = new DistributeAwardEntity();
            distributeAwardEntity.setUserId(sendAwardMessage.getUserId());
            distributeAwardEntity.setOrderId(sendAwardMessage.getOrderId());
            distributeAwardEntity.setAwardId(sendAwardMessage.getAwardId());
            distributeAwardEntity.setAwardConfig(sendAwardMessage.getAwardConfig());
            awardService.distributeAward(distributeAwardEntity);
            log.info("监听用户奖品发送消息，发奖完成 topic: {} message: {}", topic, message);
        } catch (Exception e) {
            log.error("监听用户奖品发送消息，消费失败 topic: {} message: {}", topic, message);
            throw e;
        }
    }

}
