package com.autotask.app.permission

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * 国产 ROM 自启动 / 权限管理页跳转（M6 + 后台弹出界面适配）。
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

    /**
     * 打开"后台弹出界面"权限页（vivo/OriginOS 独立于悬浮窗的开关，
     * 控制第三方 App 在后台直接拉起 Activity；被拦截时 startActivity 不报错但静默丢弃）。
     * 无法直接定位到单项时打开 vivo 权限管理页，由用户手动开启。
     */
    fun openBackgroundPopupSettings(activity: Activity) {
        val candidates = listOf(
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.PermissionListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.AppManageActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.AppPermissionSettingsActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.AppDetailActivity",
            "com.iqoo.secure" to "com.iqoo.secure.permission.PermissionManageActivity",
        )
        for ((pkg, cls) in candidates) {
            val intent = Intent().setComponent(ComponentName(pkg, cls))
            if (intent.resolveActivity(activity.packageManager) != null) {
                runCatching { activity.startActivity(intent) }.onSuccess { return }
            }
        }
        runCatching { activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${activity.packageName}"))) }
    }
}
