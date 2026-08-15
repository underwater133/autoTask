package com.autotask.app.log

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 轻量文件日志器（M4 落盘 / M5 页面展示）。
 * - 追加写，单文件环形上限 1MB
 * - 超限时保留最近 3000 行
 * - 线程安全（执行与重试可能并发写）
 */
object AppLogger {

    private const val MAX_FILE_SIZE = 1_000_000L
    private const val MAX_LINES_TO_KEEP = 3000
    private const val FILE_NAME = "executions.log"

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private fun logFile(context: android.content.Context): File =
        File(context.filesDir, "logs/$FILE_NAME")

    @Synchronized
    fun log(context: android.content.Context, message: String) {
        val file = logFile(context)
        file.parentFile?.mkdirs()
        val line = "${timeFormat.format(Date())} | $message\n"
        file.appendText(line)
        if (file.length() > MAX_FILE_SIZE) trim(file)
    }

    private fun trim(file: File) {
        val keep = file.readLines().takeLast(MAX_LINES_TO_KEEP)
        file.writeText(keep.joinToString("\n", postfix = "\n"))
    }

    /** 读取最近 limit 条日志，最新在前 */
    @Synchronized
    fun readRecent(context: android.content.Context, limit: Int = 200): List<String> {
        val file = logFile(context)
        if (!file.exists()) return emptyList()
        return file.readLines().takeLast(limit).reversed()
    }

    @Synchronized
    fun clear(context: android.content.Context) {
        logFile(context).delete()
    }
}
