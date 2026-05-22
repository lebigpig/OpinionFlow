package com.lespider.opinionflow.ai.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "chat_history")
class ChatHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @field:Column(name = "session_id", nullable = false, length = 255)
    var sessionId: String = "",

    @field:Column(name = "role", nullable = false, length = 20)
    var role: String = "",  // "user" / "assistant"

    @field:Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    var content: String = "",

    @field:Column(name = "token_count")
    var tokenCount: Int = 0,

    @field:Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: LocalDateTime? = null,
)