package com.lespider.opinionflow.web.dto

data class ScriptRunAllResponse(
    val ok: Boolean,
    val durationMs: Long,
    val results: Map<String, ScriptRunResponse>,
    val message: String? = null,
)

