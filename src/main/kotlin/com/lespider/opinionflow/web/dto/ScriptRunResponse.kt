package com.lespider.opinionflow.web.dto

data class ScriptRunResponse(
    val ok: Boolean,
    val key: String,
    val exitCode: Int? = null,
    val durationMs: Long,
    val stdout: String = "",
    val stderr: String = "",
    val message: String? = null,
)

