package com.autotask.app.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autotask.app.executor.TaskExecutor
import com.autotask.app.task.TaskDao

/**
 * 重试闹钟接收器（M4）：收到延时广播后执行下一次尝试。
 * 任务被停用或删除时静默丢弃。
 */
class TaskRetryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(Scheduler.EXTRA_TASK_ID, -1L)
        val attempt = intent.getIntExtra(TaskExecutor.EXTRA_ATTEMPT, 1)
        if (taskId < 0L) return

        val task = TaskDao(context).getById(taskId) ?: return
        if (!task.enabled) return

        TaskExecutor.attempt(context, task, attempt)
    }
}
