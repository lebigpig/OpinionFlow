package com.lespider.opinionflow.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.lespider.opinionflow.web.dto.SaveEchartRequest
import com.lespider.opinionflow.web.dto.SaveEchartResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/echart")
class EchartController(
    private val objectMapper: ObjectMapper,
) {
    private val dtf = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    @PostMapping("/save")
    fun save(@RequestBody body: SaveEchartRequest): SaveEchartResponse {
        val jsonText = body.jsonText?.trim().orEmpty()
        if (jsonText.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "jsonText 不能为空")
        }

        // 验证 JSON 合法性（同时允许前端直接传任意 JSON）
        try {
            objectMapper.readTree(jsonText)
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "jsonText 不是合法 JSON：${e.message}", e)
        }

        val safeName = sanitizeFilename(body.filename)
        val filename = safeName ?: "echart-${LocalDateTime.now().format(dtf)}.json"

        val targetDir = Path.of("..", "opinionflow-vue", "public", "echart").normalize()
        Files.createDirectories(targetDir)

        val file = targetDir.resolve(filename).normalize()
        if (!file.startsWith(targetDir)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "filename 非法")
        }

        Files.writeString(file, jsonText)

        return SaveEchartResponse(
            savedPath = file.toAbsolutePath().toString(),
            filename = filename,
        )
    }

    @GetMapping("/list")
    fun list(): List<Map<String, String>> {
        val targetDir = Path.of("..", "opinionflow-vue", "public", "echart").normalize()
        if (!Files.isDirectory(targetDir)) return emptyList()

        return Files.list(targetDir)
            .filter { it.toString().endsWith(".json") }
            .map { path ->
                mapOf(
                    "filename" to path.fileName.toString(),
                    "path" to "/echart/${path.fileName}",
                )
            }
            .sorted(compareByDescending { it["filename"] ?: "" })
            .toList()
    }

    @GetMapping("/read/{filename}")
    fun read(@PathVariable filename: String): Map<String, String> {
        val safeName = sanitizeFilename(filename)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "filename 非法")

        val targetDir = Path.of("..", "opinionflow-vue", "public", "echart").normalize()
        val file = targetDir.resolve(safeName).normalize()
        if (!file.startsWith(targetDir)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "filename 非法")
        }
        if (!Files.exists(file)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在")
        }

        val content = Files.readString(file)
        return mapOf("filename" to safeName, "content" to content)
    }

    private fun sanitizeFilename(raw: String?): String? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null

        // 仅保留文件名本身，避免路径注入
        val normalized = s.replace('\\', '/').substringAfterLast('/').trim()
        if (normalized.isEmpty()) return null
        if (!normalized.lowercase().endsWith(".json")) return null

        // Windows 禁用字符 + 控制字符；不允许以空格/点结尾
        if (Regex("""[<>:"/\\|?*\x00-\x1F]""").containsMatchIn(normalized)) return null
        if (Regex("""[. ]$""").containsMatchIn(normalized)) return null
        if (normalized.length > 120) return null

        // 额外防御：拒绝 .. 这种可疑片段（虽然后面还有 startsWith 校验）
        if (normalized.contains("..")) return null

        return normalized
    }
}

