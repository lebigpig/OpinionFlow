package com.lespider.opinionflow.spider.dto

data class ScriptRunRequest(
    val key: String? = null,
    val code: String? = null,
    /** 股票代码等额外参数（key=finance 时使用，如 600519） */
    val symbol: String? = null,
)