package com.autotask.app.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * TaskFormat 单元测试：组件串展示 + 下一个即将执行任务。
 */
class TaskFormatTest {

    private fun task(
        name: String,
        nextTriggerAt: Long,
        enabled: Boolean = true,
        activity: String = "",
    ) = Task(
        id = 1,
        name = name,
        targetPackage = "com.example.app",
        targetActivity = activity,
        scheduleMode = ScheduleMode.DAILY,
        hour = 8,
        minute = 0,
        enabled = enabled,
        nextTriggerAt = nextTriggerAt,
    )

    @Test
    fun componentText_packageOnly() {
        assertEquals("com.example.app", TaskFormat.componentText(task("t", 0)))
    }

    @Test
    fun componentText_withActivity() {
        // 相对形式展示（去掉包名前缀）
        val t = task("t", 0, activity = "com.example.app.MainActivity")
        assertEquals("com.example.app/.MainActivity", TaskFormat.componentText(t))
    }

    @Test
    fun nextUpTask_picksNearestFuture() {
        val now = 1_000_000L
        val tasks = listOf(
            task("过去", now - 1000),
            task("最近", now + 60_000),
            task("较远", now + 300_000),
            task("更远", now + 600_000),
        )
        assertEquals("最近", TaskFormat.nextUpTask(tasks, now)?.name)
    }

    @Test
    fun nextUpTask_ignoresDisabledAndPast() {
        val now = 1_000_000L
        val tasks = listOf(
            task("停用但未来", now + 60_000, enabled = false),
            task("启用但过去", now - 1000),
            task("启用且未来", now + 120_000),
        )
        assertEquals("启用且未来", TaskFormat.nextUpTask(tasks, now)?.name)
    }

    @Test
    fun nextUpTask_emptyWhenNoFutureEnabled() {
        val now = 1_000_000L
        assertNull(TaskFormat.nextUpTask(emptyList(), now))
        assertNull(TaskFormat.nextUpTask(listOf(task("停用", now + 1000, enabled = false)), now))
    }
}
