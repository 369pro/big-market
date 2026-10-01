package com.pro.infrastructure.event;

import com.alibaba.fastjson.JSON;
import com.pro.types.event.BaseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 消息发送
 * @create 2024-03-30 12:40
 */
@Slf4j
@Component
public class EventPublisher {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void publish(String topic, BaseEvent.EventMessage<?> eventMessage) {
        try {
            String messageJson = JSON.toJSONString(eventMessage);
            rabbitTemplate.convertAndSend(topic, messageJson);
            log.info("发送MQ消息 topic:{} message:{}", topic, messageJson);
        } catch (Exception e) {
            log.error("发送MQ消息失败 topic:{} message:{}", topic, JSON.toJSONString(eventMessage), e);
            throw e;
        }
    }

    public void publish(String topic, String eventMessageJSON){
        try {
            rabbitTemplate.convertAndSend(topic, eventMessageJSON);
            log.info("发送MQ消息 topic:{} message:{}", topic, eventMessageJSON);
        } catch (Exception e) {
            log.error("发送MQ消息失败 topic:{} message:{}", topic, eventMessageJSON, e);
            throw e;
        }
    }

    /**
     * 在独占 Channel 上发布并等待 broker confirm。只有确认成功后，调用方才能删除 outbox。
     */
    public void publishConfirm(String topic, BaseEvent.EventMessage<?> eventMessage) {
        String messageJson = JSON.toJSONString(eventMessage);
        rabbitTemplate.invoke(operations -> {
            operations.convertAndSend(topic, messageJson);
            operations.waitForConfirmsOrDie(5000);
            return null;
        });
        log.info("发送MQ消息并收到确认 topic:{} messageId:{}", topic, eventMessage.getId());
    }
}
