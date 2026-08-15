package com.autotask.app.schedule

import com.autotask.app.calendar.HolidayCalendarData
import com.autotask.app.task.ScheduleMode
import com.autotask.app.task.Task
import java.util.Calendar

/**
 * 下一次触发时间计算器（M3）。
 * 纯逻辑、不依赖 Android API，便于 JVM 单元测试。
 */
object NextTriggerCalculator {

    /**
     * 计算 task 在 now 之后的下一次触发时间戳（毫秒）。
     */
    fun compute(task: Task, now: Long, cal: HolidayCalendarData): Long = when (task.scheduleMode) {
        ScheduleMode.ONCE, ScheduleMode.DAILY, ScheduleMode.SMART ->
            nextAtOrLater(now, task.hour, task.minute)

        ScheduleMode.WEEKLY ->
            nextSelectedWeekday(now, task)

        ScheduleMode.WORKDAY ->
            nextByRule(now, task) { y, m, d -> cal.isWorkday(y, m, d) }

        ScheduleMode.HOLIDAY ->
            nextByRule(now, task) { y, m, d -> cal.isHoliday(y, m, d) }
    }

    /** 下一次 HH:mm 时刻（严格晚于 now；当天已过则顺延一天） */
    private fun nextAtOrLater(now: Long, hour: Int, minute: Int): Long {
        val base = Calendar.getInstance().apply { timeInMillis = now }
        val cand = (base.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cand.timeInMillis <= now) cand.add(Calendar.DAY_OF_YEAR, 1)
        return cand.timeInMillis
    }

    /** 下一个被勾选的星期（严格晚于 now，最多看 8 天覆盖完整一周） */
    private fun nextSelectedWeekday(now: Long, task: Task): Long {
        val base = Calendar.getInstance().apply { timeInMillis = now }
        // Calendar.MONDAY=2 … SUNDAY=8 → 归一化为 0=周一 … 6=周日
        for (offset in 0..7) {
            val cand = (base.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            val index = (cand.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
            if (task.weekDays and (1 shl index) != 0) {
                cand.set(Calendar.HOUR_OF_DAY, task.hour)
                cand.set(Calendar.MINUTE, task.minute)
                cand.set(Calendar.SECOND, 0)
                cand.set(Calendar.MILLISECOND, 0)
                if (cand.timeInMillis > now) return cand.timeInMillis
            }
        }
        // weekDays 为空时（UI 已校验不应发生）：兜底 8 天后
        return nextAtOrLater(now + 8L * 24 * 3600_000, task.hour, task.minute)
    }

    /** 按日期规则（工作日/节假日）找下一个满足条件的日子（严格晚于 now） */
    private fun nextByRule(
        now: Long,
        task: Task,
        matches: (year: Int, month: Int, day: Int) -> Boolean,
    ): Long {
        val base = Calendar.getInstance().apply { timeInMillis = now }
        // 最多看 366 天；每次触发后都会重新调度，窗口足够
        for (offset in 0..366) {
            val cand = (base.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            val y = cand.get(Calendar.YEAR)
            val m = cand.get(Calendar.MONTH) + 1
            val d = cand.get(Calendar.DAY_OF_MONTH)
            if (matches(y, m, d)) {
                cand.set(Calendar.HOUR_OF_DAY, task.hour)
                cand.set(Calendar.MINUTE, task.minute)
                cand.set(Calendar.SECOND, 0)
                cand.set(Calendar.MILLISECOND, 0)
                if (cand.timeInMillis > now) return cand.timeInMillis
            }
        }
        return now + 366L * 24 * 3600_000 // 兜底
    }
}
