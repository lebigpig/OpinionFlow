package com.lespider.opinionflow.web

import com.lespider.opinionflow.service.ScriptRunnerService
import com.lespider.opinionflow.web.dto.ScriptRunAllRequest
import com.lespider.opinionflow.web.dto.ScriptRunAllResponse
import com.lespider.opinionflow.web.dto.ScriptRunRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.springframework.http.MediaType
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/scripts")
class ScriptController(
    private val scriptRunnerService: ScriptRunnerService,
) {
    @PostMapping("/run")
    fun run(@RequestBody body: ScriptRunRequest) : Any {
        val key = body.key?.trim().orEmpty()
        if (key.isEmpty()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "key 不能为空")
        if (key !in setOf("comments", "news", "realtime")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的 key：$key")
        }
        val codeRaw = body.code?.trim()
        val code = if (key == "comments") {
            if (codeRaw.isNullOrBlank()) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "code 不能为空（评论爬取需要股票编号）")
            }
            val digits = codeRaw.filter { it.isDigit() }
            if (digits.isEmpty()) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "code 格式不正确：$codeRaw")
            }
            // A 股常见 6 位；不足左侧补 0，过长取最后 6 位
            val normalized = digits.takeLast(6).padStart(6, '0')
            normalized
        } else null

        return scriptRunnerService.run(key, code)
    }

    @PostMapping("/run/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun runStream(@RequestBody body: ScriptRunRequest): SseEmitter {
        val key = body.key?.trim().orEmpty()
        if (key.isEmpty()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "key 不能为空")
        if (key !in setOf("comments", "news", "realtime")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的 key：$key")
        }

        val codeRaw = body.code?.trim()
        val code = if (key == "comments") {
            if (codeRaw.isNullOrBlank()) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "code 不能为空（评论爬取需要股票编号）")
            }
            val digits = codeRaw.filter { it.isDigit() }
            if (digits.isEmpty()) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "code 格式不正确：$codeRaw")
            }
            digits.takeLast(6).padStart(6, '0')
        } else null

        val emitter = SseEmitter(0L)
        val executor = Executors.newSingleThreadExecutor()
        val lock = Any()
        val clientGone = AtomicBoolean(false)

        fun safeSend(event: String, data: Any) {
            if (clientGone.get()) return
            try {
                synchronized(lock) {
                    emitter.send(SseEmitter.event().name(event).data(data))
                }
            } catch (_: Exception) {
                // 客户端断开/刷新/网络中断时会抛 IOException/IllegalStateException
                clientGone.set(true)
                try {
                    emitter.complete()
                } catch (_: Exception) {
                }
            }
        }

        emitter.onCompletion { clientGone.set(true) }
        emitter.onTimeout {
            clientGone.set(true)
            try { emitter.complete() } catch (_: Exception) {}
        }
        emitter.onError {
            clientGone.set(true)
            try { emitter.complete() } catch (_: Exception) {}
        }

        executor.submit {
            try {
                val resp = scriptRunnerService.runStream(
                    key = key,
                    code = code,
                    onMeta = { text ->
                        safeSend("meta", text)
                    },
                    onStdout = { line ->
                        safeSend("stdout", line)
                    },
                    onStderr = { line ->
                        safeSend("stderr", line)
                    },
                )

                safeSend("done", resp)
                if (!clientGone.get()) emitter.complete()
            } catch (e: Exception) {
                safeSend("error", (e.message ?: "error"))
                try { emitter.complete() } catch (_: Exception) {}
            } finally {
                executor.shutdown()
            }
        }

        return emitter
    }

    /**
     * 同时运行 comments/news/realtime 三个 key（并行），最后返回汇总。
     * - comments 需要 code，否则会 400
     */
    @PostMapping("/run-all")
    fun runAll(@RequestBody body: ScriptRunAllRequest): ScriptRunAllResponse {
        val t0 = System.currentTimeMillis()
        val codeRaw = body.code?.trim()
        if (codeRaw.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "code 不能为空（并行运行包含评论爬取）")
        }
        val digits = codeRaw.filter { it.isDigit() }
        if (digits.isEmpty()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "code 格式不正确：$codeRaw")
        val code = digits.takeLast(6).padStart(6, '0')

        val keys = listOf("comments", "news", "realtime")
        val results = ConcurrentHashMap<String, com.lespider.opinionflow.web.dto.ScriptRunResponse>()
        val pool = Executors.newFixedThreadPool(keys.size)
        val latch = CountDownLatch(keys.size)

        try {
            for (k in keys) {
                pool.submit {
                    try {
                        val r = scriptRunnerService.run(k, if (k == "comments") code else null)
                        results[k] = r
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await()
        } finally {
            pool.shutdown()
        }

        val duration = System.currentTimeMillis() - t0
        val ok = keys.all { results[it]?.ok == true }
        val msg = if (ok) null else "部分脚本运行失败，请查看 results 中的 message/exitCode"
        return ScriptRunAllResponse(ok = ok, durationMs = duration, results = results, message = msg)
    }

    /**
     * 同时运行 comments/news/realtime 三个 key（并行），并通过 SSE 实时推送输出。
     * 事件：
     * - meta/stdout/stderr：data 会带前缀 [key]
     * - done_key：每个 key 完成时发送一次 JSON（ScriptRunResponse）
     * - done_all：全部完成时发送汇总 JSON（ScriptRunAllResponse）
     */
    @PostMapping("/run-all/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun runAllStream(@RequestBody body: ScriptRunAllRequest): SseEmitter {
        val t0 = System.currentTimeMillis()
        val codeRaw = body.code?.trim()
        if (codeRaw.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "code 不能为空（并行运行包含评论爬取）")
        }
        val digits = codeRaw.filter { it.isDigit() }
        if (digits.isEmpty()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "code 格式不正确：$codeRaw")
        val code = digits.takeLast(6).padStart(6, '0')

        val emitter = SseEmitter(0L)
        val lock = Any()
        val clientGone = AtomicBoolean(false)
        val keys = listOf("comments", "news", "realtime")
        val results = ConcurrentHashMap<String, com.lespider.opinionflow.web.dto.ScriptRunResponse>()
        val latch = CountDownLatch(keys.size)
        val pool = Executors.newFixedThreadPool(keys.size)

        fun send(event: String, data: Any) {
            if (clientGone.get()) return
            try {
                synchronized(lock) {
                    emitter.send(SseEmitter.event().name(event).data(data))
                }
            } catch (_: Exception) {
                clientGone.set(true)
                try { emitter.complete() } catch (_: Exception) {}
            }
        }

        emitter.onCompletion { clientGone.set(true) }
        emitter.onTimeout {
            clientGone.set(true)
            try { emitter.complete() } catch (_: Exception) {}
        }
        emitter.onError {
            clientGone.set(true)
            try { emitter.complete() } catch (_: Exception) {}
        }

        send("meta", "START run-all: ${keys.joinToString(",")}")

        for (k in keys) {
            pool.submit {
                try {
                    val r = scriptRunnerService.runStream(
                        key = k,
                        code = if (k == "comments") code else null,
                        onMeta = { line -> send("meta", "[$k] $line") },
                        onStdout = { line -> send("stdout", "[$k] $line") },
                        onStderr = { line -> send("stderr", "[$k] $line") },
                    )
                    results[k] = r
                    send("done_$k", r)
                } catch (e: Exception) {
                    send("stderr", "[$k] ERROR: ${e.message ?: "error"}")
                } finally {
                    latch.countDown()
                }
            }
        }

        val finisher = Executors.newSingleThreadExecutor()
        finisher.submit {
            try {
                latch.await()
                val duration = System.currentTimeMillis() - t0
                val ok = keys.all { results[it]?.ok == true }
                val msg = if (ok) null else "部分脚本运行失败，请查看 results 中的 message/exitCode"
                val all = ScriptRunAllResponse(ok = ok, durationMs = duration, results = results, message = msg)
                send("done_all", all)
                if (!clientGone.get()) emitter.complete()
            } catch (e: Exception) {
                try {
                    send("error", e.message ?: "error")
                } catch (_: Exception) {
                }
                try { emitter.complete() } catch (_: Exception) {}
            } finally {
                pool.shutdown()
                finisher.shutdown()
            }
        }

        return emitter
    }
}

