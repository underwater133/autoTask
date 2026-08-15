package com.autotask.app.permission

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * 权限检测与系统设置跳转工具（M1）。
 *
 * 覆盖四类关键权限：
 * - 悬浮窗 SYSTEM_ALERT_WINDOW：Android 10+ 从后台拉起 Activity 的豁免途径
 * - 电池优化白名单：避免 Doze 深度休眠导致闹钟延迟
 * - 通知：常驻通知与执行结果提醒
 * - 使用情况访问 PACKAGE_USAGE_STATS：判断目标 App 是否真的进入前台
 */
object PermissionHelper {

    // ---------- 悬浮窗 ----------
    fun isOverlayGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun overlaySettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )

    // ---------- 电池优化 ----------
    fun isBatteryOptimizationExempt(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun batteryOptimizationIntent(context: Context): Intent {
        // 优先直接请求本应用豁免；个别 ROM 不支持该 Intent 时回退到列表页
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
        if (direct.resolveActivity(context.packageManager) != null) return direct
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

    // ---------- 通知 ----------
    fun isNotificationEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    // ---------- 使用情况访问 ----------
    fun isUsageAccessGranted(context: Context): Boolean {
        // 方式一：AppOps 检查（部分 ROM 返回不准确）
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        if (mode == AppOpsManager.MODE_ALLOWED) return true

        // 方式二：真实查询一次使用事件（无权限时返回空，跨 ROM 更可靠）
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 60_000, now)
            val event = UsageEvents.Event()
            var hasData = false
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                hasData = true
                break
            }
            hasData
        } catch (_: SecurityException) {
            false
        }
    }

    fun usageAccessSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
}
