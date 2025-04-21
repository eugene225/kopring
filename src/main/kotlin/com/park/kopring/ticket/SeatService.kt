package com.park.kopring.ticket

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class SeatService(
    private val redisTemplate: StringRedisTemplate
) {

    fun initSeats(seats: List<String>) {
        val ops = redisTemplate.opsForValue()
        seats.forEach { seatId ->
            ops.set("seat:$seatId", "AVAILABLE")
        }
    }

    fun holdSeat(seatId: String, userId: String): Boolean {
        val script = DefaultRedisScript<Long>()
        script.setScriptText(
            """
            if redis.call('get', KEYS[1]) == 'AVAILABLE' then
                redis.call('set', KEYS[1], ARGV[1])
                redis.call('expire', KEYS[1], 300)
                return 1
            else
                return 0
            end
            """.trimIndent()
        )
        script.resultType = Long::class.java

        val result = redisTemplate.execute(script, listOf("seat:$seatId"), "HELD:$userId")
        return result == 1L
    }

    fun confirmSeat(seatId: String, userId: String): Boolean {
        val current = redisTemplate.opsForValue().get("seat:$seatId")
        return if (current == "HELD:$userId") {
            redisTemplate.opsForValue().set("seat:$seatId", "SOLD")
            redisTemplate.expire("seat:$seatId", Duration.ofDays(365)) // 영구
            true
        } else {
            false
        }
    }

    fun getSeatStatus(seatId: String): String? {
        return redisTemplate.opsForValue().get("seat:$seatId")
    }

    fun releaseSeat(seatId: String, userId: String) {
        val key = "seat:$seatId"
        val current = redisTemplate.opsForValue().get(key)
        if (current == "HELD:$userId") {
            redisTemplate.opsForValue().set(key, "AVAILABLE")
            redisTemplate.expire(key, Duration.ofMinutes(10))
        }
    }

}