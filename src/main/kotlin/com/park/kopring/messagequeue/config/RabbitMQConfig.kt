package com.park.kopring.messagequeue.config

import org.springframework.amqp.core.*
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * RabbitMQ 설정
 * Queue, Exchange, Binding 및 메시지 변환기 설정
 */
@Configuration
class RabbitMQConfig {

    companion object {
        // Point-to-Point 패턴용
        const val ORDER_QUEUE = "ticket.order.queue"
        
        // Pub/Sub 패턴용 (Fanout Exchange)
        const val NOTIFICATION_EXCHANGE = "notification.fanout"
        const val EMAIL_QUEUE = "notification.email.queue"
        const val SMS_QUEUE = "notification.sms.queue"
        const val PUSH_QUEUE = "notification.push.queue"
        
        // Work Queue 패턴용
        const val PAYMENT_QUEUE = "payment.processing.queue"
        
        // Dead Letter Queue
        const val DLQ_EXCHANGE = "dlx.exchange"
        const val DLQ_QUEUE = "dlq.queue"
        const val DLQ_ROUTING_KEY = "dlq.#"
    }

    /**
     * JSON 메시지 변환기
     * 객체를 JSON으로 직렬화/역직렬화
     */
    @Bean
    fun messageConverter(): MessageConverter {
        return Jackson2JsonMessageConverter()
    }

    /**
     * RabbitTemplate 설정
     * 메시지 발행 시 사용
     */
    @Bean
    fun rabbitTemplate(connectionFactory: ConnectionFactory): RabbitTemplate {
        val template = RabbitTemplate(connectionFactory)
        template.messageConverter = messageConverter()
        return template
    }

    /**
     * Listener Container Factory 설정
     * 메시지 수신 시 사용
     */
    @Bean
    fun rabbitListenerContainerFactory(connectionFactory: ConnectionFactory): SimpleRabbitListenerContainerFactory {
        val factory = SimpleRabbitListenerContainerFactory()
        factory.setConnectionFactory(connectionFactory)
        factory.setMessageConverter(messageConverter())
        return factory
    }

    // ===== Point-to-Point 패턴 설정 =====
    
    @Bean
    fun orderQueue(): Queue {
        return QueueBuilder.durable(ORDER_QUEUE)
            .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
            .withArgument("x-dead-letter-routing-key", "order.failed")
            .build()
    }

    // ===== Pub/Sub 패턴 설정 (Fanout Exchange) =====
    
    @Bean
    fun notificationExchange(): FanoutExchange {
        return FanoutExchange(NOTIFICATION_EXCHANGE)
    }

    @Bean
    fun emailQueue(): Queue {
        return Queue(EMAIL_QUEUE, true)
    }

    @Bean
    fun smsQueue(): Queue {
        return Queue(SMS_QUEUE, true)
    }

    @Bean
    fun pushQueue(): Queue {
        return Queue(PUSH_QUEUE, true)
    }

    @Bean
    fun emailBinding(emailQueue: Queue, notificationExchange: FanoutExchange): Binding {
        return BindingBuilder.bind(emailQueue).to(notificationExchange)
    }

    @Bean
    fun smsBinding(smsQueue: Queue, notificationExchange: FanoutExchange): Binding {
        return BindingBuilder.bind(smsQueue).to(notificationExchange)
    }

    @Bean
    fun pushBinding(pushQueue: Queue, notificationExchange: FanoutExchange): Binding {
        return BindingBuilder.bind(pushQueue).to(notificationExchange)
    }

    // ===== Work Queue 패턴 설정 =====
    
    @Bean
    fun paymentQueue(): Queue {
        return QueueBuilder.durable(PAYMENT_QUEUE)
            .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
            .withArgument("x-dead-letter-routing-key", "payment.failed")
            .build()
    }

    // ===== Dead Letter Queue 설정 =====
    
    @Bean
    fun deadLetterExchange(): DirectExchange {
        return DirectExchange(DLQ_EXCHANGE)
    }

    @Bean
    fun deadLetterQueue(): Queue {
        return Queue(DLQ_QUEUE, true)
    }

    @Bean
    fun deadLetterBinding(deadLetterQueue: Queue, deadLetterExchange: DirectExchange): Binding {
        return BindingBuilder.bind(deadLetterQueue)
            .to(deadLetterExchange)
            .with(DLQ_ROUTING_KEY)
    }
}
