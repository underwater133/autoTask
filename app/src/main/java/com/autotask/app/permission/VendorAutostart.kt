package com.autotask.app.permission

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * 国产 ROM 自启动管理页跳转（M6）。
 * 各厂商设置页 Intent 可能随系统版本变化，逐个 try 并回退到系统设置总页。
 */
object VendorAutostart {

    /** 打开当前厂商的自启动管理页；失败时回退系统设置 */
    fun openSettings(activity: Activity) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val candidates = when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> listOf(
                "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
                "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivityV2",
            )
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> listOf(
                "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                "com.huawei.systemmanager" to "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
            )
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> listOf(
                "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
                "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            )
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> listOf(
                "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
            )
            manufacturer.contains("samsung") -> listOf(
                "com.samsung.android.lool" to "com.samsung.android.smartmanager.ui.WhiteListActivity",
            )
            else -> emptyList()
        }

        for ((pkg, cls) in candidates) {
            val intent = Intent().setComponent(ComponentName(pkg, cls))
            if (intent.resolveActivity(activity.packageManager) != null) {
                runCatching { activity.startActivity(intent) }.onSuccess { return }
            }
        }
        // 兜底：系统设置总页
        runCatching { activity.startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
}
