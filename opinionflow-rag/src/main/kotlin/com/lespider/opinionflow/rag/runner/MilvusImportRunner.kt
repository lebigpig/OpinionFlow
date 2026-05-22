package com.lespider.opinionflow.rag.runner

import com.lespider.opinionflow.rag.service.MilvusNewsImportService
import io.milvus.client.MilvusServiceClient
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component

/**
 * 应用启动时自动执行 Milvus 新闻增量导入
 * 仅在启用了 Milvus 时生效
 */
@Component
@ConditionalOnBean(MilvusServiceClient::class)
class MilvusImportRunner(
    private val milvusNewsImportService: MilvusNewsImportService,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments?) {
        try {
            log.info("[MilvusImportRunner] 开始执行启动增量导入...")
            val result = milvusNewsImportService.importAllIfNeeded()
            log.info("[MilvusImportRunner] 导入完成: skipped={}, reason={}, totalImported={}",
                result.skipped, result.reason, result.totalImported)
        } catch (e: Exception) {
            log.error("[MilvusImportRunner] 导入失败: {}", e.message, e)
        }
    }
}