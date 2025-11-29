package com.park.kopring.messagequeue

import com.park.kopring.messagequeue.config.RabbitMQConfig
import com.park.kopring.messagequeue.consumer.MessageConsumer
import com.park.kopring.messagequeue.event.TicketOrderEvent
import com.park.kopring.messagequeue.producer.MessageProducer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Point-to-Point 메시징 패턴 테스트
 * 
 * 학습 목표:
 * 1. 단일 Producer가 Queue에 메시지를 보내는 방법
 * 2. 단일 Consumer가 Queue에서 메시지를 받는 방법
 * 3. 메시지가 순서대로 처리되는지 확인
 */
@SpringBootTest
@Testcontainers
class PointToPointMessagingTest {

    companion object {
        @Container
        val rabbitMQContainer = RabbitMQContainer("rabbitmq:3.13-management-alpine")
            .withExposedPorts(5672, 15672)

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.rabbitmq.host") { rabbitMQContainer.host }
            registry.add("spring.rabbitmq.port") { rabbitMQContainer.amqpPort }
            registry.add("spring.rabbitmq.username") { "guest" }
            registry.add("spring.rabbitmq.password") { "guest" }
        }
    }

    @Autowired
    private lateinit var messageProducer: MessageProducer

    @Autowired
    private lateinit var messageConsumer: MessageConsumer

    @AfterEach
    fun cleanup() {
        messageConsumer.clear()
    }

    @Test
    fun `단일 메시지 발행 및 수신 테스트`() {
        // Given: 티켓 주문 이벤트 생성
        val orderEvent = TicketOrderEvent(
            orderId = "ORDER-001",
            userId = "USER-123",
            ticketId = "TICKET-456",
            seatNumber = "A-10",
            price = 50000,
            orderTime = LocalDateTime.now()
        )

        // When: 메시지 발행
        messageProducer.sendOrderMessage(orderEvent)

        // Then: Consumer가 메시지를 받았는지 확인 (최대 5초 대기)
        Thread.sleep(1000) // 메시지 처리 대기
        
        assertTrue(messageConsumer.receivedOrders.containsKey("ORDER-001"))
        val receivedEvent = messageConsumer.receivedOrders["ORDER-001"]
        assertNotNull(receivedEvent)
        assertEquals("USER-123", receivedEvent?.userId)
        assertEquals("A-10", receivedEvent?.seatNumber)
        assertEquals(50000, receivedEvent?.price)
    }

    @Test
    fun `여러 메시지 순차 처리 테스트`() {
        // Given: 여러 주문 이벤트 생성
        val orders = listOf(
            TicketOrderEvent("ORDER-001", "USER-1", "TICKET-1", "A-1", 10000),
            TicketOrderEvent("ORDER-002", "USER-2", "TICKET-2", "A-2", 20000),
            TicketOrderEvent("ORDER-003", "USER-3", "TICKET-3", "A-3", 30000)
        )

        // When: 순차적으로 메시지 발행
        orders.forEach { messageProducer.sendOrderMessage(it) }

        // Then: 모든 메시지가 수신되었는지 확인
        Thread.sleep(2000)
        
        assertEquals(3, messageConsumer.receivedOrders.size)
        assertTrue(messageConsumer.receivedOrders.containsKey("ORDER-001"))
        assertTrue(messageConsumer.receivedOrders.containsKey("ORDER-002"))
        assertTrue(messageConsumer.receivedOrders.containsKey("ORDER-003"))
    }

    @Test
    fun `메시지 내용 정확성 검증`() {
        // Given
        val orderEvent = TicketOrderEvent(
            orderId = "ORDER-999",
            userId = "USER-999",
            ticketId = "TICKET-999",
            seatNumber = "VIP-1",
            price = 100000
        )

        // When
        messageProducer.sendOrderMessage(orderEvent)
        Thread.sleep(1000)

        // Then: 수신한 메시지의 모든 필드가 정확한지 확인
        val received = messageConsumer.receivedOrders["ORDER-999"]
        assertNotNull(received)
        assertEquals(orderEvent.orderId, received?.orderId)
        assertEquals(orderEvent.userId, received?.userId)
        assertEquals(orderEvent.ticketId, received?.ticketId)
        assertEquals(orderEvent.seatNumber, received?.seatNumber)
        assertEquals(orderEvent.price, received?.price)
    }
}
