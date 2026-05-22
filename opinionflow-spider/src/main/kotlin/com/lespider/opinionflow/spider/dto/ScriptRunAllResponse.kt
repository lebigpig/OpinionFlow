package com.lespider.opinionflow.spider.dto

data class ScriptRunAllResponse(
    val ok: Boolean,
    val durationMs: Long,
    val results: Map<String, ScriptRunResponse>,
    val message: String? = null,
)