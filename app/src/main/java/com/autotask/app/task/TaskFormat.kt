package com.autotask.app.task

import android.content.Context
import android.content.pm.PackageManager

/**
 * 任务展示辅助（M2）。
 */
object TaskFormat {

    private val WEEKDAY_CHARS = charArrayOf('一', '二', '三', '四', '五', '六', '日')

    /** 模式中文名 */
    fun modeLabel(mode: ScheduleMode): String = when (mode) {
        ScheduleMode.ONCE -> "单次"
        ScheduleMode.DAILY -> "每日"
        ScheduleMode.WEEKLY -> "每周"
        ScheduleMode.WORKDAY -> "工作日"
        ScheduleMode.HOLIDAY -> "节假日"
        ScheduleMode.SMART -> "智能"
    }

    /** WEEKLY 的星期位掩码 → "一、三、五" */
    fun weekDaysText(weekDays: Int): String =
        buildString {
            for (i in 0..6) {
                if (weekDays and (1 shl i) != 0) {
                    if (isNotEmpty()) append("、")
                    append(WEEKDAY_CHARS[i])
                }
            }
        }

    /** 列表页摘要 */
    fun summary(task: Task): String = when (task.scheduleMode) {
        ScheduleMode.ONCE -> "单次 · ${task.timeText()}"
        ScheduleMode.DAILY -> "每日 · ${task.timeText()}"
        ScheduleMode.WEEKLY -> "每周 · ${weekDaysText(task.weekDays)} · ${task.timeText()}"
        ScheduleMode.WORKDAY -> "工作日 · ${task.timeText()}"
        ScheduleMode.HOLIDAY -> "节假日 · ${task.timeText()}"
        ScheduleMode.SMART -> "智能 · ${task.timeText()}"
    }

    /** 目标应用显示名；查询失败时回退包名 */
    fun appLabel(context: Context, packageName: String): String {
        val pm = context.packageManager
        return runCatching {
            pm.getApplicationInfo(packageName, 0)
                .loadLabel(pm).toString()
        }.getOrElse { packageName }
    }

    /** 目标应用是否仍安装 */
    fun isAppInstalled(context: Context, packageName: String): Boolean =
        runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
        }.isSuccess

    /** 完整组件串（"包名" 或 "包名/Activity"），用于展示 */
    fun componentText(task: Task): String =
        if (task.targetActivity.isNotBlank()) {
            "${task.targetPackage}/${task.targetActivity.removePrefix(task.targetPackage)}"
        } else {
            task.targetPackage
        }

    /** 下一个即将执行的任务（启用且触发时间在未来），无则返回 null */
    fun nextUpTask(tasks: List<Task>, now: Long = System.currentTimeMillis()): Task? =
        tasks.filter { it.enabled && it.nextTriggerAt > now }
            .minByOrNull { it.nextTriggerAt }

    /**
     * 触发时间已过且明显未执行的启用任务（可能被系统拦截/闹钟丢失）。
     * 用于周期自检的日志记录（不自动重排，避免睡眠延迟投递导致重复执行）。
     */
    fun findStaleTasks(
        tasks: List<Task>,
        now: Long = System.currentTimeMillis(),
        thresholdMs: Long = 5 * 60_000L,
    ): List<Task> =
        tasks.filter { it.enabled && it.nextTriggerAt > 0 && now - it.nextTriggerAt > thresholdMs }
}
