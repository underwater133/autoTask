package com.autotask.app.task

/**
 * 调度模式（M2 定义，M3 实现日期推算）。
 *
 * - ONCE:    单次，执行后自动完成
 * - DAILY:   每日固定时刻
 * - WEEKLY:  每周勾选的若干星期
 * - WORKDAY: 每个工作日（含调休补班日，跳过法定节假日）
 * - HOLIDAY: 每个节假日（含周末，不含调休补班日）
 * - SMART:   自定义智能规则组合（规则字段后续扩展）
 */
enum class ScheduleMode(val dbValue: Int) {
    ONCE(0),
    DAILY(1),
    WEEKLY(2),
    WORKDAY(3),
    HOLIDAY(4),
    SMART(5);

    companion object {
        fun fromDb(value: Int): ScheduleMode = entries.firstOrNull { it.dbValue == value } ?: DAILY
    }
}

/**
 * 定时任务实体（M2）。
 */
data class Task(
    val id: Long = 0L,
    val name: String,
    val targetPackage: String,
    val targetActivity: String = "",
    val scheduleMode: ScheduleMode,
    val hour: Int,
    val minute: Int,
    /** WEEKLY 模式使用：bit0=周一 … bit6=周日 */
    val weekDays: Int = 0,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    /** 下一次触发时间戳（M3 调度引擎维护） */
    val nextTriggerAt: Long = 0L,
) {
    /** 时间格式 "HH:mm" */
    fun timeText(): String = String.format("%02d:%02d", hour, minute)
}
