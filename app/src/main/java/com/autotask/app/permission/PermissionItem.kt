package com.autotask.app.permission

import android.app.Activity
import android.content.Context

/**
 * 一条权限引导项：标题/说明 + 检测函数 + 处理动作（跳设置 / 启动服务等）。
 */
data class PermissionItem(
    val title: String,
    val description: String,
    val isGranted: (Context) -> Boolean,
    val action: (Activity) -> Unit,
)

/**
 * 一条系统引导项（不可自动检测，如厂商自启动 / 最近任务锁定）。
 */
data class GuidanceItem(
    val title: String,
    val description: String,
    val actionLabel: String,
    val action: (Activity) -> Unit,
)
