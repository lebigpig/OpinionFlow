package com.lespider.opinionflow.runner

import com.lespider.opinionflow.service.MilvusNewsImportService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * 应用启动后自动触发 Milvus 数据导入
 * 使用 ApplicationRunner 确保在 Spring 容器完全初始化后执行
 */
@Component
class MilvusImportRunner(
    private val importService: MilvusNewsImportService,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments?) {
        log.info("[Milvus] 启动导入检查...")
        try {
            val result = importService.importAllIfNeeded()
            if (result.skipped) {
                log.info("[Milvus] 导入已跳过: {}", result.reason)
            } else {
                log.info("[Milvus] 导入完成，共导入 {} 条数据", result.totalImported)
            }
        } catch (e: Exception) {
            log.error("[Milvus] 启动导入失败: {}", e.message, e)
        }
    }
}