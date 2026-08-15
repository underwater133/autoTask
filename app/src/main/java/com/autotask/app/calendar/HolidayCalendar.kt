package com.autotask.app.calendar

import android.content.Context
import org.json.JSONObject
import java.util.Calendar

/**
 * 节假日日历（M3）：从 assets/holidays.json 加载中国法定节假日与调休数据。
 * 数据格式：
 * {
 *   "2025": {
 *     "holidays": ["01-01", "01-28", ...],   // 法定节假日（含调休连休）
 *     "workdays": ["01-26", "02-08", ...]    // 调休补班日
 *   }
 * }
 *
 * 无数据的年份回退为"仅周一~周五为工作日"的朴素规则，并可通过
 * 后续的联网更新功能补充（见开发计划 §3.1）。
 */
object HolidayCalendar : HolidayCalendarData {

    /** year -> (holidays, workdays)，日期格式 "MM-dd" */
    private var years: Map<Int, Pair<Set<String>, Set<String>>> = emptyMap()

    @Volatile
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            load(context)
            loaded = true
        }
    }

    private fun load(context: Context) {
        val json = context.assets.open("holidays.json").bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val result = mutableMapOf<Int, Pair<Set<String>, Set<String>>>()
        for (key in root.keys()) {
            val year = key.toIntOrNull() ?: continue
            val obj = root.getJSONObject(key)
            val holidays = jsonArrayToSet(obj, "holidays")
            val workdays = jsonArrayToSet(obj, "workdays")
            result[year] = holidays to workdays
        }
        years = result
    }

    private fun jsonArrayToSet(obj: JSONObject, field: String): Set<String> {
        val array = obj.optJSONArray(field) ?: return emptySet()
        return buildSet { for (i in 0 until array.length()) add(array.getString(i)) }
    }

    override fun isWorkday(year: Int, month: Int, day: Int): Boolean {
        val key = "%02d-%02d".format(month, day)
        val data = years[year]
        return if (data != null) {
            when {
                key in data.second -> true  // 调休补班日
                key in data.first -> false  // 法定节假日
                else -> isWeekday(year, month, day)
            }
        } else {
            isWeekday(year, month, day)     // 无数据年份：朴素规则
        }
    }

    private fun isWeekday(year: Int, month: Int, day: Int): Boolean {
        val c = Calendar.getInstance()
        c.clear()
        c.set(year, month - 1, day)
        val dow = c.get(Calendar.DAY_OF_WEEK)
        return dow != Calendar.SATURDAY && dow != Calendar.SUNDAY
    }
}
