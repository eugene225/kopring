package com.park.kopring.ticket

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
class SeatServiceRetryConfirmTest @Autowired constructor(
    private val seatService: SeatService
) {
    private val seatIds = listOf("A1", "A2", "A3", "A4", "A5")

    @BeforeEach
    fun init() {
        seatService.initSeats(seatIds)
    }

    @Test
    fun `랜덤 구매 실패 상황에서도 좌석이 결국 모두 판매되는지 테스트`() {
        val soldSeats = mutableSetOf<String>()
        val userCounter = AtomicInteger(0)
        val random = java.util.Random()
        val executor = Executors.newFixedThreadPool(10)

        while (soldSeats.size < seatIds.size) {
            val latch = CountDownLatch(1)

            executor.submit {
                val seatId = seatIds.filter { it !in soldSeats }.random()
                val userId = "user${userCounter.getAndIncrement()}"

                val held = seatService.holdSeat(seatId, userId)
                if (held) {
                    println("✅ 선점 성공: $seatId by $userId")

                    // 랜덤으로 결제 성공/실패
                    val confirmSuccess = if (random.nextBoolean()) {
                        seatService.confirmSeat(seatId, userId)
                    } else {
                        println("💥 $userId 결제 실패 (강제 실패)")
                        false
                    }

                    if (confirmSuccess) {
                        println("🎉 구매 완료: $seatId by $userId")
                        soldSeats.add(seatId)
                    } else {
                        // 실패 시 AVAILABLE 복원
                        seatService.releaseSeat(seatId, userId)
                        println("🔁 $seatId 좌석 복원됨")
                    }
                } else {
                    println("❌ 선점 실패: $seatId by $userId")
                }

                latch.countDown()
            }

            latch.await()
        }

        // 최종 검증
        seatIds.forEach { seatId ->
            val status = seatService.getSeatStatus(seatId)
            println("🪑 최종 상태: $seatId = $status")
            assert(status == "SOLD")
        }
    }

    @Test
    fun `락을 활용한 동시성 테스트`() {
        val soldSeats = mutableSetOf<String>()
        val userCounter = AtomicInteger(0)
        val random = java.util.Random()
        val executor = Executors.newFixedThreadPool(10)

        while (soldSeats.size < seatIds.size) {
            val latch = CountDownLatch(1)

            executor.submit {
                val seatId = seatIds.filter { it !in soldSeats }.random()
                val userId = "user${userCounter.getAndIncrement()}"

                val held = seatService.holdSeatWithLock(seatId, userId)
                if (held) {
                    println("✅ 선점 성공: $seatId by $userId")

                    // 랜덤으로 결제 성공/실패
                    val confirmSuccess = if (random.nextBoolean()) {
                        seatService.confirmSeat(seatId, userId)
                    } else {
                        println("💥 $userId 결제 실패 (강제 실패)")
                        false
                    }

                    if (confirmSuccess) {
                        println("🎉 구매 완료: $seatId by $userId")
                        soldSeats.add(seatId)
                    } else {
                        // 실패 시 AVAILABLE 복원
                        seatService.releaseSeat(seatId, userId)
                        println("🔁 $seatId 좌석 복원됨")
                    }
                } else {
                    println("❌ 선점 실패: $seatId by $userId")
                }

                latch.countDown()
            }

            latch.await()
        }

        // 최종 검증
        seatIds.forEach { seatId ->
            val status = seatService.getSeatStatus(seatId)
            println("🪑 최종 상태: $seatId = $status")
            assert(status == "SOLD")
        }
    }
}
