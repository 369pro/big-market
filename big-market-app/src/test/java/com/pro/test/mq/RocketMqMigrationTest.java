package com.pro.test.mq;

import com.pro.config.RocketMqConfig;
import com.pro.domain.strategy.service.IRaffleStock;
import com.pro.infrastructure.event.EventPublisher;
import com.pro.infrastructure.event.StrategyAwardStockMessagePublisher;
import com.pro.infrastructure.redis.IRedisService;
import com.pro.domain.strategy.event.StrategyAwardStockDeductMessageEvent;
import com.pro.trigger.listener.SendAwardCustomer;
import com.pro.trigger.listener.StrategyAwardStockDeductCustomer;
import com.pro.types.common.Constants;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RocketMqMigrationTest {

    @Test
    public void producerRequiresSendOk() throws Exception {
        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        EventPublisher publisher = new EventPublisher(producer);
        SendResult result = new SendResult();
        result.setSendStatus(SendStatus.FLUSH_DISK_TIMEOUT);
        when(producer.send(any(Message.class))).thenReturn(result);
        try {
            publisher.publish("stock", "{}");
            fail("A failed disk flush must not clear the outbox");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("RocketMQ send failed"));
        }
        result.setSendStatus(SendStatus.SEND_OK);
        publisher.publish("stock", "{}");
        verify(producer, times(2)).send(any(Message.class));
    }

    @Test
    public void outboxSurvivesSendFailureAndIsRemovedAfterConfirmation() {
        EventPublisher publisher = mock(EventPublisher.class);
        IRedisService redis = mock(IRedisService.class);
        StrategyAwardStockDeductMessageEvent event = new StrategyAwardStockDeductMessageEvent();
        ReflectionTestUtils.setField(event, "topic", "stock");
        StrategyAwardStockMessagePublisher stockPublisher = new StrategyAwardStockMessagePublisher();
        ReflectionTestUtils.setField(stockPublisher, "eventPublisher", publisher);
        ReflectionTestUtils.setField(stockPublisher, "stockDeductMessageEvent", event);
        ReflectionTestUtils.setField(stockPublisher, "redisService", redis);
        String member = "100001:101:unique-event:9";
        doThrow(new IllegalStateException("Broker unavailable")).doNothing()
                .when(publisher).publishConfirm(eq("stock"), any());
        try {
            stockPublisher.publish(member);
            fail("Expected broker failure");
        } catch (IllegalStateException expected) {
            verifyNoInteractions(redis);
        }
        stockPublisher.publish(member);
        verify(redis).removeFromSet(Constants.RedisKey.STRATEGY_AWARD_MQ_OUTBOX_SET, member);
    }

    @Test
    public void consumerReturnsRetryOnBusinessFailure() throws Exception {
        RocketMqConfig config = new RocketMqConfig();
        ReflectionTestUtils.setField(config, "nameServer", "localhost:9876");
        ReflectionTestUtils.setField(config, "groupPrefix", "test-market");
        SendAwardCustomer customer = mock(SendAwardCustomer.class);
        doThrow(new IllegalStateException("Database unavailable")).doNothing().when(customer).listener("{}");
        DefaultMQPushConsumer consumer = config.sendAwardConsumer("award", customer);
        MessageListenerConcurrently listener = (MessageListenerConcurrently) consumer.getMessageListener();
        ConsumeConcurrentlyContext context = new ConsumeConcurrentlyContext(new MessageQueue("award", "broker", 0));
        assertEquals(ConsumeConcurrentlyStatus.RECONSUME_LATER,
                listener.consumeMessage(Collections.singletonList(message("{}")), context));
        assertEquals(ConsumeConcurrentlyStatus.CONSUME_SUCCESS,
                listener.consumeMessage(Collections.singletonList(message("{}")), context));
    }

    @Test
    public void inventoryAggregatesDuplicatesAndOutOfOrderSnapshots() {
        IRaffleStock stock = mock(IRaffleStock.class);
        StrategyAwardStockDeductCustomer customer = new StrategyAwardStockDeductCustomer();
        ReflectionTestUtils.setField(customer, "raffleStock", stock);
        customer.listener(Arrays.asList(stockMessage(101, 8), stockMessage(101, 9),
                stockMessage(101, 8), stockMessage(102, 4)));
        verify(stock).syncStrategyAwardStock(100001L, 101, 8);
        verify(stock).syncStrategyAwardStock(100001L, 102, 4);
        verifyNoMoreInteractions(stock);
    }

    @Test
    public void invalidStockMessageDoesNotPartiallyWriteBatch() {
        IRaffleStock stock = mock(IRaffleStock.class);
        StrategyAwardStockDeductCustomer customer = new StrategyAwardStockDeductCustomer();
        ReflectionTestUtils.setField(customer, "raffleStock", stock);
        try {
            customer.listener(Arrays.asList(stockMessage(101, 8), stockMessage(102, -1)));
            fail("Expected invalid stock rejection");
        } catch (IllegalArgumentException expected) {
            verifyNoInteractions(stock);
        }
    }

    static MessageExt stockMessage(int awardId, int surplus) {
        return message("{\"id\":\"event\",\"data\":{\"strategyId\":100001,\"awardId\":"
                + awardId + ",\"surplus\":" + surplus + "}}");
    }

    static MessageExt message(String body) {
        MessageExt message = new MessageExt();
        message.setBody(body.getBytes(StandardCharsets.UTF_8));
        return message;
    }
}
