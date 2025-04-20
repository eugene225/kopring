package com.park.kopring.ticket

class TicketService {
    private var stock = 10

    fun buyTicket(): Boolean {
        if (stock > 0) {
            Thread.sleep(10)
            stock--
            return true
        }
        return false
    }

    fun getStock(): Int {
        return stock
    }

}