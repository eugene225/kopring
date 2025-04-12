package com.park.kopring.index

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "messages")
data class Message(
        @Id
        val id: String? = null,
        val text: String,
        val author: String,
        val chatRoomId: String
)