package com.park.kopring.messagequeue.event

import java.time.LocalDateTime

/**
 * 티켓 주문 이벤트
 * 티켓 예약이 완료되었을 때 발행되는 이벤트
 */
data class TicketOrderEvent(
    val orderId: String,
    val userId: String,
    val ticketId: String,
    val seatNumber: String,
    val price: Int,
    val orderTime: LocalDateTime = LocalDateTime.now()
)
