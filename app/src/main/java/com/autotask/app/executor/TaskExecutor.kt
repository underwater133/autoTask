package com.autotask.app.executor

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import com.autotask.app.log.AppLogger
import com.autotask.app.permission.PermissionHelper
import com.autotask.app.schedule.Scheduler
import com.autotask.app.schedule.TaskRetryReceiver
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
    private const val VERIFY_TIMEOUT_MS = 5_000L

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
                AppLogger.log(appContext, "【${task.name}】目标应用已进入前台，执行成功")
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
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (ForegroundDetector.isAppInForeground(context, packageName)) return true
            Thread.sleep(500)
        }
        return false
    }

    private fun handleFailure(context: Context, task: Task, attempt: Int, reason: String) {
        val nextAttempt = attempt + 1
        if (nextAttempt > RetryPolicy.MAX_ATTEMPTS) {
            AppLogger.log(context, "【${task.name}】已达最大尝试次数 ${RetryPolicy.MAX_ATTEMPTS}，放弃。原因：$reason")
            // 达到失败次数：震动提醒用户（双段脉冲）
            vibrateFailure(context)
            onTerminal(context, task, success = false)
        } else {
            val delay = RetryPolicy.delayForAttempt(attempt)
            AppLogger.log(context, "【${task.name}】${delay / 1000} 秒后进行第 $nextAttempt 次重试")
            scheduleRetry(context, task, nextAttempt, delay)
        }
    }

    /** 失败震动提醒：300ms 双脉冲（震-停-震） */
    private fun vibrateFailure(context: Context) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!vibrator.hasVibrator()) return
        val pattern = longArrayOf(0, 300, 200, 300)
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
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
        am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, pi)
    }

    /** 终态：单次任务停用 + 全量重排下一次触发 */
    private fun onTerminal(context: Context, task: Task, success: Boolean) {
        val dao = TaskDao(context)
        if (task.scheduleMode == ScheduleMode.ONCE) {
            dao.setEnabled(task.id, false)
            AppLogger.log(context, "【${task.name}】单次任务已${if (success) "完成" else "结束"}并停用")
        }
        Scheduler.rescheduleAll(context)
    }
}
