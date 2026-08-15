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
     * @param requireOverlay 是否要求悬浮窗权限（后台启动必需；前台手动测试时传 false）
     * 返回 ok=false 时 reason 给出失败原因（权限缺失 / 启动异常等）。
     */
    fun launch(context: Context, task: Task, requireOverlay: Boolean = true): LaunchResult {
        val pm = context.packageManager

        if (requireOverlay &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !Settings.canDrawOverlays(context)
        ) {
            return LaunchResult(false, "缺少悬浮窗权限（Android 10+ 后台启动必需）")
        }

        val intent = buildLaunchIntent(pm, task)
            ?: return LaunchResult(false, "无法解析启动入口（应用可能未安装）")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

        return try {
            context.startActivity(intent)
            LaunchResult(true)
        } catch (e: Exception) {
            LaunchResult(false, "启动异常：${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * 构建启动 Intent（三级策略）：
     * 1. 显式组件（已解析出 activity 名时）
     * 2. launcher intent（正常应用）
     * 3. 隐式 + setPackage（vivo 等系统隐藏应用的唯一路径：
     *    查询 API 被系统过滤，但 startActivity 由系统解析，不受影响）
     */
    private fun buildLaunchIntent(pm: PackageManager, task: Task): Intent? {
        if (task.targetActivity.isNotBlank()) {
            return Intent().setClassName(task.targetPackage, task.targetActivity)
        }
        pm.getLaunchIntentForPackage(task.targetPackage)?.let { return it }
        return Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(task.targetPackage)
    }

    /**
     * 解析用户输入的组件字符串。
     * 支持两种格式：
     * - "com.example.app"             → 仅包名（走隐式启动）
     * - "com.example.app/.Main"       → 包名 + 相对 Activity（自动补全为 com.example.app.Main）
     * - "com.example.app/com.example.app.Main" → 包名 + 完整 Activity
     *
     * 显式组件启动不经过 intent 解析，可绕过 vivo 等系统对侧载应用的解析过滤。
     */
    fun parseComponentInput(input: String): Pair<String, String> {
        val trimmed = input.trim()
        val slash = trimmed.indexOf('/')
        return if (slash > 0) {
            val pkg = trimmed.substring(0, slash).trim()
            var act = trimmed.substring(slash + 1).trim()
            if (act.startsWith(".")) act = pkg + act
            pkg to act
        } else {
            trimmed to ""
        }
    }
}
