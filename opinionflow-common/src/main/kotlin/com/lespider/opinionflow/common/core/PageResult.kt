package com.lespider.opinionflow.common.core

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * 分页响应格式
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PageResult<T>(
    val total: Long = 0,
    val page: Int = 1,
    val size: Int = 10,
    val list: List<T> = emptyList()
)