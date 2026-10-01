package com.pro.config;

import com.pro.trigger.listener.ActivitySkuStockZeroCustomer;
import com.pro.trigger.listener.CreditAdjustSuccessCustomer;
import com.pro.trigger.listener.RebateMessageCustomer;
import com.pro.trigger.listener.SendAwardCustomer;
import com.pro.trigger.listener.StockMessageBatcher;
import com.pro.trigger.listener.StrategyAwardStockDeductCustomer;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Configuration
public class RocketMqConfig {

    @Value("${rocketmq.name-server}")
    private String nameServer;
    @Value("${rocketmq.consumer-group-prefix}")
    private String groupPrefix;

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQProducer rocketMqProducer(
            @Value("${rocketmq.producer-group}") String group,
            @Value("${rocketmq.send-timeout-ms}") int timeoutMs) {
        DefaultMQProducer producer = new DefaultMQProducer(group);
        producer.setNamesrvAddr(nameServer);
        producer.setSendMsgTimeout(timeoutMs);
        producer.setRetryTimesWhenSendFailed(2);
        return producer;
    }

    @Bean(destroyMethod = "close")
    public StockMessageBatcher stockMessageBatcher(
            @Value("${rocketmq.stock.batch-size}") int batchSize,
            @Value("${rocketmq.stock.flush-interval-ms}") long intervalMs,
            StrategyAwardStockDeductCustomer customer) {
        return new StockMessageBatcher(batchSize, intervalMs, customer::listener);
    }

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQPushConsumer stockConsumer(
            @Value("${rocketmq.topic.strategy_award_stock_deduct}") String topic,
            StockMessageBatcher batcher) throws MQClientException {
        DefaultMQPushConsumer consumer = consumer("stock", topic, messages -> batcher.submit(messages).get());
        consumer.setConsumeMessageBatchMaxSize(500);
        consumer.setPullBatchSize(32);
        return consumer;
    }

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQPushConsumer activityStockZeroConsumer(
            @Value("${rocketmq.topic.activity_sku_stock_zero}") String topic,
            ActivitySkuStockZeroCustomer customer) throws MQClientException {
        return consumer("activity-stock-zero", topic, messages -> {
            for (MessageExt message : messages) customer.listener(body(message));
        });
    }

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQPushConsumer sendAwardConsumer(
            @Value("${rocketmq.topic.send_award}") String topic,
            SendAwardCustomer customer) throws MQClientException {
        return consumer("send-award", topic, messages -> {
            for (MessageExt message : messages) customer.listener(body(message));
        });
    }

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQPushConsumer rebateConsumer(
            @Value("${rocketmq.topic.send_rebate}") String topic,
            RebateMessageCustomer customer) throws MQClientException {
        return consumer("rebate", topic, messages -> {
            for (MessageExt message : messages) customer.listener(body(message));
        });
    }

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQPushConsumer creditConsumer(
            @Value("${rocketmq.topic.credit_adjust_success}") String topic,
            CreditAdjustSuccessCustomer customer) throws MQClientException {
        return consumer("credit-adjust-success", topic, messages -> {
            for (MessageExt message : messages) customer.listener(body(message));
        });
    }

    private DefaultMQPushConsumer consumer(String suffix, String topic, MessageHandler handler)
            throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(groupPrefix + "-" + suffix);
        consumer.setNamesrvAddr(nameServer);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.setConsumeThreadMin(20);
        consumer.setConsumeThreadMax(20);
        consumer.setAwaitTerminationMillisWhenShutdown(30000);
        consumer.subscribe(topic, "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
            try {
                handler.handle(messages);
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            } catch (Exception e) {
                log.error("RocketMQ consumption failed topic:{} group:{}", topic, consumer.getConsumerGroup(), e);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        });
        return consumer;
    }

    private static String body(MessageExt message) {
        return new String(message.getBody(), StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface MessageHandler {
        void handle(List<MessageExt> messages) throws Exception;
    }
}
