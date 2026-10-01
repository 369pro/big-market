package com.pro.infrastructure.event;

import com.alibaba.fastjson.JSON;
import com.pro.types.event.BaseEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 消息发送
 * @create 2024-03-30 12:40
 */
@Slf4j
@Component
public class EventPublisher {

    private final DefaultMQProducer producer;

    @Autowired
    public EventPublisher(DefaultMQProducer producer) {
        this.producer = producer;
    }

    public void publish(String topic, BaseEvent.EventMessage<?> eventMessage) {
        send(topic, JSON.toJSONString(eventMessage), eventMessage.getId());
    }

    public void publish(String topic, String eventMessageJSON){
        send(topic, eventMessageJSON, null);
    }

    /**
     * 同步发送并检查 SEND_OK，确认成功后调用方才能删除 outbox。
     */
    public void publishConfirm(String topic, BaseEvent.EventMessage<?> eventMessage) {
        publish(topic, eventMessage);
    }

    private void send(String topic, String json, String eventId) {
        Message message = new Message(topic, json.getBytes(StandardCharsets.UTF_8));
        if (eventId != null) message.setKeys(eventId);
        try {
            SendResult result = producer.send(message);
            if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
                throw new IllegalStateException("RocketMQ did not confirm delivery: " + result);
            }
            log.info("RocketMQ sent topic:{} messageId:{}", topic, result.getMsgId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("RocketMQ send interrupted: " + topic, e);
        } catch (Exception e) {
            throw new IllegalStateException("RocketMQ send failed: " + topic, e);
        }
    }
}
