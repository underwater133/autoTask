package com.autotask.app.permission

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.autotask.app.R
import com.autotask.app.service.KeeperService

/**
 * 权限引导项清单（M1 核心四类 + M6 常驻服务项）。
 */
object PermissionCatalog {

    fun all(context: Context): List<PermissionItem> = listOf(
        PermissionItem(
            title = context.getString(R.string.perm_overlay_title),
            description = context.getString(R.string.perm_overlay_desc),
            isGranted = { PermissionHelper.isOverlayGranted(it) },
            action = { activity ->
                activity.startActivity(PermissionHelper.overlaySettingsIntent(activity))
            },
        ),
        PermissionItem(
            title = context.getString(R.string.perm_battery_title),
            description = context.getString(R.string.perm_battery_desc),
            isGranted = { PermissionHelper.isBatteryOptimizationExempt(it) },
            action = { activity ->
                activity.startActivity(PermissionHelper.batteryOptimizationIntent(activity))
            },
        ),
        PermissionItem(
            title = context.getString(R.string.perm_notification_title),
            description = context.getString(R.string.perm_notification_desc),
            isGranted = { PermissionHelper.isNotificationEnabled(it) },
            action = { activity ->
                activity.startActivity(PermissionHelper.notificationSettingsIntent(activity))
            },
        ),
        PermissionItem(
            title = context.getString(R.string.perm_usage_title),
            description = context.getString(R.string.perm_usage_desc),
            isGranted = { PermissionHelper.isUsageAccessGranted(it) },
            action = { activity ->
                activity.startActivity(PermissionHelper.usageAccessSettingsIntent(activity))
            },
        ),
        PermissionItem(
            title = context.getString(R.string.perm_service_title),
            description = context.getString(R.string.perm_service_desc),
            isGranted = { KeeperService.isRunning(it) },
            action = { activity ->
                ContextCompat.startForegroundService(
                    activity,
                    Intent(activity, KeeperService::class.java)
                )
            },
        ),
    )
}

/**
 * 系统引导项（防清理，不可自动检测）。
 */
object GuidanceCatalog {

    fun all(context: Context): List<GuidanceItem> = listOf(
        GuidanceItem(
            title = context.getString(R.string.guide_autostart_title),
            description = context.getString(R.string.guide_autostart_desc),
            actionLabel = context.getString(R.string.guide_action_setting),
            action = { activity -> VendorAutostart.openSettings(activity) },
        ),
        GuidanceItem(
            title = context.getString(R.string.guide_lock_title),
            description = context.getString(R.string.guide_lock_desc),
            actionLabel = context.getString(R.string.guide_action_view),
            action = { activity ->
                AlertDialog.Builder(activity)
                    .setTitle(R.string.guide_lock_title)
                    .setMessage(R.string.guide_lock_dialog)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            },
        ),
    )
}
