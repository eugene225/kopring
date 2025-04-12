package com.park.kopring.index

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "messages_index")
@CompoundIndex(
    name = "chatRoomId_author_idx",
    def = "{'chatRoomId': 1, 'author': 1}",
)
data class IndexedMessage(
    @Id
    val id: String? = null,
    val text: String,
    val author: String,
    val chatRoomId: String
)
