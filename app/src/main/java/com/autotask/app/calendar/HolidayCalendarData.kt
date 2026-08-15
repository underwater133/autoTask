package com.autotask.app.calendar

/**
 * 节假日日历数据源接口（M3）。
 * 拆成接口以便 JVM 单元测试注入假数据。
 */
interface HolidayCalendarData {

    /**
     * 某天是否为工作日（中国口径）：
     * 工作日 = 周一~五 ∪ 调休补班日 − 法定节假日
     */
    fun isWorkday(year: Int, month: Int, day: Int): Boolean

    /** 某天是否为节假日（含普通周末，不含调休补班日） */
    fun isHoliday(year: Int, month: Int, day: Int): Boolean = !isWorkday(year, month, day)
}
