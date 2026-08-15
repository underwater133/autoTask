package com.autotask.app.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.autotask.app.calendar.HolidayCalendar
import com.autotask.app.task.Task
import com.autotask.app.task.TaskDao

/**
 * 调度引擎（M3）：把任务注册到 AlarmManager，并维护 nextTriggerAt。
 *
 * 触发链路：AlarmManager → TaskAlarmReceiver → 执行器（M4）→ 重新调度
 */
object Scheduler {

    const val EXTRA_TASK_ID = "task_id"
    private const val TAG = "AutoTask"

    /** 全量重排：启用的任务注册闹钟，停用的取消 */
    fun rescheduleAll(context: Context) {
        val dao = TaskDao(context)
        dao.getAll().forEach { task ->
            if (task.enabled) schedule(context, task) else cancel(context, task.id)
        }
        // 任务变化后刷新系统通知栏的"即将执行"信息
        com.autotask.app.service.KeeperService.updateTaskNotification(context)
    }

    /** 注册（或更新）一个任务的闹钟，返回下一次触发时间戳 */
    fun schedule(context: Context, task: Task): Long {
        HolidayCalendar.ensureLoaded(context)
        val next = NextTriggerCalculator.compute(task, System.currentTimeMillis(), HolidayCalendar)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, task.id)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        Log.i(TAG, "schedule task=${task.id} next=$next canExact=$canExact")
        if (!canExact) {
            // Android 12+ 未授予"闹钟和提醒"权限时降级为非精确闹钟
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        }
        Log.i(TAG, "alarm set done task=${task.id}")
        if (task.nextTriggerAt != next) {
            TaskDao(context).update(task.copy(nextTriggerAt = next))
        }
        return next
    }

    /** 取消任务的闹钟 */
    fun cancel(context: Context, taskId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, taskId))
    }

    private fun pendingIntent(context: Context, taskId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            Intent(context, TaskAlarmReceiver::class.java).putExtra(EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
