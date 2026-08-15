package com.autotask.app.executor

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import com.autotask.app.log.AppLogger
import com.autotask.app.schedule.Scheduler
import com.autotask.app.schedule.TaskAlarmReceiver
import com.autotask.app.schedule.TaskRetryReceiver
import com.autotask.app.task.ScheduleMode
import com.autotask.app.task.Task
import com.autotask.app.task.TaskDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowSettings

/**
 * 端到端执行链路测试（Robolectric）：
 * 闹钟触发 → 拉起失败 → 重试调度 → 重试耗尽 → 放弃/停用；以及成功路径。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskExecutorFlowTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var dao: TaskDao

    @Before
    fun setUp() {
        dao = TaskDao(context)
        AppLogger.clear(context)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        // 重置震动设置（SharedPreferences 在测试间会保留）
        com.autotask.app.settings.SettingsStore.setVibrateOnSuccess(context, false)
        com.autotask.app.settings.SettingsStore.setVibrateOnFailure(context, true)
    }

    private fun insertOnceTask(targetPackage: String = "com.tencent.mm"): Long =
        dao.insert(
            Task(
                name = "端到端任务",
                targetPackage = targetPackage,
                scheduleMode = ScheduleMode.ONCE,
                hour = 8,
                minute = 0,
            )
        )

    private fun fireAlarm(taskId: Long) {
        val intent = Intent(context, TaskAlarmReceiver::class.java)
            .putExtra(Scheduler.EXTRA_TASK_ID, taskId)
        TaskAlarmReceiver().onReceive(context, intent)
    }

    private fun fireRetry(taskId: Long, attempt: Int) {
        val intent = Intent(context, TaskRetryReceiver::class.java)
            .putExtra(Scheduler.EXTRA_TASK_ID, taskId)
            .putExtra(TaskExecutor.EXTRA_ATTEMPT, attempt)
        TaskRetryReceiver().onReceive(context, intent)
    }

    @Test
    fun trigger_withoutOverlayPermission_logsFailure_andSchedulesExactRetry() {
        // 目标应用用自身包名（Robolectric 中已"安装"），但未授予悬浮窗
        val id = insertOnceTask(targetPackage = context.packageName)
        fireAlarm(id)

        val logs = AppLogger.readRecent(context)
        assertTrue("日志应含任务触发:\n${logs.joinToString("\n")}", logs.any { it.contains("任务触发") })
        assertTrue("日志应含启动失败+悬浮窗:\n${logs.joinToString("\n")}", logs.any { it.contains("启动失败") && it.contains("悬浮窗权限") })
        assertTrue("日志应含重试计划:\n${logs.joinToString("\n")}", logs.any { it.contains("30 秒后进行第 2 次重试") })

        // 重试闹钟：RTC_WAKEUP + allowWhileIdle（唤醒设备，Doze 下不被挂起），触发时间 ≈ 30s 后
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarms = shadowOf(am).scheduledAlarms
        assertTrue("应已调度重试闹钟", alarms.isNotEmpty())
        val retryAlarm = alarms[0]
        assertTrue("重试闹钟应 allowWhileIdle（Doze 下不被无限挂起）", retryAlarm.allowWhileIdle)
        assertEquals(AlarmManager.RTC_WAKEUP, retryAlarm.type)
        val expected = System.currentTimeMillis() + 30_000
        assertTrue("重试触发时间应约为 30 秒后", Math.abs(retryAlarm.triggerAtTime - expected) < 5_000)
    }

    @Test
    fun retriesExhausted_logsGiveUp_vibrates_andDisablesOnceTask() {
        val id = insertOnceTask()
        fireAlarm(id)
        fireRetry(id, 2)
        fireRetry(id, 3)

        val logs = AppLogger.readRecent(context)
        assertTrue(logs.any { it.contains("已达最大尝试次数") })
        assertTrue(logs.any { it.contains("单次任务已结束并停用") })
        assertFalse(dao.getById(id)!!.enabled)

        // 达到失败次数后应触发震动提醒
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        assertTrue("重试耗尽后应震动提醒", shadowOf(vibrator).isVibrating)
    }

    @Test
    fun retriesExhausted_failureVibrationDisabled_noVibration() {
        com.autotask.app.settings.SettingsStore.setVibrateOnFailure(context, false)
        val id = insertOnceTask()
        fireAlarm(id)
        fireRetry(id, 2)
        fireRetry(id, 3)

        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        assertFalse("关闭失败震动后不应震动", shadowOf(vibrator).isVibrating)
    }

    @Test
    fun successPath_successVibrationEnabled_vibrates() {
        com.autotask.app.settings.SettingsStore.setVibrateOnSuccess(context, true)
        ShadowSettings.setCanDrawOverlays(true)
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        (shadowOf(usm) as org.robolectric.shadows.ShadowUsageStatsManager)
            .addEvent(context.packageName, System.currentTimeMillis(), android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND)
        val id = insertOnceTask(targetPackage = context.packageName)

        fireAlarm(id)

        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && dao.getById(id)!!.enabled) {
            Thread.sleep(100)
        }
        assertFalse(dao.getById(id)!!.enabled)

        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        assertTrue("开启成功震动后，执行成功应震动", shadowOf(vibrator).isVibrating)
    }

    @Test
    fun successPath_successVibrationDisabled_noVibration() {
        // 默认关闭成功震动
        ShadowSettings.setCanDrawOverlays(true)
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        (shadowOf(usm) as org.robolectric.shadows.ShadowUsageStatsManager)
            .addEvent(context.packageName, System.currentTimeMillis(), android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND)
        val id = insertOnceTask(targetPackage = context.packageName)

        fireAlarm(id)

        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && dao.getById(id)!!.enabled) {
            Thread.sleep(100)
        }
        assertFalse(dao.getById(id)!!.enabled)

        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        assertFalse("默认不应成功震动", shadowOf(vibrator).isVibrating)
    }

    @Test
    fun successPath_launchesOwnApp_andDisablesOnceTask() {
        ShadowSettings.setCanDrawOverlays(true)

        // 注入目标应用"进入前台"的使用事件，让前台验证通过
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        (shadowOf(usm) as org.robolectric.shadows.ShadowUsageStatsManager)
            .addEvent(context.packageName, System.currentTimeMillis(), android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND)

        val id = insertOnceTask(targetPackage = context.packageName)

        fireAlarm(id)

        // 前台验证在后台线程执行，轮询等待终态
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && dao.getById(id)!!.enabled) {
            Thread.sleep(100)
        }
        val logsNow = AppLogger.readRecent(context)
        assertFalse("单次任务执行成功后应停用。日志:\n${logsNow.joinToString("\n")}", dao.getById(id)!!.enabled)

        val logs = AppLogger.readRecent(context)
        // 无使用情况访问权限时按成功处理
        assertTrue("日志应含成功/无法验证前台:\n${logs.joinToString("\n")}", logs.any { it.contains("无法验证前台") || it.contains("已进入前台") })
    }
}
