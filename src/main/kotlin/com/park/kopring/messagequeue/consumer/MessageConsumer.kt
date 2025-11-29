package com.park.kopring.messagequeue.consumer

import com.park.kopring.messagequeue.config.RabbitMQConfig
import com.park.kopring.messagequeue.event.EmailNotificationEvent
import com.park.kopring.messagequeue.event.TicketOrderEvent
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * 메시지 소비 서비스
 * RabbitMQ로부터 메시지를 수신하고 처리하는 역할
 */
@Service
class MessageConsumer {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    // 테스트 검증용: 수신한 메시지 저장
    val receivedOrders = ConcurrentHashMap<String, TicketOrderEvent>()
    val receivedEmails = mutableListOf<EmailNotificationEvent>()
    val receivedSms = mutableListOf<EmailNotificationEvent>()
    val receivedPush = mutableListOf<EmailNotificationEvent>()
    val receivedPayments = mutableListOf<Map<*, *>>()
    val failedMessages = mutableListOf<Any>()

    /**
     * Point-to-Point: 티켓 주문 메시지 수신
     */
    @RabbitListener(queues = [RabbitMQConfig.ORDER_QUEUE])
    fun handleOrderMessage(event: TicketOrderEvent) {
        logger.info("Received order message: $event")
        try {
            // 주문 처리 로직
            processOrder(event)
            receivedOrders[event.orderId] = event
        } catch (e: Exception) {
            logger.error("Failed to process order: ${event.orderId}", e)
            throw e // 재시도를 위해 예외를 다시 던짐
        }
    }

    /**
     * Pub/Sub: 이메일 알림 수신
     */
    @RabbitListener(queues = [RabbitMQConfig.EMAIL_QUEUE])
    fun handleEmailNotification(event: EmailNotificationEvent) {
        logger.info("Received email notification: $event")
        sendEmail(event)
        receivedEmails.add(event)
    }

    /**
     * Pub/Sub: SMS 알림 수신
     */
    @RabbitListener(queues = [RabbitMQConfig.SMS_QUEUE])
    fun handleSmsNotification(event: EmailNotificationEvent) {
        logger.info("Received SMS notification: $event")
        sendSms(event)
        receivedSms.add(event)
    }

    /**
     * Pub/Sub: Push 알림 수신
     */
    @RabbitListener(queues = [RabbitMQConfig.PUSH_QUEUE])
    fun handlePushNotification(event: EmailNotificationEvent) {
        logger.info("Received push notification: $event")
        sendPush(event)
        receivedPush.add(event)
    }

    /**
     * Work Queue: 결제 처리 메시지 수신
     * 여러 인스턴스가 동시에 실행되면 메시지를 분산 처리
     */
    @RabbitListener(queues = [RabbitMQConfig.PAYMENT_QUEUE], concurrency = "3")
    fun handlePaymentMessage(message: Map<*, *>) {
        logger.info("Worker processing payment: $message")
        try {
            // 결제 처리 시뮬레이션 (시간이 걸리는 작업)
            Thread.sleep(100)
            processPayment(message)
            receivedPayments.add(message)
        } catch (e: Exception) {
            logger.error("Failed to process payment: $message", e)
            throw e
        }
    }

    /**
     * Dead Letter Queue: 실패한 메시지 수신
     */
    @RabbitListener(queues = [RabbitMQConfig.DLQ_QUEUE])
    fun handleDeadLetterMessage(message: Any) {
        logger.warn("Received dead letter message: $message")
        failedMessages.add(message)
        // 실패한 메시지에 대한 알림, 로깅, 재처리 등의 로직
    }

    // ===== 비즈니스 로직 (실제로는 다른 서비스에 위임) =====

    private fun processOrder(event: TicketOrderEvent) {
        logger.info("Processing order: ${event.orderId}")
        // 실제 주문 처리 로직
    }

    private fun sendEmail(event: EmailNotificationEvent) {
        logger.info("Sending email to ${event.recipientEmail}: ${event.subject}")
        // 실제 이메일 발송 로직
    }

    private fun sendSms(event: EmailNotificationEvent) {
        logger.info("Sending SMS to ${event.recipientEmail}")
        // 실제 SMS 발송 로직
    }

    private fun sendPush(event: EmailNotificationEvent) {
        logger.info("Sending push notification to ${event.recipientEmail}")
        // 실제 푸시 알림 로직
    }

    private fun processPayment(message: Map<*, *>) {
        logger.info("Processing payment for order: ${message["orderId"]}")
        // 실제 결제 처리 로직
    }

    // 테스트용 헬퍼 메서드
    fun clear() {
        receivedOrders.clear()
        receivedEmails.clear()
        receivedSms.clear()
        receivedPush.clear()
        receivedPayments.clear()
        failedMessages.clear()
    }
}
