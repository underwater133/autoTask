package com.autotask.app.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * AppLogger 运行时测试（Robolectric）：真实文件写入/读取/清空。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLoggerTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        AppLogger.clear(context)
    }

    @Test
    fun log_thenReadRecent_containsLine() {
        AppLogger.log(context, "测试日志A")
        AppLogger.log(context, "测试日志B")

        val lines = AppLogger.readRecent(context)
        assertEquals(2, lines.size)
        // 最新在前
        assertTrue(lines[0].contains("测试日志B"))
        assertTrue(lines[1].contains("测试日志A"))
        // 每行带时间戳前缀
        assertTrue(lines[0].startsWith("20"))
    }

    @Test
    fun readRecent_emptyWhenNoLogs() {
        assertTrue(AppLogger.readRecent(context).isEmpty())
    }

    @Test
    fun clear_removesAll() {
        AppLogger.log(context, "将被清空")
        AppLogger.clear(context)
        assertTrue(AppLogger.readRecent(context).isEmpty())
    }

    @Test
    fun readRecent_respectsLimit() {
        repeat(10) { AppLogger.log(context, "日志#$it") }
        val lines = AppLogger.readRecent(context, limit = 3)
        assertEquals(3, lines.size)
        assertTrue(lines.first().contains("日志#9"))
    }
}
