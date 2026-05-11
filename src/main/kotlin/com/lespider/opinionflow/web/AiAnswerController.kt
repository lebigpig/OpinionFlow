package com.lespider.opinionflow.web

import com.lespider.opinionflow.web.dto.AiAnswerFileInfo
import com.lespider.opinionflow.web.dto.AiAnswerListResponse
import com.lespider.opinionflow.web.dto.SaveAiAnswerRequest
import com.lespider.opinionflow.web.dto.SaveAiAnswerResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.stream.Collectors
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/ai-answer")
class AiAnswerController {
    private val dtf = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    private val targetDir: Path = Path.of("..", "opinionflow-vue", "src", "AI_answer").normalize()

    @PostMapping("/save")
    fun save(@RequestBody body: SaveAiAnswerRequest): SaveAiAnswerResponse {
        val content = body.content?.trim().orEmpty()
        if (content.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "content 不能为空")
        }

        val safeName = sanitizeFilename(body.filename)
        val filename = safeName ?: "ai-answer-${LocalDateTime.now().format(dtf)}.txt"

        Files.createDirectories(targetDir)

        val file = targetDir.resolve(filename).normalize()
        if (!file.startsWith(targetDir)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "filename 非法")
        }

        Files.writeString(file, content)

        return SaveAiAnswerResponse(
            savedPath = file.toAbsolutePath().toString(),
            filename = filename,
        )
    }

    @GetMapping("/list")
    fun list(): AiAnswerListResponse {
        if (!Files.exists(targetDir)) {
            return AiAnswerListResponse(files = emptyList())
        }

        val files = Files.list(targetDir).use { stream ->
            stream
                .filter { p -> p.fileName.toString().lowercase().endsWith(".txt") }
                .sorted { a, b -> b.fileName.toString().compareTo(a.fileName.toString()) }
                .map { p ->
                    val fn = p.fileName.toString()
                    val ts = try {
                        val lastModified = Files.getLastModifiedTime(p)
                        lastModified.toInstant().toString()
                    } catch (_: Exception) {
                        ""
                    }
                    AiAnswerFileInfo(
                        filename = fn,
                        savedPath = p.toAbsolutePath().toString(),
                        timestamp = ts,
                    )
                }
                .collect(Collectors.toList())
        }

        return AiAnswerListResponse(files = files)
    }

    @GetMapping("/read/{filename:.+}")
    fun read(@PathVariable filename: String): String {
        val safeName = sanitizeFilename(filename) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "filename 非法")
        val file = targetDir.resolve(safeName).normalize()
        if (!file.startsWith(targetDir) || !Files.exists(file)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在")
        }
        return Files.readString(file)
    }

    private fun sanitizeFilename(raw: String?): String? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null

        // 仅保留文件名本身，避免路径注入
        val normalized = s.replace('\\', '/').substringAfterLast('/').trim()
        if (normalized.isEmpty()) return null
        if (!normalized.lowercase().endsWith(".txt")) return null

        // Windows 禁用字符 + 控制字符；不允许以空格/点结尾
        if (Regex("""[<>:"/\\|?*\x00-\x1F]""").containsMatchIn(normalized)) return null
        if (Regex("""[. ]$""").containsMatchIn(normalized)) return null
        if (normalized.length > 120) return null

        // 额外防御：拒绝 .. 这种可疑片段
        if (normalized.contains("..")) return null

        return normalized
    }
}
