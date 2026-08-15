package com.autotask.app.executor

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.autotask.app.task.Task

/**
 * 启动结果。
 */
data class LaunchResult(val ok: Boolean, val reason: String = "")

/**
 * 应用拉起器（M4）。
 *
 * 关键点：Android 10+ 从后台 startActivity 会被系统拦截，
 * 必须持有悬浮窗权限（SYSTEM_ALERT_WINDOW）才能豁免。
 */
object AppLauncher {

    /**
     * 尝试启动目标 App。
     * 返回 ok=false 时 reason 给出失败原因（未安装 / 权限缺失 / 启动异常）。
     */
    fun launch(context: Context, task: Task): LaunchResult {
        val pm = context.packageManager

        if (!isAppInstalled(pm, task.targetPackage)) {
            return LaunchResult(false, "目标应用未安装")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !Settings.canDrawOverlays(context)) {
            return LaunchResult(false, "缺少悬浮窗权限（Android 10+ 后台启动必需）")
        }

        val intent = if (task.targetActivity.isNotBlank()) {
            Intent().setClassName(task.targetPackage, task.targetActivity)
        } else {
            pm.getLaunchIntentForPackage(task.targetPackage)
                ?: return LaunchResult(false, "无法获取应用启动入口")
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

        return try {
            context.startActivity(intent)
            LaunchResult(true)
        } catch (e: Exception) {
            LaunchResult(false, "启动异常：${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun isAppInstalled(pm: PackageManager, packageName: String): Boolean =
        runCatching { pm.getApplicationInfo(packageName, 0) }.isSuccess
}
