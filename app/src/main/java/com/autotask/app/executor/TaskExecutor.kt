package com.autotask.app.executor

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.autotask.app.log.AppLogger
import com.autotask.app.permission.PermissionHelper
import com.autotask.app.schedule.Scheduler
import com.autotask.app.schedule.TaskRetryReceiver
import com.autotask.app.settings.SettingsStore
import com.autotask.app.task.ScheduleMode
import com.autotask.app.task.Task
import com.autotask.app.task.TaskDao

/**
 * 任务执行器（M4）：拉起目标 App → 前台验证 → 失败重试。
 *
 * 执行链路：
 *   TaskAlarmReceiver → execute() → 第 1 次尝试
 *   失败 → AlarmManager 延时 → TaskRetryReceiver → attempt() 第 2/3 次
 *   终态（成功 / 放弃）→ 单次任务停用 + 全量重排下一次
 */
object TaskExecutor {

    const val EXTRA_ATTEMPT = "attempt"
    private const val VERIFY_TIMEOUT_MS = 8_000L

    /** 由 TaskAlarmReceiver 在触发时调用 */
    fun execute(context: Context, task: Task) {
        attempt(context, task, 1)
    }

    /** 执行第 attempt 次尝试（1 起） */
    fun attempt(context: Context, task: Task, attempt: Int) {
        val launch = AppLauncher.launch(context, task)
        if (launch.ok) {
            AppLogger.log(context, "【${task.name}】第 $attempt 次启动成功，验证前台…")
            verifyAsync(context, task, attempt)
        } else {
            AppLogger.log(context, "【${task.name}】第 $attempt 次启动失败：${launch.reason}")
            handleFailure(context, task, attempt, launch.reason)
        }
    }

    /** 后台线程轮询前台状态（BroadcastReceiver 需快速返回） */
    private fun verifyAsync(context: Context, task: Task, attempt: Int) {
        val appContext = context.applicationContext
        Thread {
            val inForeground = waitForForeground(appContext, task.targetPackage, VERIFY_TIMEOUT_MS)
            if (inForeground) {
                AppLogger.log(appContext, "【${task.name}】目标应用已启动，执行成功")
                onTerminal(appContext, task, success = true)
            } else {
                val reason = if (PermissionHelper.isUsageAccessGranted(appContext)) {
                    "前台验证超时（${VERIFY_TIMEOUT_MS / 1000} 秒内未检测到目标应用）"
                } else {
                    "无法验证前台（未授予使用情况访问权限，按启动成功处理）"
                }
                AppLogger.log(appContext, "【${task.name}】$reason")
                if (reason.startsWith("无法验证")) {
                    // 无使用情况访问权限时无法验证，视为成功，避免误判重试
                    onTerminal(appContext, task, success = true)
                } else {
                    handleFailure(appContext, task, attempt, reason)
                }
            }
        }.apply {
            name = "autoTask-verify"
            start()
        }
    }

    private fun waitForForeground(context: Context, packageName: String, timeoutMs: Long): Boolean {
        if (!PermissionHelper.isUsageAccessGranted(context)) return true // 无法验证 → 视为成功
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (!pm.isInteractive) {
            // 屏幕关闭：前台事件不会产生（app 无法"显示"）。
            // 轮询等待目标进程出现（startActivity 是异步的，进程可能还在创建）；
            // 轮询超时仍检测不到时信任启动（启动调用已成功）：
            // vivo 可能过滤隐藏应用的进程查询导致假阴性，而假阴性会触发无谓重试。
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (isProcessRunning(context, packageName)) return true
                Thread.sleep(500)
            }
            return true
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (ForegroundDetector.isAppInForeground(context, packageName)) return true
            Thread.sleep(500)
        }
        // 兜底：UsageStats 在部分 ROM 上记录不可靠（如 vivo 隐藏应用/后台场景），
        // 若目标应用进程已被拉起，视为启动成功（防止误判失败导致无谓重试）
        return isProcessRunning(context, packageName)
    }

    /** 目标应用进程是否存活（ActivityManager 查询，不受 ROM 包查询过滤影响） */
    private fun isProcessRunning(context: Context, packageName: String): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        return am.getRunningAppProcesses()
            .any { it.processName == packageName || it.processName.startsWith("$packageName:") }
    }

    private fun handleFailure(context: Context, task: Task, attempt: Int, reason: String) {
        val nextAttempt = attempt + 1
        if (nextAttempt > RetryPolicy.MAX_ATTEMPTS) {
            AppLogger.log(context, "【${task.name}】已达最大尝试次数 ${RetryPolicy.MAX_ATTEMPTS}，放弃。原因：$reason")
            onTerminal(context, task, success = false)
        } else {
            val delay = RetryPolicy.delayForAttempt(attempt)
            AppLogger.log(context, "【${task.name}】${delay / 1000} 秒后进行第 $nextAttempt 次重试")
            scheduleRetry(context, task, nextAttempt, delay)
        }
    }

    private fun scheduleRetry(context: Context, task: Task, attempt: Int, delayMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context,
            task.id.toInt(),
            Intent(context, TaskRetryReceiver::class.java)
                .putExtra(Scheduler.EXTRA_TASK_ID, task.id)
                .putExtra(EXTRA_ATTEMPT, attempt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + delayMs
        // 必须用精确闹钟：非精确闹钟在 Doze 下会被挂起到设备唤醒（实测延迟数分钟），
        // 导致重试形同虚设；精确闹钟至少会在 Doze 维护窗口内触发
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    /** 终态：按用户设置震动提醒 + 单次任务停用 + 全量重排下一次触发 */
    private fun onTerminal(context: Context, task: Task, success: Boolean) {
        if (success) {
            if (SettingsStore.vibrateOnSuccess(context)) vibrateSuccess(context)
        } else {
            if (SettingsStore.vibrateOnFailure(context)) vibrateFailure(context)
        }
        val dao = TaskDao(context)
        if (task.scheduleMode == ScheduleMode.ONCE) {
            dao.setEnabled(task.id, false)
            AppLogger.log(context, "【${task.name}】单次任务已${if (success) "完成" else "结束"}并停用")
        }
        Scheduler.rescheduleAll(context)
    }

    /** 成功震动提醒：单脉冲 200ms */
    private fun vibrateSuccess(context: Context) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** 失败震动提醒：300ms 双脉冲（震-停-震） */
    private fun vibrateFailure(context: Context) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!vibrator.hasVibrator()) return
        val pattern = longArrayOf(0, 300, 200, 300)
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}
