package com.park.kopring.index

import org.springframework.data.mongodb.repository.MongoRepository

interface IndexedMessageRepository: MongoRepository<IndexedMessage, String> {
}