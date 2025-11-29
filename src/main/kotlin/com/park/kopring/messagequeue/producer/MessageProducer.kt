package com.park.kopring.messagequeue.producer

import com.park.kopring.messagequeue.config.RabbitMQConfig
import com.park.kopring.messagequeue.event.EmailNotificationEvent
import com.park.kopring.messagequeue.event.TicketOrderEvent
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service

/**
 * 메시지 발행 서비스
 * RabbitMQ에 메시지를 발행하는 역할
 */
@Service
class MessageProducer(
    private val rabbitTemplate: RabbitTemplate
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Point-to-Point: 티켓 주문 메시지 발행
     * 특정 Queue에 직접 메시지를 보냄
     */
    fun sendOrderMessage(event: TicketOrderEvent) {
        logger.info("Sending order message: $event")
        rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_QUEUE, event)
    }

    /**
     * Pub/Sub: 알림 메시지 발행
     * Fanout Exchange를 통해 모든 구독자에게 메시지를 보냄
     */
    fun publishNotification(event: EmailNotificationEvent) {
        logger.info("Publishing notification: $event")
        rabbitTemplate.convertAndSend(RabbitMQConfig.NOTIFICATION_EXCHANGE, "", event)
    }

    /**
     * Work Queue: 결제 처리 메시지 발행
     * 여러 Worker가 분산 처리할 수 있도록 메시지를 보냄
     */
    fun sendPaymentMessage(orderId: String, amount: Int) {
        val message = mapOf(
            "orderId" to orderId,
            "amount" to amount,
            "timestamp" to System.currentTimeMillis()
        )
        logger.info("Sending payment message: $message")
        rabbitTemplate.convertAndSend(RabbitMQConfig.PAYMENT_QUEUE, message)
    }

    /**
     * 메시지 발행 with 우선순위
     */
    fun sendOrderMessageWithPriority(event: TicketOrderEvent, priority: Int) {
        logger.info("Sending order message with priority $priority: $event")
        rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_QUEUE, event) { message ->
            message.messageProperties.priority = priority
            message
        }
    }
}
