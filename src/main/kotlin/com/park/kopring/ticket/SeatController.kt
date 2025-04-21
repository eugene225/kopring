package com.park.kopring.ticket

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/seats")
class SeatController(
    private val seatService: SeatService
) {

    @PostMapping("/init")
    fun initSeat(@RequestBody seatIds: List<String>): ResponseEntity<String> {
        seatService.initSeats(seatIds)
        return ResponseEntity.ok("좌석 초기화 완료")
    }

    @PostMapping("/hold")
    fun holdSeat(@RequestParam seatId: String, @RequestParam userId: String): ResponseEntity<String> {
        if(seatService.holdSeat(seatId, userId)) {
            return ResponseEntity.ok("선점 성공")
        }else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("이미 선점된 좌석 입니다.")
        }
    }

    @PatchMapping("/confirm")
    fun confirmSeat(@RequestParam seatId: String, @RequestParam userId: String): ResponseEntity<String> {
        if(seatService.confirmSeat(seatId, userId)) {
            return ResponseEntity.ok("좌석 구매 완료")
        }else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("결제 실패")
        }
    }

    @GetMapping("/status")
    fun seatStatus(@RequestParam seatId: String): String =
        seatService.getSeatStatus(seatId) ?: "좌석 정보 없음"
}