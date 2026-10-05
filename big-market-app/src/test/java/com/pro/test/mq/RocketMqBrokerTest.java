package com.pro.test.mq;

import com.pro.config.RocketMqConfig;
import com.pro.infrastructure.event.EventPublisher;
import com.pro.trigger.listener.StockMessageBatcher;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.junit.Assume;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Opt-in real broker test; never connects to the application's configured services. */
public class RocketMqBrokerTest {

    @Test
    public void brokerRedeliversWhenAggregatedWriteFails() throws Exception {
        String nameServer = System.getenv("ROCKETMQ_TEST_NAMESRV");
        Assume.assumeTrue(nameServer != null && !nameServer.isEmpty());
        String topic = "big_market_migration_test";
        String group = "market-test-" + UUID.randomUUID();
        RocketMqConfig config = new RocketMqConfig();
        ReflectionTestUtils.setField(config, "nameServer", nameServer);
        ReflectionTestUtils.setField(config, "groupPrefix", group);
        CountDownLatch retried = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        try (StockMessageBatcher batcher = new StockMessageBatcher(500, 100, messages -> {
            if (attempts.getAndIncrement() == 0) throw new IllegalStateException("Simulated database failure");
            if (messages.stream().anyMatch(message -> message.getReconsumeTimes() > 0)) retried.countDown();
        })) {
            DefaultMQPushConsumer consumer = config.stockConsumer(topic, batcher);
            DefaultMQProducer producer = config.rocketMqProducer(group + "-producer", 5000);
            try {
                consumer.start();
                producer.start();
                new EventPublisher(producer).publish(topic, "{\"id\":\"retry-test\",\"data\":{\"surplus\":8}}");
                assertTrue("Broker must redeliver the unacknowledged batch", retried.await(45, TimeUnit.SECONDS));
                assertTrue(attempts.get() >= 2);
            } finally {
                consumer.shutdown();
                producer.shutdown();
            }
        }
    }
}
