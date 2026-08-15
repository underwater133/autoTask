package com.autotask.app.executor

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.autotask.app.permission.PermissionHelper

/**
 * 前台检测器（M4）：用 UsageStatsManager 判断目标 App 是否真的进入了前台。
 * 需要"使用情况访问"权限；未授权时返回 false（由调用方决定降级策略）。
 */
object ForegroundDetector {

    private const val LOOKBACK_MS = 30_000L

    /**
     * 最近 LOOKBACK_MS 内目标包名是否处于前台（最近一次前台事件晚于最近一次后台事件）。
     */
    fun isAppInForeground(context: Context, packageName: String): Boolean {
        if (!PermissionHelper.isUsageAccessGranted(context)) return false
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - LOOKBACK_MS, now)
            val event = UsageEvents.Event()
            var lastForeground = -1L
            var lastBackground = -1L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.packageName != packageName) continue
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> lastForeground = event.timeStamp
                    UsageEvents.Event.MOVE_TO_BACKGROUND,
                    UsageEvents.Event.ACTIVITY_STOPPED,
                    -> lastBackground = event.timeStamp
                }
            }
            lastForeground > lastBackground && lastForeground > 0
        } catch (_: Exception) {
            false
        }
    }
}
