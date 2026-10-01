package com.pro.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Bean
    public Queue strategyAwardStockDeductQueue(
            @Value("${spring.rabbitmq.topic.strategy_award_stock_deduct}") String queueName) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean("strategyAwardStockBatchContainerFactory")
    public SimpleRabbitListenerContainerFactory strategyAwardStockBatchContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setConsumerBatchEnabled(true);
        factory.setBatchListener(true);
        factory.setBatchSize(500);
        factory.setPrefetchCount(500);
        factory.setReceiveTimeout(1000L);
        return factory;
    }
}
