package com.autotask.app.schedule

import com.autotask.app.calendar.HolidayCalendarData
import com.autotask.app.task.ScheduleMode
import com.autotask.app.task.Task
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * NextTriggerCalculator 单元测试（M3）。
 * 使用与 assets/holidays.json 一致的 2025 年官方数据。
 */
class NextTriggerCalculatorTest {

    /** 2025 官方节假日（与 assets/holidays.json 一致） */
    private val holidays2025 = setOf(
        "01-01",
        "01-28", "01-29", "01-30", "01-31", "02-01", "02-02", "02-03", "02-04",
        "04-04", "04-05", "04-06",
        "05-01", "05-02", "05-03", "05-04", "05-05",
        "05-31", "06-01", "06-02",
        "10-01", "10-02", "10-03", "10-04", "10-05", "10-06", "10-07", "10-08",
    )

    /** 2025 官方调休补班日 */
    private val workdays2025 = setOf("01-26", "02-08", "04-27", "09-28", "10-11")

    private val fakeCal = object : HolidayCalendarData {
        override fun isWorkday(year: Int, month: Int, day: Int): Boolean {
            val key = "%02d-%02d".format(month, day)
            return when {
                year != 2025 -> isPlainWeekday(year, month, day)
                key in workdays2025 -> true
                key in holidays2025 -> false
                else -> isPlainWeekday(year, month, day)
            }
        }
    }

    private fun isPlainWeekday(year: Int, month: Int, day: Int): Boolean {
        val c = Calendar.getInstance()
        c.clear()
        c.set(year, month - 1, day)
        val dow = c.get(Calendar.DAY_OF_WEEK)
        return dow != Calendar.SATURDAY && dow != Calendar.SUNDAY
    }

    private fun at(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0): Long {
        val c = Calendar.getInstance()
        c.clear()
        c.set(y, mo - 1, d, h, mi, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun task(
        mode: ScheduleMode,
        hour: Int = 8,
        minute: Int = 0,
        weekDays: Int = 0,
    ) = Task(
        id = 1,
        name = "t",
        targetPackage = "com.example.app",
        scheduleMode = mode,
        hour = hour,
        minute = minute,
        weekDays = weekDays,
    )

    // ---------- 每日 ----------
    @Test
    fun daily_beforeTime_sameDay() {
        // 2025-03-10(周一) 07:00 → 当日 08:30
        assertEquals(at(2025, 3, 10, 8, 30), NextTriggerCalculator.compute(task(ScheduleMode.DAILY, minute = 30), at(2025, 3, 10, 7, 0), fakeCal))
    }

    @Test
    fun daily_afterTime_nextDay() {
        // 2025-03-10 09:00 → 次日 08:30
        assertEquals(at(2025, 3, 11, 8, 30), NextTriggerCalculator.compute(task(ScheduleMode.DAILY, minute = 30), at(2025, 3, 10, 9, 0), fakeCal))
    }

    @Test
    fun daily_exactTime_isFuture() {
        // 恰好等于触发时刻 → 顺延到次日
        assertEquals(at(2025, 3, 11, 8, 30), NextTriggerCalculator.compute(task(ScheduleMode.DAILY, minute = 30), at(2025, 3, 10, 8, 30), fakeCal))
    }

    // ---------- 每周 ----------
    @Test
    fun weekly_selectedDay_picksNext() {
        // 周一+周三 09:00；now=周一 10:00 → 本周三 09:00
        val t = task(ScheduleMode.WEEKLY, hour = 9, weekDays = (1 shl 0) or (1 shl 2)) // 周一、周三
        assertEquals(at(2025, 3, 12, 9, 0), NextTriggerCalculator.compute(t, at(2025, 3, 10, 10, 0), fakeCal))
    }

    @Test
    fun weekly_selectedDay_todayBeforeTime() {
        // 周一 08:00 → 周一 09:00（当天未到时刻）
        val t = task(ScheduleMode.WEEKLY, hour = 9, weekDays = 1 shl 0)
        assertEquals(at(2025, 3, 10, 9, 0), NextTriggerCalculator.compute(t, at(2025, 3, 10, 8, 0), fakeCal))
    }

    @Test
    fun weekly_wrapToNextWeek() {
        // 仅周一；now=周一 09:01 → 下周一 09:00
        val t = task(ScheduleMode.WEEKLY, hour = 9, weekDays = 1 shl 0)
        assertEquals(at(2025, 3, 17, 9, 0), NextTriggerCalculator.compute(t, at(2025, 3, 10, 9, 1), fakeCal))
    }

    // ---------- 工作日（含调休） ----------
    @Test
    fun workday_saturday_beforeMakeupSunday() {
        // 2025-01-25(周六) → 01-26(周日, 春节调休补班) 09:00
        val t = task(ScheduleMode.WORKDAY, hour = 9)
        assertEquals(at(2025, 1, 26, 9, 0), NextTriggerCalculator.compute(t, at(2025, 1, 25, 12, 0), fakeCal))
    }

    @Test
    fun workday_skipsSpringFestivalHoliday() {
        // 2025-01-27(周一) → 春节假期 01-28~02-04 → 02-05(周三) 09:00
        val t = task(ScheduleMode.WORKDAY, hour = 9)
        assertEquals(at(2025, 2, 5, 9, 0), NextTriggerCalculator.compute(t, at(2025, 1, 27, 12, 0), fakeCal))
    }

    @Test
    fun workday_makeupSaturday_countsAsWorkday() {
        // 2025-02-08(周六, 调休补班) 是工作日；now=02-07(周五) 20:00 → 02-08 09:00
        val t = task(ScheduleMode.WORKDAY, hour = 9)
        assertEquals(at(2025, 2, 8, 9, 0), NextTriggerCalculator.compute(t, at(2025, 2, 7, 20, 0), fakeCal))
    }

    // ---------- 节假日（含周末，不含补班） ----------
    @Test
    fun holiday_fridayToWeekend() {
        // 2025-01-24(周五) 12:00 → 01-25(周六) 10:00
        val t = task(ScheduleMode.HOLIDAY, hour = 10)
        assertEquals(at(2025, 1, 25, 10, 0), NextTriggerCalculator.compute(t, at(2025, 1, 24, 12, 0), fakeCal))
    }

    @Test
    fun holiday_skipsMakeupWorkday() {
        // 2025-01-26(周日) 是补班日，不算节假日；now=01-26 08:00 → 01-28(春节假期第一天) 10:00
        val t = task(ScheduleMode.HOLIDAY, hour = 10)
        assertEquals(at(2025, 1, 28, 10, 0), NextTriggerCalculator.compute(t, at(2025, 1, 26, 8, 0), fakeCal))
    }

    @Test
    fun holiday_saturdayToSunday() {
        // 2025-03-15(周六) 12:00 → 03-16(周日) 10:00
        val t = task(ScheduleMode.HOLIDAY, hour = 10)
        assertEquals(at(2025, 3, 16, 10, 0), NextTriggerCalculator.compute(t, at(2025, 3, 15, 12, 0), fakeCal))
    }

    // ---------- 单次 / 智能 ----------
    @Test
    fun once_lateNight() {
        assertEquals(at(2025, 3, 10, 23, 0), NextTriggerCalculator.compute(task(ScheduleMode.ONCE, hour = 23), at(2025, 3, 10, 22, 0), fakeCal))
    }

    @Test
    fun smart_fallsBackToDaily() {
        assertEquals(at(2025, 3, 10, 8, 30), NextTriggerCalculator.compute(task(ScheduleMode.SMART, minute = 30), at(2025, 3, 10, 7, 0), fakeCal))
    }
}
