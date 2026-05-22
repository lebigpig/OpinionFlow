package com.lespider.opinionflow.ai.repo

import com.lespider.opinionflow.ai.domain.ChatHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ChatHistoryRepository : JpaRepository<ChatHistory, Long> {
    fun findBySessionIdOrderByCreatedAtAsc(sessionId: String): List<ChatHistory>
    fun findFirstBySessionIdOrderByCreatedAtDesc(sessionId: String): ChatHistory?
    fun deleteBySessionId(sessionId: String)

    @Query(value = "SELECT session_id FROM chat_history GROUP BY session_id ORDER BY MAX(created_at) DESC", nativeQuery = true)
    fun findDistinctSessionIds(): List<String>

    @Query("SELECT ch FROM ChatHistory ch WHERE ch.sessionId = :sessionId AND ch.role = 'user' ORDER BY ch.createdAt ASC LIMIT 1")
    fun findFirstUserMessage(@Param("sessionId") sessionId: String): ChatHistory?

    @Query("SELECT ch FROM ChatHistory ch WHERE ch.sessionId = :sessionId AND ch.role = 'assistant' ORDER BY ch.createdAt DESC LIMIT 1")
    fun findLastAssistantMessage(@Param("sessionId") sessionId: String): ChatHistory?
}