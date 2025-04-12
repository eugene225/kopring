package com.park.kopring.index

import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface MessageRepository : MongoRepository<Message, String>{
}