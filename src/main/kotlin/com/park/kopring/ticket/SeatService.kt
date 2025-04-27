package com.park.kopring.ticket

import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.TimeUnit

@Service
class SeatService(
    private val redisTemplate: StringRedisTemplate,
    private val redissonClient: RedissonClient,
) {
    private val REDISSON_LOCK_KEY: String = "LOCK:"

    companion object LuaScript {
        val AVAILABLE_TO_HOLD: DefaultRedisScript<Long> = DefaultRedisScript<Long>().apply {
            setScriptText(
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
            resultType = Long::class.java
        }
    }

    fun initSeats(seats: List<String>) {
        val ops = redisTemplate.opsForValue()
        seats.forEach { seatId ->
            ops.set("seat:$seatId", "AVAILABLE")
        }
    }

    fun holdSeat(seatId: String, userId: String): Boolean {
        return runCatching {
            val result = redisTemplate.execute(LuaScript.AVAILABLE_TO_HOLD, listOf("seat:$seatId"), "HELD:$userId")
            result == 1L
        }.getOrElse { throw RuntimeException("Redis Lua script 실행 실패", it) }
    }

    fun holdSeatWithLock(seatId: String, userId: String): Boolean {
        val rLock: RLock = redissonClient.getLock("$REDISSON_LOCK_KEY$seatId")
        return try {
            if (rLock.tryLock(5, 10, TimeUnit.SECONDS)) { // 락 획득 시도 (최대 5초 대기, 10초 유지)
                println("락 획득 성공: $seatId by $userId")
                val key = "seat:$seatId"
                val current = redisTemplate.opsForValue().get(key)
                if (current == "AVAILABLE") {
                    redisTemplate.opsForValue().set(key, "HELD:$userId")
                    redisTemplate.expire(key, Duration.ofMinutes(5))
                    true
                } else {
                    false
                }
            } else {
                println("락 획득 실패: $seatId by $userId")
                false // 락 획득 실패
            }
        } catch (e: Exception) {
            throw RuntimeException("Redisson 락 처리 중 오류 발생", e)
        } finally {
            if (rLock.isHeldByCurrentThread) {
                println("락 해제: $seatId by $userId")
                rLock.unlock() // 락 해제
            }
        }
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