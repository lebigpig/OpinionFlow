package com.lespider.opinionflow.spider.service

import com.lespider.opinionflow.spider.dto.ScriptRunResponse
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.math.min
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class ScriptRunnerService(
    @Value("\${opinionflow.scripts.enabled:false}")
    private val enabled: Boolean,
    @Value("\${opinionflow.scripts.python:python}")
    private val pythonExe: String,
    @Value("\${opinionflow.scripts.comment-path:}")
    private val commentPath: String,
    @Value("\${opinionflow.scripts.news-path:}")
    private val newsPath: String,
    @Value("\${opinionflow.scripts.realtime-path:}")
    private val realtimePath: String,
) {
    private fun normalizeScriptPath(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty()) return s

        if (File(s).exists()) return s

        val recovered = try {
            String(s.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
        } catch (_: Exception) {
            s
        }
        if (recovered != s && File(recovered).exists()) return recovered

        val slashed = s.replace('\\', '/')
        if (slashed != s && File(slashed).exists()) return slashed

        val recoveredSlashed = recovered.replace('\\', '/')
        if (recoveredSlashed != recovered && File(recoveredSlashed).exists()) return recoveredSlashed

        return s
    }

    fun run(key: String, code: String? = null): ScriptRunResponse {
        val t0 = System.currentTimeMillis()
        if (!enabled) {
            return ScriptRunResponse(
                ok = false,
                key = key,
                durationMs = System.currentTimeMillis() - t0,
                message = "脚本运行已禁用：opinionflow.scripts.enabled=false",
            )
        }

        val scriptSpec = when (key) {
            "comments" -> commentPath
            "news" -> newsPath
            "realtime" -> realtimePath
            else -> ""
        }.trim()

        if (scriptSpec.isEmpty()) {
            return ScriptRunResponse(
                ok = false,
                key = key,
                durationMs = System.currentTimeMillis() - t0,
                message = "未配置脚本路径：key=$key",
            )
        }

        val scripts = scriptSpec
            .split(',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .map { normalizeScriptPath(it) }

        if (scripts.isEmpty()) {
            return ScriptRunResponse(
                ok = false,
                key = key,
                durationMs = System.currentTimeMillis() - t0,
                message = "未配置脚本路径：key=$key",
            )
        }

        val stdoutAll = StringBuilder()
        val stderrAll = StringBuilder()
        var lastExit: Int? = null
        var message: String? = null

        for ((idx, script) in scripts.withIndex()) {
            val file = File(script)
            if (!file.exists() || !file.isFile) {
                message = "脚本文件不存在：$script"
                return ScriptRunResponse(
                    ok = false,
                    key = key,
                    exitCode = lastExit,
                    durationMs = System.currentTimeMillis() - t0,
                    stdout = stdoutAll.toString(),
                    stderr = stderrAll.toString(),
                    message = message,
                )
            }

            stdoutAll.appendLine("===== [${idx + 1}/${scripts.size}] START: ${file.absolutePath} =====")
            stderrAll.appendLine("===== [${idx + 1}/${scripts.size}] START: ${file.absolutePath} =====")

            val cmd = mutableListOf(pythonExe, file.absolutePath)
            if (key == "comments" && !code.isNullOrBlank()) {
                cmd.add("--code")
                cmd.add(code)
            }
            stdoutAll.appendLine("CMD: ${cmd.joinToString(" ")}")

            val pb = ProcessBuilder(cmd)
            pb.redirectErrorStream(false)

            val proc = try {
                pb.start()
            } catch (e: Exception) {
                message = "启动脚本失败：${e.message}"
                return ScriptRunResponse(
                    ok = false,
                    key = key,
                    exitCode = lastExit,
                    durationMs = System.currentTimeMillis() - t0,
                    stdout = stdoutAll.toString(),
                    stderr = stderrAll.toString(),
                    message = message,
                )
            }

            val charset = Charset.forName("UTF-8")
            val maxChars = 200_000

            val outThread = StreamCollector(proc.inputStream, charset, maxChars)
            val errThread = StreamCollector(proc.errorStream, charset, maxChars)
            outThread.start()
            errThread.start()

            val timeout = Duration.ofMinutes(20).toMillis()
            val finished = proc.waitFor(timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                proc.destroyForcibly()
            }

            outThread.join(2000)
            errThread.join(2000)

            val exit = if (finished) proc.exitValue() else null
            lastExit = exit

            stdoutAll.appendLine(outThread.result)
            stderrAll.appendLine(errThread.result)

            stdoutAll.appendLine("===== [${idx + 1}/${scripts.size}] END: exit=$exit finished=$finished =====")
            stderrAll.appendLine("===== [${idx + 1}/${scripts.size}] END: exit=$exit finished=$finished =====")

            if (!finished) {
                message = "脚本运行超时（已强制结束）：${timeout}ms"
                break
            }
            if (exit != 0) {
                message = "脚本退出码非 0：$exit"
                break
            }
        }

        val duration = System.currentTimeMillis() - t0
        val ok = message == null

        return ScriptRunResponse(
            ok = ok,
            key = key,
            exitCode = lastExit,
            durationMs = duration,
            stdout = stdoutAll.toString(),
            stderr = stderrAll.toString(),
            message = message,
        )
    }

    fun runStream(
        key: String,
        code: String? = null,
        onMeta: (String) -> Unit = {},
        onStdout: (String) -> Unit = {},
        onStderr: (String) -> Unit = {},
    ): ScriptRunResponse {
        val t0 = System.currentTimeMillis()
        if (!enabled) {
            return ScriptRunResponse(
                ok = false,
                key = key,
                durationMs = System.currentTimeMillis() - t0,
                message = "脚本运行已禁用：opinionflow.scripts.enabled=false",
            )
        }

        val scriptSpec = when (key) {
            "comments" -> commentPath
            "news" -> newsPath
            "realtime" -> realtimePath
            else -> ""
        }.trim()

        if (scriptSpec.isEmpty()) {
            return ScriptRunResponse(
                ok = false,
                key = key,
                durationMs = System.currentTimeMillis() - t0,
                message = "未配置脚本路径：key=$key",
            )
        }

        val scripts = scriptSpec
            .split(',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .map { normalizeScriptPath(it) }

        if (scripts.isEmpty()) {
            return ScriptRunResponse(
                ok = false,
                key = key,
                durationMs = System.currentTimeMillis() - t0,
                message = "未配置脚本路径：key=$key",
            )
        }

        val charset = Charset.forName("UTF-8")
        val maxChars = 200_000
        val stdoutAll = StringBuilder()
        val stderrAll = StringBuilder()
        var lastExit: Int? = null
        var message: String? = null

        fun appendLimited(sb: StringBuilder, s: String) {
            sb.appendLine(s)
            if (sb.length > maxChars) {
                val keep = sb.substring(sb.length - maxChars)
                sb.setLength(0)
                sb.append(keep)
            }
        }

        for ((idx, script) in scripts.withIndex()) {
            val file = File(script)
            if (!file.exists() || !file.isFile) {
                message = "脚本文件不存在：$script"
                break
            }

            val startLine = "===== [${idx + 1}/${scripts.size}] START: ${file.absolutePath} ====="
            onMeta(startLine)
            appendLimited(stdoutAll, startLine)
            appendLimited(stderrAll, startLine)

            val cmd = mutableListOf(pythonExe, file.absolutePath)
            if (key == "comments" && !code.isNullOrBlank()) {
                cmd.add("--code")
                cmd.add(code)
            }
            val cmdLine = "CMD: ${cmd.joinToString(" ")}"
            onMeta(cmdLine)
            appendLimited(stdoutAll, cmdLine)

            val pb = ProcessBuilder(cmd)
            pb.redirectErrorStream(false)

            val proc = try {
                pb.start()
            } catch (e: Exception) {
                message = "启动脚本失败：${e.message}"
                break
            }

            val outPumper = StreamPumper(proc.inputStream, charset) { line ->
                val s = line.trimEnd('\r')
                if (s.isNotEmpty()) {
                    onStdout(s)
                    appendLimited(stdoutAll, s)
                }
            }
            val errPumper = StreamPumper(proc.errorStream, charset) { line ->
                val s = line.trimEnd('\r')
                if (s.isNotEmpty()) {
                    onStderr(s)
                    appendLimited(stderrAll, s)
                }
            }
            outPumper.start()
            errPumper.start()

            val timeout = Duration.ofMinutes(20).toMillis()
            val finished = proc.waitFor(timeout, TimeUnit.MILLISECONDS)
            if (!finished) {
                proc.destroyForcibly()
            }

            outPumper.join(2000)
            errPumper.join(2000)

            val exit = if (finished) proc.exitValue() else null
            lastExit = exit

            val endLine = "===== [${idx + 1}/${scripts.size}] END: exit=$exit finished=$finished ====="
            onMeta(endLine)
            appendLimited(stdoutAll, endLine)
            appendLimited(stderrAll, endLine)

            if (!finished) {
                message = "脚本运行超时（已强制结束）：${timeout}ms"
                break
            }
            if (exit != 0) {
                message = "脚本退出码非 0：$exit"
                break
            }
        }

        val duration = System.currentTimeMillis() - t0
        val ok = message == null
        return ScriptRunResponse(
            ok = ok,
            key = key,
            exitCode = lastExit,
            durationMs = duration,
            stdout = stdoutAll.toString(),
            stderr = stderrAll.toString(),
            message = message,
        )
    }

    private class StreamCollector(
        private val input: java.io.InputStream,
        private val charset: Charset,
        private val maxChars: Int,
    ) : Thread("script-stream-collector") {
        @Volatile
        var result: String = ""
            private set

        override fun run() {
            try {
                val text = input.bufferedReader(charset).readText()
                result = if (text.length <= maxChars) text else text.takeLast(min(text.length, maxChars))
            } catch (_: Exception) {
                result = ""
            }
        }
    }

    private class StreamPumper(
        private val input: InputStream,
        private val charset: Charset,
        private val onLine: (String) -> Unit,
    ) : Thread("script-stream-pumper") {
        override fun run() {
            try {
                input.bufferedReader(charset).useLines { seq ->
                    seq.forEach { onLine(it) }
                }
            } catch (_: Exception) {
                // ignore
            }
        }
    }
}