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
        // 2027 无内置数据：2027-01-01 是周五 → 朴素规则下为工作日
        assertTrue(HolidayCalendar.isWorkday(2027, 1, 1))
        // 2027-01-02 周六 → 节假日
        assertFalse(HolidayCalendar.isWorkday(2027, 1, 2))
    }

    // ---------- 2026 官方安排（国办发明电〔2025〕7号） ----------

    @Test
    fun year2026_newYear_makeupSunday() {
        // 元旦 1/1~1/3 放假；1/4 周日补班
        assertFalse(HolidayCalendar.isWorkday(2026, 1, 1))
        assertFalse(HolidayCalendar.isWorkday(2026, 1, 2))
        assertTrue(HolidayCalendar.isWorkday(2026, 1, 4))
    }

    @Test
    fun year2026_springFestival_makeupSaturdays() {
        // 春节 2/15~2/23 放假 9 天；2/14、2/28 两个周六补班
        assertFalse(HolidayCalendar.isWorkday(2026, 2, 15))
        assertFalse(HolidayCalendar.isWorkday(2026, 2, 23))
        assertTrue(HolidayCalendar.isWorkday(2026, 2, 14))
        assertTrue(HolidayCalendar.isWorkday(2026, 2, 28))
    }

    @Test
    fun year2026_nationalDay_makeupDays() {
        // 国庆 10/1~10/7；9/20、10/10 补班
        assertFalse(HolidayCalendar.isWorkday(2026, 10, 1))
        assertFalse(HolidayCalendar.isWorkday(2026, 10, 7))
        assertTrue(HolidayCalendar.isWorkday(2026, 9, 20))
        assertTrue(HolidayCalendar.isWorkday(2026, 10, 10))
    }

    @Test
    fun year2026_laborDay_makeupSaturday() {
        // 劳动节 5/1~5/5；5/9 周六补班
        assertFalse(HolidayCalendar.isWorkday(2026, 5, 1))
        assertTrue(HolidayCalendar.isWorkday(2026, 5, 9))
    }
}
