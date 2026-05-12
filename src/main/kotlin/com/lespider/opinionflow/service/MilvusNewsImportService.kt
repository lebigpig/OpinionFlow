package com.lespider.opinionflow.service

import com.lespider.opinionflow.domain.FalshNews
import com.lespider.opinionflow.domain.MilvusImportState
import com.lespider.opinionflow.domain.WyNews
import com.lespider.opinionflow.domain.YahooFinanceNews
import com.lespider.opinionflow.repo.FalshNewsRepository
import com.lespider.opinionflow.repo.MilvusImportStateRepository
import com.lespider.opinionflow.repo.WyNewsRepository
import com.lespider.opinionflow.repo.YahooFinanceNewsRepository
import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel
import io.milvus.client.MilvusServiceClient
import io.milvus.common.clientenum.ConsistencyLevelEnum
import io.milvus.grpc.DataType
import io.milvus.param.R
import io.milvus.param.collection.CreateCollectionParam
import io.milvus.param.collection.FieldType
import io.milvus.param.collection.HasCollectionParam
import io.milvus.param.collection.LoadCollectionParam
import io.milvus.param.dml.InsertParam
import io.milvus.param.index.CreateIndexParam
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Milvus 向量数据库新闻增量批量导入服务
 *
 * 功能：
 * 1. 应用启动时自动将网易新闻、实时财经新闻、雅虎新闻增量导入 Milvus
 * 2. 通过 MySQL milvus_import_state 表跟踪每个数据源最后导入的位置
 * 3. 网易新闻 / 实时财经新闻：通过自增 id > lastImportedId 实现增量
 * 4. 雅虎新闻：通过 fetched_at > lastImportedTime 实现增量
 * 5. 每 500 条批量写入
 * 6. 使用 AllMiniLmL6V2 本地模型进行文本向量化
 */
@Service
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
        const val VECTOR_DIM = 384  // AllMiniLmL6V2 输出维度
        const val BATCH_SIZE = 500
        val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    private lateinit var embeddingModel: EmbeddingModel

    @PostConstruct
    fun init() {
        embeddingModel = AllMiniLmL6V2QuantizedEmbeddingModel()
        ensureCollection()
        buildIndex()
        log.info("[Milvus] 初始化完成，集合: {}", COLLECTION_NAME)
    }

    // ─── 集合管理 ──────────────────────────────────────────────

    /** 确保集合存在，不存在则创建 */
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

        milvusClient.createCollection(
            CreateCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .withFieldTypes(fields)
                .withShardsNum(2)
                .withConsistencyLevel(ConsistencyLevelEnum.BOUNDED)
                .build()
        )
        log.info("[Milvus] 集合 {} 创建成功", COLLECTION_NAME)
    }

    /** 为向量字段创建索引 */
    private fun buildIndex() {
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
        milvusClient.loadCollection(
            LoadCollectionParam.newBuilder()
                .withCollectionName(COLLECTION_NAME)
                .build()
        )
        log.info("[Milvus] 索引创建完成，集合已加载到内存")
    }

    // ─── 导入状态管理（MySQL） ──────────────────────────────────

    /** 获取或创建导入状态记录 */
    private fun getState(sourceName: String): MilvusImportState {
        return importStateRepository.findById(sourceName).orElse(
            MilvusImportState(sourceName = sourceName, lastImportedId = 0L, lastImportedTime = null, updatedAt = null)
        )
    }

    /** 保存导入状态 */
    private fun saveState(state: MilvusImportState) {
        state.updatedAt = LocalDateTime.now().format(DATE_FMT)
        importStateRepository.save(state)
    }

    // ─── 增量导入 ──────────────────────────────────────────────

    /**
     * 执行增量导入（启动时调用）
     * - 网易新闻 / 实时财经新闻：通过自增 id > lastImportedId 增量
     * - 雅虎新闻：通过 fetched_at > lastImportedTime 增量
     */
    fun importAllIfNeeded(): ImportResult {
        log.info("[Milvus] 开始增量导入检查...")
        var totalImported = 0L

        // 1. 增量导入网易新闻（by id）
        val wyState = getState("wynews")
        log.info("[Milvus] wynews 最后导入 source_id: {}", wyState.lastImportedId)
        val wyCount = importWyNewsIncremental(wyState)
        totalImported += wyCount
        log.info("[Milvus] 网易新闻增量导入完成: {} 条", wyCount)

        // 2. 增量导入实时财经新闻（by id）
        val falshState = getState("falsh")
        log.info("[Milvus] falsh 最后导入 source_id: {}", falshState.lastImportedId)
        val falshCount = importFalshNewsIncremental(falshState)
        totalImported += falshCount
        log.info("[Milvus] 实时财经新闻增量导入完成: {} 条", falshCount)

        // 3. 增量导入雅虎新闻（by fetched_at）
        val yahooState = getState("yahoo")
        log.info("[Milvus] yahoo 最后导入时间: {}", yahooState.lastImportedTime)
        val yahooCount = importYahooNewsIncremental(yahooState)
        totalImported += yahooCount
        log.info("[Milvus] 雅虎新闻增量导入完成: {} 条", yahooCount)

        log.info("[Milvus] 增量导入完成，共 {} 条", totalImported)
        return ImportResult(skipped = false, totalImported = totalImported)
    }

    /** 增量导入网易新闻：id > lastImportedId */
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

            // 更新最大 ID
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

    /** 增量导入实时财经新闻：id > lastImportedId */
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

    /** 增量导入雅虎新闻：fetched_at > lastImportedTime */
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

            // 更新最大时间
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

    /**
     * 按字符数截断字符串，同时确保 UTF-8 字节长度不超过 Milvus VARCHAR 字段的字节限制。
     * Milvus 的 maxLength 以字节为单位，中文字符 UTF-8 占 3 字节。
     */
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