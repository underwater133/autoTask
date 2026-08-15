package com.autotask.app.executor

import com.autotask.app.task.ScheduleMode
import com.autotask.app.task.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * AppLauncher 单元测试：悬浮窗检查开关（前台测试模式绕过，后台模式检查）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLauncherTest {

    private val context get() = RuntimeEnvironment.getApplication()

    private fun task(targetPackage: String = context.packageName) = Task(
        id = 1,
        name = "t",
        targetPackage = targetPackage,
        scheduleMode = ScheduleMode.ONCE,
        hour = 8,
        minute = 0,
    )

    @Test
    fun launch_requireOverlay_withoutPermission_failsWithOverlayReason() {
        // 默认 requireOverlay=true：未授权悬浮窗时直接失败并给出明确原因
        val result = AppLauncher.launch(context, task())
        assertFalse(result.ok)
        assertTrue(result.reason.contains("悬浮窗"))
    }

    @Test
    fun launch_noOverlayCheck_canStartOwnApp() {
        // requireOverlay=false（前台手动测试模式）：不检查悬浮窗，直接启动
        val result = AppLauncher.launch(context, task(), requireOverlay = false)
        assertTrue("前台测试模式应能启动自身应用: ${result.reason}", result.ok)
    }

    // ---------- 组件输入解析 ----------

    @Test
    fun parseComponent_onlyPackage() {
        val (pkg, act) = AppLauncher.parseComponentInput("com.launchrecorder.app")
        assertEquals("com.launchrecorder.app", pkg)
        assertEquals("", act)
    }

    @Test
    fun parseComponent_relativeActivity() {
        // "包名/.MainActivity" → 自动补全为完整类名
        val (pkg, act) = AppLauncher.parseComponentInput("com.launchrecorder.app/.MainActivity")
        assertEquals("com.launchrecorder.app", pkg)
        assertEquals("com.launchrecorder.app.MainActivity", act)
    }

    @Test
    fun parseComponent_fullActivity() {
        val (pkg, act) = AppLauncher.parseComponentInput("com.launchrecorder.app/com.launchrecorder.app.MainActivity")
        assertEquals("com.launchrecorder.app", pkg)
        assertEquals("com.launchrecorder.app.MainActivity", act)
    }

    @Test
    fun parseComponent_trimsWhitespace() {
        val (pkg, act) = AppLauncher.parseComponentInput("  com.example.app/.Main  ")
        assertEquals("com.example.app", pkg)
        assertEquals("com.example.app.Main", act)
    }

    @Test
    fun launch_explicitComponent_bypassesResolution() {
        // 显式组件启动（不经过 intent 解析）：对自身包名有效
        val t = task().copy(targetActivity = "${context.packageName}.MainActivity")
        val result = AppLauncher.launch(context, t, requireOverlay = false)
        assertTrue("显式组件启动应成功: ${result.reason}", result.ok)
    }
}
