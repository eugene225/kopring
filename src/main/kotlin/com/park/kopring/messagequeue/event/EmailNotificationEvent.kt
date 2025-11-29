package com.park.kopring.messagequeue.event

import java.time.LocalDateTime

/**
 * 이메일 알림 이벤트
 * 사용자에게 이메일을 발송해야 할 때 발행되는 이벤트
 */
data class EmailNotificationEvent(
    val recipientEmail: String,
    val subject: String,
    val body: String,
    val eventType: EmailType,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class EmailType {
    ORDER_CONFIRMATION,  // 주문 확인
    PAYMENT_SUCCESS,     // 결제 성공
    TICKET_ISSUED,       // 티켓 발급
    CANCELLATION         // 취소 확인
}
