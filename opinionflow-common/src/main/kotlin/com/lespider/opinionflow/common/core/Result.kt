package com.lespider.opinionflow.common.core

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * 统一响应格式
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Result<T>(
    val code: Int = 200,
    val msg: String = "success",
    val data: T? = null
) {
    companion object {
        fun <T> ok(data: T): Result<T> = Result(code = 200, msg = "success", data = data)

        fun ok(): Result<Nothing> = Result(code = 200, msg = "success")

        fun <T> fail(msg: String): Result<T> = Result(code = 500, msg = msg)

        fun <T> fail(code: Int, msg: String): Result<T> = Result(code = code, msg = msg)
    }
}