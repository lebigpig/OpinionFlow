package com.lespider.opinionflow.repository

import com.lespider.opinionflow.domain.ChatHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ChatHistoryRepository : JpaRepository<ChatHistory, Long> {
    /**
     * 查询指定 sessionId 下的所有对话记录，按创建时间正序排列
     */
    fun findBySessionIdOrderByCreatedAtAsc(sessionId: String): List<ChatHistory>

    /**
     * 查询指定 sessionId 最新的一条记录（用于获取当前 content 内容）
     */
    fun findFirstBySessionIdOrderByCreatedAtDesc(sessionId: String): ChatHistory?

    /**
     * 删除指定 sessionId 的所有对话记录
     */
    fun deleteBySessionId(sessionId: String)

    /**
     * 查询所有不同的 sessionId，按最后消息时间倒序
     */
    @Query(value = "SELECT session_id FROM chat_history GROUP BY session_id ORDER BY MAX(created_at) DESC", nativeQuery = true)
    fun findDistinctSessionIds(): List<String>

    /**
     * 查询指定 sessionId 下第一条用户消息
     */
    @Query("SELECT ch FROM ChatHistory ch WHERE ch.sessionId = :sessionId AND ch.role = 'user' ORDER BY ch.createdAt ASC LIMIT 1")
    fun findFirstUserMessage(@Param("sessionId") sessionId: String): ChatHistory?

    /**
     * 查询指定 sessionId 下最新的助手消息
     */
    @Query("SELECT ch FROM ChatHistory ch WHERE ch.sessionId = :sessionId AND ch.role = 'assistant' ORDER BY ch.createdAt DESC LIMIT 1")
    fun findLastAssistantMessage(@Param("sessionId") sessionId: String): ChatHistory?
}