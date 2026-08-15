package com.autotask.app.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autotask.app.executor.TaskExecutor
import com.autotask.app.log.AppLogger
import com.autotask.app.task.TaskDao

/**
 * 任务闹钟接收器（M4）：到点触发 → 执行器（拉起 + 验证 + 重试）。
 */
class TaskAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(Scheduler.EXTRA_TASK_ID, -1L)
        if (taskId < 0L) return

        val task = TaskDao(context).getById(taskId) ?: return
        if (!task.enabled) return

        AppLogger.log(context, "═══ 任务触发【${task.name}】计划时间 ${task.timeText()} ═══")
        TaskExecutor.execute(context, task)
    }
}
