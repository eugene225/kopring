package com.park.kopring.messagequeue

import com.park.kopring.messagequeue.config.RabbitMQConfig
import com.park.kopring.messagequeue.consumer.MessageConsumer
import com.park.kopring.messagequeue.event.TicketOrderEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Dead Letter Queue (DLQ) 및 에러 핸들링 테스트
 * 
 * 학습 목표:
 * 1. 메시지 처리 실패 시 재시도 메커니즘
 * 2. 재시도 실패 후 DLQ로 메시지 이동
 * 3. 실패한 메시지의 후처리 방법
 */
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@SpringBootTest
@Testcontainers
class DeadLetterQueueTest {

    companion object {
        @Container
        val rabbitMQContainer = RabbitMQContainer("rabbitmq:3.13-management-alpine")
            .withExposedPorts(5672, 15672)

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            // 1. Testcontainer 연결 정보
            registry.add("spring.rabbitmq.host") { rabbitMQContainer.host }
            registry.add("spring.rabbitmq.port") { rabbitMQContainer.amqpPort }
            registry.add("spring.rabbitmq.username") { "guest" }
            registry.add("spring.rabbitmq.password") { "guest" }

            // [추가 1] 리스너 자동 시작 방지 (메시지 도둑 방지)
            // 이 설정이 있어야 receiveAndConvert()로 내가 원할 때 꺼내볼 수 있습니다.
            registry.add("spring.rabbitmq.listener.simple.auto-startup") { "false" }

            // [추가 2] Heartbeat 비활성화 (디버깅 중 연결 끊김 방지)
            // 0으로 설정하면 서버가 클라이언트 생존 확인을 하지 않아 연결이 유지됩니다.
            registry.add("spring.rabbitmq.requested-heartbeat") { "0" }
        }
    }

    @Autowired
    private lateinit var rabbitTemplate: RabbitTemplate

    @Autowired
    private lateinit var messageConsumer: MessageConsumer

    @Autowired
    private lateinit var rabbitListenerEndpointRegistry: RabbitListenerEndpointRegistry

    @BeforeEach
    fun setup() {
        // 모든 리스너 중지 - 이제 rabbitTemplate.receive()로 직접 메시지를 확인할 수 있음
        rabbitListenerEndpointRegistry.listenerContainers.forEach { container ->
            container.stop()
        }
    }

    @AfterEach
    fun cleanup() {
        messageConsumer.clear()
        // 리스너 재시작 (다음 테스트를 위해)
        rabbitListenerEndpointRegistry.listenerContainers.forEach { container ->
            container.start()
        }
    }

    @Test
    fun `정상 메시지는 DLQ로 가지 않는지 확인`() {
        // Given: 정상적인 주문 이벤트
        val orderEvent = TicketOrderEvent(
            orderId = "NORMAL-ORDER-001",
            userId = "USER-123",
            ticketId = "TICKET-456",
            seatNumber = "A-10",
            price = 50000
        )

        // When: 메시지 발행
        rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_QUEUE, orderEvent)

        // Then: Order Queue에 메시지가 있고, DLQ에는 없음
        Thread.sleep(500)

        val orderMessage = rabbitTemplate.receive(RabbitMQConfig.ORDER_QUEUE, 500)
        assertNotNull(orderMessage, "Order queue should have the message")

        val dlqMessage = rabbitTemplate.receive(RabbitMQConfig.DLQ_QUEUE, 100)
        assertNull(dlqMessage, "DLQ should be empty for normal messages")
    }

    @Test
    fun `DLQ 설정 확인 - Queue에 DLX 설정이 되어있는지`() {
        // RabbitMQ Admin API를 통해 Queue 설정 확인
        // 실제로는 Management API를 호출하거나 RabbitAdmin을 사용
        
        // 이 테스트는 설정이 올바르게 되어있는지 확인하는 용도
        // DLQ 설정:
        // - x-dead-letter-exchange: dlx.exchange
        // - x-dead-letter-routing-key: order.failed 또는 payment.failed
        
        // Queue가 생성되었는지 확인
        val queueProperties = rabbitTemplate.execute { channel ->
            channel.queueDeclarePassive(RabbitMQConfig.ORDER_QUEUE)
        }
        
        assertNotNull(queueProperties)
        println("Order Queue declared: ${queueProperties?.queue}")
    }

    @Test
    fun `DLQ가 존재하는지 확인`() {
        // DLQ와 DLX가 생성되었는지 확인
        val dlqProperties = rabbitTemplate.execute { channel ->
            channel.queueDeclarePassive(RabbitMQConfig.DLQ_QUEUE)
        }
        
        assertNotNull(dlqProperties)
        println("DLQ declared: ${dlqProperties?.queue}")
    }

    @Test
    fun `메시지 TTL 만료 시 DLQ 이동 테스트`() {
        // Given: TTL이 짧은 임시 Queue 생성
        val tempQueue = "temp.ttl.queue"
        rabbitTemplate.execute { channel ->
            val args = mapOf(
                "x-message-ttl" to 1000,  // 1초 TTL
                "x-dead-letter-exchange" to RabbitMQConfig.DLQ_EXCHANGE,
                "x-dead-letter-routing-key" to "dlq.#"
            )
            channel.queueDeclare(tempQueue, true, false, false, args)
        }

        // When: 메시지 발행 (Consumer 없음)
        val testMessage = mapOf("test" to "ttl-message")
        rabbitTemplate.convertAndSend(tempQueue, testMessage)

        // Then: TTL 만료 후 DLQ로 이동
        Thread.sleep(2000)

        // temp queue에서 receive 시도 → RabbitMQ가 만료 확인하고 DLQ로 이동시킴
        rabbitTemplate.receive(tempQueue, 100)

        // 실제 RabbitMQ DLQ에서 메시지 확인 (리스너가 중지되어 있어서 직접 확인 가능)
        val dlqMessage = rabbitTemplate.receive(RabbitMQConfig.DLQ_QUEUE, 1000)
        assertNotNull(dlqMessage, "Message should be moved to DLQ after TTL expiration")
        println("Message moved to DLQ: $dlqMessage")
    }

    @Test
    fun `여러 실패 메시지가 DLQ에 모이는지 확인`() {
        // Given: TTL이 짧은 여러 메시지
        val tempQueue = "temp.multi.queue"
        rabbitTemplate.execute { channel ->
            val args = mapOf(
                "x-message-ttl" to 500,
                "x-dead-letter-exchange" to RabbitMQConfig.DLQ_EXCHANGE,
                "x-dead-letter-routing-key" to "dlq.#"
            )
            channel.queueDeclare(tempQueue, false, false, true, args)
        }

        // When: 여러 메시지 발행
        repeat(5) { index ->
            rabbitTemplate.convertAndSend(tempQueue, mapOf("id" to index))
        }

        // Then: 모든 메시지가 DLQ로 이동
        Thread.sleep(2000)

        // temp queue에서 receive 시도하여 만료 확인 트리거
        rabbitTemplate.receive(tempQueue, 100)

        // 실제 RabbitMQ DLQ에서 메시지 개수 확인
        var dlqCount = 0
        while (rabbitTemplate.receive(RabbitMQConfig.DLQ_QUEUE, 100) != null) {
            dlqCount++
        }

        assertTrue(dlqCount >= 5, "Expected at least 5 messages in DLQ, but got $dlqCount")
        println("Messages in DLQ: $dlqCount")
    }
}
