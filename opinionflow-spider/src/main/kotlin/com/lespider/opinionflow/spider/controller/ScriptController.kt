package com.lespider.opinionflow.spider.controller

import com.lespider.opinionflow.spider.dto.ScriptRunAllRequest
import com.lespider.opinionflow.spider.dto.ScriptRunAllResponse
import com.lespider.opinionflow.spider.dto.ScriptRunRequest
import com.lespider.opinionflow.spider.dto.ScriptRunResponse
import com.lespider.opinionflow.spider.service.ScriptRunnerService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/scripts")
class ScriptController(
    private val scriptRunnerService: ScriptRunnerService,
) {

    @PostMapping("/run")
    fun runScript(@RequestBody req: ScriptRunRequest): ScriptRunResponse {
        val key = req.key?.trim()
        if (key.isNullOrEmpty()) {
            return ScriptRunResponse(ok = false, key = "", message = "缺少 key")
        }
        return scriptRunnerService.run(key, req.code)
    }

    @PostMapping("/run-stream", "/run/stream")
    fun runStream(@RequestBody req: ScriptRunRequest): SseEmitter {
        val key = req.key?.trim()
        if (key.isNullOrEmpty()) {
            val emitter = SseEmitter(0L)
            emitter.send(
                SseEmitter.event()
                    .name("error")
                    .data("{\"ok\":false,\"message\":\"缺少 key\"}")
            )
            emitter.complete()
            return emitter
        }

        val emitter = SseEmitter(0L)
        Thread({
            try {
                scriptRunnerService.runStream(
                    key = key,
                    code = req.code,
                    onMeta = { line ->
                        emitter.send(SseEmitter.event().name("meta").data(line))
                    },
                    onStdout = { line ->
                        emitter.send(SseEmitter.event().name("stdout").data(line))
                    },
                    onStderr = { line ->
                        emitter.send(SseEmitter.event().name("stderr").data(line))
                    },
                )
                emitter.send(
                    SseEmitter.event()
                        .name("done")
                        .data("{\"ok\":true}")
                )
                emitter.complete()
            } catch (ex: Exception) {
                try {
                    emitter.send(
                        SseEmitter.event()
                            .name("error")
                            .data("{\"ok\":false,\"message\":\"${ex.message}\"}")
                    )
                } catch (_: Exception) {}
                emitter.completeWithError(ex)
            }
        }, "script-run-stream").start()

        emitter.onTimeout { emitter.complete() }
        emitter.onError { _ -> }
        return emitter
    }

    @PostMapping("/run-all")
    fun runAll(@RequestBody req: ScriptRunAllRequest): ScriptRunAllResponse {
        val t0 = System.currentTimeMillis()
        val keys = listOf("comments", "news", "realtime")
        val results = mutableMapOf<String, ScriptRunResponse>()
        for (key in keys) {
            results[key] = scriptRunnerService.run(key, req.code)
        }
        val allOk = results.values.all { it.ok }
        return ScriptRunAllResponse(
            ok = allOk,
            durationMs = System.currentTimeMillis() - t0,
            results = results,
            message = if (allOk) "全部脚本运行成功" else "部分脚本运行失败",
        )
    }
}