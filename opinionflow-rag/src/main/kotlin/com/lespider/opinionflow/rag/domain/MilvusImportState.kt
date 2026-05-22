package com.lespider.opinionflow.rag.domain

import jakarta.persistence.*

/**
 * Milvus 导入状态跟踪表
 * 记录每个数据源最后导入到 Milvus 的位置（自增ID或时间戳）
 */
@Entity
@Table(name = "milvus_import_state")
data class MilvusImportState(
    @Id
    @Column(name = "source_name", length = 32)
    var sourceName: String = "",

    /** 最后导入的 MySQL 自增 ID（wynews / falsh_news 使用） */
    @Column(name = "last_imported_id")
    var lastImportedId: Long = 0L,

    /** 最后导入的时间戳（yahoo_finance_news 使用） */
    @Column(name = "last_imported_time", length = 64)
    var lastImportedTime: String? = null,

    /** 最后导入时间 */
    @Column(name = "updated_at", length = 64)
    var updatedAt: String? = null,
)