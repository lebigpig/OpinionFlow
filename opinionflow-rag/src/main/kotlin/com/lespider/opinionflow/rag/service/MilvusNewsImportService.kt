package com.lespider.opinionflow.rag.service

import com.lespider.opinionflow.rag.domain.FalshNews
import com.lespider.opinionflow.rag.domain.MilvusImportState
import com.lespider.opinionflow.rag.domain.WyNews
import com.lespider.opinionflow.rag.domain.YahooFinanceNews
import com.lespider.opinionflow.rag.repo.FalshNewsRepository
import com.lespider.opinionflow.rag.repo.MilvusImportStateRepository
import com.lespider.opinionflow.rag.repo.WyNewsRepository
import com.lespider.opinionflow.rag.repo.YahooFinanceNewsRepository
import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel
import io.milvus.client.MilvusServiceClient
import io.milvus.common.clientenum.ConsistencyLevelEnum
import io.milvus.grpc.DataType
import io.milvus.grpc.SearchResults
import io.milvus.param.R
import io.milvus.param.collection.CollectionSchemaParam
import io.milvus.param.collection.CreateCollectionParam
import io.milvus.param.collection.FieldType
import io.milvus.param.collection.GetCollectionStatisticsParam
import io.milvus.param.collection.HasCollectionParam
import io.milvus.param.collection.LoadCollectionParam
import io.milvus.param.collection.ReleaseCollectionParam
import io.milvus.param.dml.InsertParam
import io.milvus.param.dml.SearchParam
import io.milvus.param.index.CreateIndexParam
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Milvus 向量数据库新闻增量批量导入 + 检索服务
 */
@Service
@ConditionalOnBean(MilvusServiceClient::class)
class MilvusNewsImportService(
    private val milvusClient: MilvusServiceClient,
    private val wyNewsRepository: WyNewsRepository,
    private val falshNewsRepository: FalshNewsRepository,
    private val yahooFinanceNewsRepository: YahooFinanceNewsRepository,
    private val importStateRepository: MilvusImportStateRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val COLLECTION_NAME = "news_vectors"
        const val VECTOR_DIM = 512
        const val BATCH_SIZE = 500
        val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    private lateinit var embeddingModel: EmbeddingModel

    @PostConstruct
    fun init() {
        embeddingModel = BgeSmallZhV15EmbeddingModel()
        ensureCollection()
        buildIndex()
        log.info("[Milvus] 初始化完成，集合: {}", COLLECTION_NAME)
    }

    // ─── 集合管理 ──────────────────────────────────────────────

    private fun ensureCollection() {
        val has = milvusClient.hasCollection(
            HasCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .build()
        ).getData()

        if (has) {
            log.info("[Milvus] 集合 {} 已存在", COLLECTION_NAME)
            return
        }

        val fields = listOf(
            FieldType.newBuilder()
                .withName("id")
                .withDataType(DataType.Int64)
                .withPrimaryKey(true)
                .withAutoID(true)
                .build(),
            FieldType.newBuilder()
                .withName("source")
                .withDataType(DataType.VarChar)
                .withMaxLength(32)
                .build(),
            FieldType.newBuilder()
                .withName("source_id")
                .withDataType(DataType.VarChar)
                .withMaxLength(64)
                .build(),
            FieldType.newBuilder()
                .withName("title")
                .withDataType(DataType.VarChar)
                .withMaxLength(1024)
                .build(),
            FieldType.newBuilder()
                .withName("content")
                .withDataType(DataType.VarChar)
                .withMaxLength(65535)
                .build(),
            FieldType.newBuilder()
                .withName("publish_time")
                .withDataType(DataType.VarChar)
                .withMaxLength(64)
                .build(),
            FieldType.newBuilder()
                .withName("vector")
                .withDataType(DataType.FloatVector)
                .withDimension(VECTOR_DIM)
                .build(),
        )

        val schema = CollectionSchemaParam.newBuilder()
            .addFieldType(fields[0])
            .addFieldType(fields[1])
            .addFieldType(fields[2])
            .addFieldType(fields[3])
            .addFieldType(fields[4])
            .addFieldType(fields[5])
            .addFieldType(fields[6])
            .build()

        milvusClient.createCollection(
            CreateCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withSchema(schema)
                .withShardsNum(2)
                .withConsistencyLevel(ConsistencyLevelEnum.BOUNDED)
                .build()
        )
        log.info("[Milvus] 集合 {} 创建成功", COLLECTION_NAME)
    }

    private fun buildIndex() {
        try {
            milvusClient.createIndex(
                CreateIndexParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withFieldName("vector")
                    .withIndexType(io.milvus.param.IndexType.IVF_FLAT)
                    .withMetricType(io.milvus.param.MetricType.L2)
                    .withExtraParam("{\"nlist\":128}")
                    .withSyncMode(true)
                    .build()
            )
            log.info("[Milvus] 索引创建成功")
        } catch (e: Exception) {
            log.info("[Milvus] 索引创建跳过（可能已存在）: {}", e.message)
        }
        milvusClient.loadCollection(
            LoadCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .build()
        )
        log.info("[Milvus] 集合已加载到内存，搜索就绪")
    }

    // ─── 向量检索 API（供 AI 服务通过 Feign 调用） ──────────────

    data class RagSearchResult(
        val source: String,
        val sourceId: String,
        val title: String,
        val content: String,
        val publishTime: String,
        val score: Double,
    )

    /**
     * 向量检索：根据查询文本搜索最相关的新闻
     */
    fun search(query: String, topK: Int): List<RagSearchResult> {
        val queryVector = embeddingModel.embed(query).content().vectorAsList()

        val searchParam = SearchParam.newBuilder()
            .withCollectionName(COLLECTION_NAME)
            .withVectorFieldName("vector")
            .withTopK(topK)
            .withMetricType(io.milvus.param.MetricType.L2)
            .withOutFields(listOf("source", "source_id", "title", "content", "publish_time"))
            .withExpr("")
            .withVectors(listOf(queryVector))
            .withParams("{\"nprobe\":16}")
            .build()

        val searchResp: R<SearchResults> = milvusClient.search(searchParam)
        if (searchResp.status != R.success<SearchResults>()!!.status) {
            log.error("[Milvus] 搜索失败: {}", searchResp.message)
            return emptyList()
        }

        val results = mutableListOf<RagSearchResult>()
        val searchResults = searchResp.getData().getResults()
        val scores = searchResults.getScoresList()
        val fieldsData = searchResults.getFieldsDataList()

        // 解析各字段
        val sourceMap = fieldsData.find { it.getFieldName() == "source" }
            ?.getScalars()?.getStringData()?.getDataList() ?: emptyList()
        val sourceIdMap = fieldsData.find { it.getFieldName() == "source_id" }
            ?.getScalars()?.getStringData()?.getDataList() ?: emptyList()
        val titleMap = fieldsData.find { it.getFieldName() == "title" }
            ?.getScalars()?.getStringData()?.getDataList() ?: emptyList()
        val contentMap = fieldsData.find { it.getFieldName() == "content" }
            ?.getScalars()?.getStringData()?.getDataList() ?: emptyList()
        val timeMap = fieldsData.find { it.getFieldName() == "publish_time" }
            ?.getScalars()?.getStringData()?.getDataList() ?: emptyList()

        for (i in scores.indices) {
            results.add(
                RagSearchResult(
                    source = sourceMap.getOrElse(i) { "" },
                    sourceId = sourceIdMap.getOrElse(i) { "" },
                    title = titleMap.getOrElse(i) { "" },
                    content = contentMap.getOrElse(i) { "" },
                    publishTime = timeMap.getOrElse(i) { "" },
                    score = scores[i].toDouble(),
                )
            )
        }
        log.info("[Milvus] 检索完成，查询: '{}', 返回 {} 条结果", query.take(50), results.size)
        return results
    }

    // ─── 导入状态管理 ──────────────────────────────────────────

    private fun getState(sourceName: String): MilvusImportState {
        return importStateRepository.findById(sourceName).orElse(
            MilvusImportState(sourceName = sourceName, lastImportedId = 0L, lastImportedTime = null, updatedAt = null)
        )
    }

    private fun saveState(state: MilvusImportState) {
        state.updatedAt = LocalDateTime.now().format(DATE_FMT)
        importStateRepository.save(state)
    }

    // ─── 增量导入 ──────────────────────────────────────────────

    fun importAllIfNeeded(): ImportResult {
        log.info("[Milvus] 开始增量导入检查...")

        val rowCount = getCollectionRowCount()
        log.info("[Milvus] 集合当前行数: {}", rowCount)
        if (rowCount == 0L) {
            log.warn("[Milvus] 集合为空！重置导入状态，将执行全量导入")
            importStateRepository.deleteAll()
            importStateRepository.flush()
        }

        var totalImported = 0L

        val wyState = getState("wynews")
        log.info("[Milvus] wynews 最后导入 source_id: {}", wyState.lastImportedId)
        val wyCount = importWyNewsIncremental(wyState)
        totalImported += wyCount
        log.info("[Milvus] 网易新闻增量导入完成: {} 条", wyCount)

        val falshState = getState("falsh")
        log.info("[Milvus] falsh 最后导入 source_id: {}", falshState.lastImportedId)
        val falshCount = importFalshNewsIncremental(falshState)
        totalImported += falshCount
        log.info("[Milvus] 实时财经新闻增量导入完成: {} 条", falshCount)

        val yahooState = getState("yahoo")
        log.info("[Milvus] yahoo 最后导入时间: {}", yahooState.lastImportedTime)
        val yahooCount = importYahooNewsIncremental(yahooState)
        totalImported += yahooCount
        log.info("[Milvus] 雅虎新闻增量导入完成: {} 条", yahooCount)

        log.info("[Milvus] 增量导入完成，共 {} 条", totalImported)

        if (totalImported > 0) {
            reloadCollection()
        }

        return ImportResult(skipped = false, totalImported = totalImported)
    }

    private fun reloadCollection() {
        try {
            milvusClient.releaseCollection(
                ReleaseCollectionParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .build()
            )
            milvusClient.loadCollection(
                LoadCollectionParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .build()
            )
            log.info("[Milvus] 集合已重新加载，新数据可搜索")
        } catch (e: Exception) {
            log.warn("[Milvus] 重新加载集合失败: {}", e.message)
        }
    }

    private fun importWyNewsIncremental(state: MilvusImportState): Long {
        var total = 0L
        var page = 0
        var maxId = state.lastImportedId
        while (true) {
            val slice: Page<WyNews> = wyNewsRepository.findByIdAfter(
                state.lastImportedId,
                PageRequest.of(page, BATCH_SIZE)
            )
            if (slice.isEmpty) break

            val texts = slice.content.map { buildText(it.title, it.content) }
            val vectors = batchEmbed(texts)

            val sourceCol = slice.content.map { "wynews" }
            val sourceIdCol = slice.content.map { it.id?.toString() ?: "" }
            val titleCol = slice.content.map { truncate(it.title ?: "", 1024) }
            val contentCol = slice.content.map { truncate(it.content ?: "", 65535) }
            val timeCol = slice.content.map { it.publishTime?.format(DATE_FMT) ?: "" }

            insertBatch(sourceCol, sourceIdCol, titleCol, contentCol, timeCol, vectors)
            total += slice.content.size

            slice.content.forEach { if ((it.id ?: 0) > maxId) maxId = it.id!! }
            log.debug("[Milvus] wynews 增量已导入 {} 条 (page {})", total, page)

            if (!slice.hasNext()) break
            page++
        }

        if (total > 0) {
            state.lastImportedId = maxId
            saveState(state)
        }
        return total
    }

    private fun importFalshNewsIncremental(state: MilvusImportState): Long {
        var total = 0L
        var page = 0
        var maxId = state.lastImportedId
        while (true) {
            val slice: Page<FalshNews> = falshNewsRepository.findByIdAfter(
                state.lastImportedId,
                PageRequest.of(page, BATCH_SIZE)
            )
            if (slice.isEmpty) break

            val texts = slice.content.map { buildText(it.send, it.content) }
            val vectors = batchEmbed(texts)

            val sourceCol = slice.content.map { "falsh" }
            val sourceIdCol = slice.content.map { it.id?.toString() ?: "" }
            val titleCol = slice.content.map { truncate(it.send ?: "", 1024) }
            val contentCol = slice.content.map { truncate(it.content ?: "", 65535) }
            val timeCol = slice.content.map { it.send ?: "" }

            insertBatch(sourceCol, sourceIdCol, titleCol, contentCol, timeCol, vectors)
            total += slice.content.size

            slice.content.forEach { if ((it.id ?: 0) > maxId) maxId = it.id!! }
            log.debug("[Milvus] falsh 增量已导入 {} 条 (page {})", total, page)

            if (!slice.hasNext()) break
            page++
        }

        if (total > 0) {
            state.lastImportedId = maxId
            saveState(state)
        }
        return total
    }

    private fun importYahooNewsIncremental(state: MilvusImportState): Long {
        var total = 0L
        var page = 0
        val afterTime = if (!state.lastImportedTime.isNullOrBlank()) {
            try { LocalDateTime.parse(state.lastImportedTime, DATE_FMT) } catch (_: Exception) { null }
        } else null
        var maxTime = afterTime

        while (true) {
            val slice: Page<YahooFinanceNews> = if (afterTime != null) {
                yahooFinanceNewsRepository.findByFetchedAtAfter(
                    afterTime,
                    PageRequest.of(page, BATCH_SIZE)
                )
            } else {
                yahooFinanceNewsRepository.findAll(
                    PageRequest.of(page, BATCH_SIZE, Sort.by(Sort.Direction.ASC, "fetchedAt"))
                )
            }
            if (slice.isEmpty) break

            val texts = slice.content.map { buildText(it.title, it.summary) }
            val vectors = batchEmbed(texts)

            val sourceCol = slice.content.map { "yahoo" }
            val sourceIdCol = slice.content.map { it.id ?: "" }
            val titleCol = slice.content.map { truncate(it.title ?: "", 1024) }
            val contentCol = slice.content.map { truncate(it.summary ?: "", 65535) }
            val timeCol = slice.content.map { it.displayTime ?: it.fetchedAt?.format(DATE_FMT) ?: "" }

            insertBatch(sourceCol, sourceIdCol, titleCol, contentCol, timeCol, vectors)
            total += slice.content.size

            slice.content.forEach {
                val t = it.fetchedAt
                if (t != null && (maxTime == null || t.isAfter(maxTime))) maxTime = t
            }
            log.debug("[Milvus] yahoo 增量已导入 {} 条 (page {})", total, page)

            if (!slice.hasNext()) break
            page++
        }

        if (total > 0) {
            state.lastImportedTime = maxTime?.format(DATE_FMT) ?: state.lastImportedTime
            saveState(state)
        }
        return total
    }

    private fun getCollectionRowCount(): Long {
        try {
            val resp = milvusClient.getCollectionStatistics(
                GetCollectionStatisticsParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .build()
            )
            if (resp.getStatus() == 0) {
                val data = resp.getData()
                if (data != null) {
                    for (kv in data.getStatsList()) {
                        if (kv.getKey() == "row_count") {
                            val count = kv.getValue().toLongOrNull() ?: 0L
                            log.info("[Milvus] getCollectionStatistics 返回 row_count: {}", count)
                            return count
                        }
                    }
                }
            } else {
                log.warn("[Milvus] getCollectionStatistics 失败: {}", resp.getMessage())
            }
        } catch (e: Exception) {
            log.warn("[Milvus] 获取集合行数失败: {}", e.message)
        }
        return 0L
    }

    // ─── 向量化 ──────────────────────────────────────────────

    private fun batchEmbed(texts: List<String>): List<List<Float>> {
        val segments: List<TextSegment> = texts.map { t -> TextSegment.from(t) }
        val embeddings: List<Embedding> = embeddingModel.embedAll(segments).content()
        return embeddings.map { it.vectorAsList() }
    }

    // ─── Milvus 写入 ──────────────────────────────────────────

    private fun insertBatch(
        source: List<String>,
        sourceId: List<String>,
        title: List<String>,
        content: List<String>,
        publishTime: List<String>,
        vectors: List<List<Float>>,
    ) {
        val fields = listOf(
            InsertParam.Field("source", source),
            InsertParam.Field("source_id", sourceId),
            InsertParam.Field("title", title),
            InsertParam.Field("content", content),
            InsertParam.Field("publish_time", publishTime),
            InsertParam.Field("vector", vectors),
        )
        val resp = milvusClient.insert(
            InsertParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFields(fields)
                .build()
        )
        if (resp.status != R.success<InsertParam>()!!.status) {
            log.error("[Milvus] 插入失败: {}", resp.message)
            throw RuntimeException("Milvus 插入失败: ${resp.message}")
        }
    }

    private fun truncate(s: String, maxLen: Int, maxBytes: Int = maxLen): String {
        if (s.isEmpty()) return s
        val sb = StringBuilder()
        var byteCount = 0
        for (ch in s) {
            val charBytes = when {
                ch.code <= 0x7F -> 1
                ch.code <= 0x7FF -> 2
                else -> 3
            }
            if (sb.length + 1 > maxLen || byteCount + charBytes > maxBytes) break
            sb.append(ch)
            byteCount += charBytes
        }
        return sb.toString()
    }

    private fun buildText(title: String?, content: String?): String {
        val t = (title ?: "").trim()
        val c = (content ?: "").trim()
        return when {
            t.isEmpty() && c.isEmpty() -> "空内容"
            c.isEmpty() -> t
            t.isEmpty() -> c.take(500)
            else -> "$t\n${c.take(500)}"
        }
    }

    data class ImportResult(
        val skipped: Boolean,
        val reason: String? = null,
        val totalImported: Long = 0,
    )
}