package com.ghostlock.app.data

import android.content.Context
import java.io.File
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Debug root shell with three interchangeable back ends.
 *
 * 1. [Mode.SU] —— 管理器（KernelSU / ReSukiSU / SukiSU Ultra / 7kimisu …）提供的 `su`。
 *    **这是 KernelSU 激活后唯一可靠的通道**：原版 KSU init 会把启动脚本连同它的
 *    文件轮询循环一起收掉，`.gl_cmd` 写进去也不会有人执行（旧版本"只回显无输出"的原因）。
 * 2. [Mode.TEMP_ROOT] —— 临时 root 的文件通道：app 写 `.gl_cmd`，脚本的轮询循环用
 *    `/system/bin/sh` 执行并把 `$ cmd` + 输出追加到 `.gl_out`，本类 tail 该文件。
 *    仅 KSU 激活前有效，且打开终端时会先用 [probeChannel] 探活。
 * 3. [Mode.LOCAL] —— 应用自身权限的 `/system/bin/sh -c`（未 root 时的兜底）。
 *
 * 命令一律以独立进程执行并**流式**回传输出：密集输出（`dmesg`、`logcat`）不会把
 * 输出一次性堆进内存或卡住 UI；超时/停止会 `destroyForcibly`。
 */
object RootTerminal {
    private const val OUT_NAME = ".gl_out"
    private const val CMD_NAME = ".gl_cmd"
    private const val MAX_CHUNK = 1 shl 20

    /** 通道存活探测用的标记；UI 侧会过滤掉带该前缀的行。 */
    const val PROBE_PREFIX = "__ghostlock_probe_"
    private const val PROBE_TIMEOUT_MS = 1_600L

    private const val SU_PROBE_TIMEOUT_S = 8L
    private const val SU_NEGATIVE_TTL_MS = 15_000L

    /** su 候选：KernelSU 系管理器通常经它自己的挂载在 /system/bin/su 暴露（Magisk 系在
     *  /debug_ramdisk/su）；`/data/adb` 下的路径对应用不可见，故不列。最后的 "su" 走 PATH。
     *  超时给到 8 秒：首次调用可能弹管理器授权框，用户点"允许"后即返回。 */
    private val SU_CANDIDATES = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/debug_ramdisk/su",
        "su",
    )

    /** When set, the next run passes `GHOSTLOCK_KSU_ENFORCE=9`: the root script
     * stops before KSU deployment and leaves the temp root shell running. */
    @Volatile
    var debugRootOnly: Boolean = false

    private fun outFile(context: Context) = File(context.filesDir, OUT_NAME)

    private fun cmdFile(context: Context) = File(context.filesDir, CMD_NAME)

    /** Why the channel is (not) usable — shown by the terminal UI. */
    enum class Status { READY, NOT_RUN, KSU_ACTIVE, STALE }

    /** 命令执行后端。 */
    enum class Mode { SU, TEMP_ROOT, LOCAL }

    data class ExecResult(
        val exitCode: Int? = null,
        val timedOut: Boolean = false,
        val error: String? = null,
    )

    @Volatile
    private var running: Process? = null

    @Volatile
    private var cachedSu: String? = null

    @Volatile
    private var suNegativeAt: Long = 0L

    private const val KSU_LOG_PREFIX = "ghostlock-ksu-"
    private const val KSU_LOG_LIMIT = 256 * 1024

    /** Distinguishes "no run yet" from "the run was skipped because KernelSU
     * already owns root" and from "an older build wrote the root script". */
    fun diagnose(context: Context): Status {
        if (isReady(context)) return Status.READY
        val newest = context.filesDir.listFiles { file ->
            file.isFile && file.name.startsWith(KSU_LOG_PREFIX)
        }?.maxByOrNull { it.lastModified() } ?: return Status.NOT_RUN
        val text = runCatching {
            newest.inputStream().use { input ->
                val buffer = ByteArray(KSU_LOG_LIMIT)
                val read = input.read(buffer)
                if (read > 0) String(buffer, 0, read) else ""
            }
        }.getOrDefault("")
        return when {
            text.contains("debug root terminal ready") -> Status.STALE
            text.contains("KernelSU already has root") -> Status.KSU_ACTIVE
            text.contains("root script start") -> Status.STALE
            else -> Status.NOT_RUN
        }
    }

    /** True once a run reached the patched script (it creates `.gl_out`). */
    fun isReady(context: Context): Boolean = outFile(context).isFile

    /** `.gl_out` 当前长度：终端打开时从这里开始 tail。 */
    fun outLength(context: Context): Long = runCatching { outFile(context).length() }.getOrDefault(0L)

    /** KernelSU 系驱动是否已加载（`/proc/modules` 对应用可读）。 */
    fun ksuLoaded(): Boolean = runCatching {
        File("/proc/modules").useLines { lines ->
            lines.any { it.startsWith("kernelsu") || it.startsWith("sevenk") }
        }
    }.getOrDefault(false)

    /** Writes the command atomically so the root loop never sees a partial file. */
    fun submit(context: Context, command: String): Boolean {
        val line = command.trim()
        if (line.isEmpty()) return false
        return runCatching {
            val staged = File(context.filesDir, "$CMD_NAME.new")
            staged.writeText(line + "\n")
            val target = cmdFile(context)
            if (target.exists() && !target.delete()) error("cannot replace $CMD_NAME")
            require(staged.renameTo(target)) { "rename failed" }
        }.isSuccess
    }

    /** Returns newly appended output and the offset to resume from. */
    fun readNew(context: Context, offset: Long): Pair<String, Long> = runCatching {
        val file = outFile(context)
        if (!file.isFile) return@runCatching "" to offset
        val length = file.length()
        if (length < offset) return@runCatching "" to 0L // cleared externally
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            val size = (length - offset).coerceAtMost(MAX_CHUNK.toLong()).toInt()
            val buffer = ByteArray(size)
            val read = raf.read(buffer)
            if (read <= 0) "" to offset
            else String(buffer, 0, read) to (offset + read)
        }
    }.getOrElse { "" to offset }

    fun clear(context: Context) {
        runCatching { outFile(context).writeText("") }
    }

    /**
     * 文件通道存活探测：写一条 `echo <marker>`，1.6 秒内没在 `.gl_out` 里看到标记，
     * 就认为脚本的轮询循环已经不在了（典型场景：KernelSU 已激活并收掉了脚本）。
     */
    suspend fun probeChannel(context: Context): Boolean = withContext(Dispatchers.IO) {
        val file = outFile(context)
        if (!file.isFile) return@withContext false
        val marker = PROBE_PREFIX + System.nanoTime()
        if (!submit(context, "echo $marker")) return@withContext false
        var offset = file.length()
        val deadline = System.currentTimeMillis() + PROBE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val (chunk, next) = readNew(context, offset)
            if (chunk.isNotEmpty()) {
                offset = next
                if (chunk.contains(marker)) return@withContext true
            }
            delay(120)
        }
        false
    }

    /** 探测管理器 su 是否可用（root 生效）。正结果永久缓存，负结果缓存 15 秒。 */
    suspend fun suBinary(): String? = withContext(Dispatchers.IO) {
        cachedSu?.let { return@withContext it }
        if (System.currentTimeMillis() - suNegativeAt < SU_NEGATIVE_TTL_MS) return@withContext null
        for (candidate in SU_CANDIDATES) {
            if (candidate != "su" && !File(candidate).exists()) continue
            val ok = runCatching {
                val process = ProcessBuilder(candidate, "-c", "id")
                    .redirectErrorStream(true)
                    .start()
                val finished = process.waitFor(SU_PROBE_TIMEOUT_S, TimeUnit.SECONDS)
                val text = process.inputStream.bufferedReader().use { it.readText() }
                if (!finished) process.destroyForcibly()
                finished && text.contains("uid=0")
            }.getOrDefault(false)
            if (ok) {
                cachedSu = candidate
                return@withContext candidate
            }
        }
        suNegativeAt = System.currentTimeMillis()
        null
    }

    /** 打开终端/运行状态变化时解析一次后端：su 优先，其次探活的临时 root 通道，最后本地。 */
    suspend fun resolveMode(context: Context): Mode {
        if (suBinary() != null) return Mode.SU
        if (probeChannel(context)) return Mode.TEMP_ROOT
        return Mode.LOCAL
    }

    /** 终止当前命令（终端 UI 的“停止”）。 */
    fun cancelCurrent() {
        runCatching { running?.destroyForcibly() }
        running = null
    }

    /**
     * 以独立进程执行命令，输出按块回调（在 IO 线程）。`dmesg` 这类密集输出因此
     * 是流式的：内存只留调用方需要的部分，UI 侧自己决定保留多少行。
     */
    suspend fun exec(
        command: String,
        mode: Mode,
        timeoutSeconds: Long = 60,
        suBinary: String? = null,
        onChunk: (String) -> Unit = {},
    ): ExecResult = withContext(Dispatchers.IO) {
        val argv = when (mode) {
            Mode.SU -> listOf(suBinary ?: cachedSu ?: "su", "-c", command)
            Mode.TEMP_ROOT, Mode.LOCAL -> listOf("/system/bin/sh", "-c", command)
        }
        runCatching {
            val process = ProcessBuilder(argv)
                .redirectErrorStream(true)
                .start()
            running = process
            val reader = Thread {
                runCatching {
                    InputStreamReader(process.inputStream, StandardCharsets.UTF_8).use { stream ->
                        val buffer = CharArray(4096)
                        while (true) {
                            val read = stream.read(buffer)
                            if (read <= 0) break
                            onChunk(String(buffer, 0, read))
                        }
                    }
                }
            }.apply {
                isDaemon = true
                start()
            }
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                reader.join(500)
                ExecResult(timedOut = true)
            } else {
                reader.join(1_500)
                ExecResult(exitCode = process.exitValue())
            }
        }.getOrElse { error ->
            ExecResult(error = error.message ?: error.javaClass.simpleName)
        }.also { running = null }
    }

    /** Fallback execution in the app's own context (`/system/bin/sh -c`), so the
     * terminal still works before any root channel exists. Output is captured
     * on a reader thread; a timeout kills the process instead of hanging the UI. */
    suspend fun runLocal(command: String, timeoutSeconds: Long = 20): String {
        val output = StringBuilder()
        val result = exec(command, Mode.LOCAL, timeoutSeconds) { output.append(it) }
        val tail = when {
            result.timedOut -> "[timeout]\n"
            result.error != null -> "! local exec failed: ${result.error}\n"
            result.exitCode != null -> "[exit ${result.exitCode}]\n"
            else -> ""
        }
        return output.toString() + tail
    }
}
