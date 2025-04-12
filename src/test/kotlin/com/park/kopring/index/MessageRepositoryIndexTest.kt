package com.park.kopring.index

import com.mongodb.client.model.Filters
import jakarta.annotation.PostConstruct
import org.bson.Document
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query

@SpringBootTest
class CompareMessageIndexPerformanceTest {

    @Autowired
    lateinit var mongoTemplate: MongoTemplate

    val dataCount = 100_000

    @PostConstruct
    fun setup() {
        // 기존 데이터가 있으면 드롭
        mongoTemplate.remove(Query(), Message::class.java)
        mongoTemplate.remove(Query(), IndexedMessage::class.java)

        val messages = mutableListOf<Message>()
        val indexedMessages = mutableListOf<IndexedMessage>()

        for (i in 1..dataCount) {
            val author = "user${i % 50}"
            val room = "room${i % 100}"
            messages.add(Message(text = "Hi $i", author = author, chatRoomId = room))
            indexedMessages.add(IndexedMessage(text = "Hi $i", author = author, chatRoomId = room))
        }

        // 데이터 삽입
        mongoTemplate.insert(messages, Message::class.java)
        mongoTemplate.insert(indexedMessages, IndexedMessage::class.java)

//        val indexOps: IndexOperations = mongoTemplate.indexOps(IndexedMessage::class.java)
//        indexOps.ensureIndex(
//            org.springframework.data.mongodb.core.index.Index()
//                .on("chatRoomId", Sort.Direction.ASC)
//                .on("author", Sort.Direction.ASC)
//        )
    }

    @org.junit.jupiter.api.Test
    fun `measure performance and explain - with index`() {
        val query = Query(
            Criteria.where("chatRoomId").`is`("room10")
                .and("author").`is`("user10")
        )

        val indexOps = mongoTemplate.indexOps("messages_index")
        indexOps.indexInfo.forEach { println(it) }

        val start = System.nanoTime()
        val result = mongoTemplate.find(query, IndexedMessage::class.java)
        val end = System.nanoTime()

        println("✅ With Compound Index - Time: ${(end - start) / 1_000_000}ms, Size: ${result.size}")

        // explain 출력
        val rawQuery = Filters.and(
            Filters.eq("chatRoomId", "room10"),
            Filters.eq("author", "user10")
        )

        val explain = mongoTemplate
            .db
            .getCollection("messages_index")
            .find(rawQuery)
            .explain(Document::class.java)

        println("✅ With Index - Explain:\n$explain")
    }

    @org.junit.jupiter.api.Test
    fun `measure performance and explain - without index`() {
        val query = Query(
            Criteria.where("chatRoomId").`is`("room10")
                .and("author").`is`("user10")
        )

        val start = System.nanoTime()
        val result = mongoTemplate.find(query, Message::class.java)
        val end = System.nanoTime()

        println("❌ Without Index - Time: ${(end - start) / 1_000_000}ms, Size: ${result.size}")

        // explain 출력
        val rawQuery = Filters.and(
            Filters.eq("chatRoomId", "room10"),
            Filters.eq("author", "user10")
        )

        val explain = mongoTemplate
            .db
            .getCollection("messages")
            .find(rawQuery)
            .explain(Document::class.java)

        println("❌ Without Index - Explain:\n$explain")
    }
}