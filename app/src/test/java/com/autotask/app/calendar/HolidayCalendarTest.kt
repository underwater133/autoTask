package com.autotask.app.calendar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * HolidayCalendar 运行时测试（Robolectric）：从真实 assets/holidays.json 加载。
 * 校验 2025 官方数据 + 无数据年份回退规则。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HolidayCalendarTest {

    @Before
    fun setUp() {
        HolidayCalendar.ensureLoaded(RuntimeEnvironment.getApplication())
    }

    @Test
    fun normalWeekday_isWorkday() {
        // 2025-03-17 周一
        assertTrue(HolidayCalendar.isWorkday(2025, 3, 17))
        // 2025-03-21 周五
        assertTrue(HolidayCalendar.isWorkday(2025, 3, 21))
    }

    @Test
    fun normalWeekend_isHoliday() {
        // 2025-03-22 周六 / 2025-03-23 周日
        assertFalse(HolidayCalendar.isWorkday(2025, 3, 22))
        assertFalse(HolidayCalendar.isWorkday(2025, 3, 23))
        assertTrue(HolidayCalendar.isHoliday(2025, 3, 22))
    }

    @Test
    fun makeupWorkday_countsAsWorkday() {
        // 2025-01-26 周日（春节调休补班）
        assertTrue(HolidayCalendar.isWorkday(2025, 1, 26))
        // 2025-02-08 周六（补班）
        assertTrue(HolidayCalendar.isWorkday(2025, 2, 8))
        // 2025-10-11 周六（国庆补班）
        assertTrue(HolidayCalendar.isWorkday(2025, 10, 11))
    }

    @Test
    fun statutoryHoliday_notWorkday() {
        // 春节假期 2025-01-28 ~ 02-04
        assertFalse(HolidayCalendar.isWorkday(2025, 1, 28))
        assertFalse(HolidayCalendar.isWorkday(2025, 2, 4))
        // 劳动节 2025-05-01
        assertFalse(HolidayCalendar.isWorkday(2025, 5, 1))
        // 国庆中秋 2025-10-01
        assertFalse(HolidayCalendar.isWorkday(2025, 10, 1))
    }

    @Test
    fun yearWithoutData_fallsBackToWeekdayRule() {
        // 2026 无内置数据：2026-01-01 是周四 → 朴素规则下为工作日
        assertTrue(HolidayCalendar.isWorkday(2026, 1, 1))
        // 2026-01-03 周六 → 节假日
        assertFalse(HolidayCalendar.isWorkday(2026, 1, 3))
    }
}
