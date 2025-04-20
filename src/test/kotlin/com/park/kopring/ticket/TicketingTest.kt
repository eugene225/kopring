package com.park.kopring.ticket

import org.junit.jupiter.api.Assertions.assertEquals
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test

class TicketingTest {
    private val ticketService = TicketService();

    @Test
    fun `동시성 없이 티켓 구매 시 재고 초과 가능성`() {
        val successCount = mutableListOf<Boolean>()
        val threadCount = 100
        val executor = Executors.newFixedThreadPool(10)
        val latch = CountDownLatch(threadCount)

        repeat(threadCount) {
            executor.submit {
                val success = ticketService.buyTicket()
                synchronized(successCount) {
                    successCount.add(success)
                }
                latch.countDown()
            }
        }

        latch.await()

        val boughtCount = successCount.count { it }
        println("성공한 구매 수: $boughtCount")
        println("남은 재고: ${ticketService.getStock()}")

        // 테스트 실패할 수도 있음
        assertEquals(10, boughtCount)
        assertEquals(0, ticketService.getStock())
    }
}