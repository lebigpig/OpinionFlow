package com.lespider.opinionflow.spider.dto

data class ScriptRunResponse(
    val ok: Boolean,
    val key: String,
    val exitCode: Int? = null,
    val durationMs: Long = 0,
    val stdout: String = "",
    val stderr: String = "",
    val message: String? = null,
)