package com.autotask.app.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.autotask.app.MainActivity
import com.autotask.app.R

/**
 * 常驻前台服务（M6）：提升进程存活率，防止后台清理。
 * - Android 14 起必须声明 foregroundServiceType=dataSync
 * - START_STICKY：被系统杀掉后尽力重建
 * - 通知使用低优先级渠道，尽量不打扰用户
 */
class KeeperService : Service() {

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 服务被系统重建（START_STICKY）时恢复闹钟：
        // 部分 ROM 的后台管理（速冻/清理）会清掉第三方应用的闹钟
        runCatching { com.autotask.app.schedule.Scheduler.rescheduleAll(this) }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.keeper_channel_name),
            NotificationManager.IMPORTANCE_MIN // 低优先级，不响铃不打扰
        )
        channel.description = getString(R.string.keeper_channel_desc)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(getString(R.string.keeper_notif_title))
            .setContentText(getString(R.string.keeper_notif_text))
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "keeper"
        private const val NOTIFICATION_ID = 1

        /** 服务是否正在运行 */
        fun isRunning(context: Context): Boolean {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            return am.getRunningServices(200)
                .any { it.service.className == KeeperService::class.java.name }
        }

        /**
         * 刷新常驻通知内容：
         * 悬浮窗权限已开启且存在启用任务时，显示"即将执行"的任务详情；
         * 否则显示默认文案。
         */
        fun updateTaskNotification(context: Context) {
            val overlayGranted = com.autotask.app.permission.PermissionHelper.isOverlayGranted(context)
            val next = if (overlayGranted) {
                com.autotask.app.task.TaskFormat.nextUpTask(
                    com.autotask.app.task.TaskDao(context).getEnabled()
                )
            } else {
                null
            }
            val title: String
            val text: String
            if (next != null) {
                title = context.getString(R.string.keeper_notif_next_title, next.name)
                val time = java.text.SimpleDateFormat(
                    "MM-dd HH:mm", java.util.Locale.getDefault()
                ).format(java.util.Date(next.nextTriggerAt))
                val target = com.autotask.app.task.TaskFormat.appLabel(context, next.targetPackage)
                text = context.getString(
                    R.string.keeper_notif_next_text,
                    time, com.autotask.app.task.TaskFormat.summary(next), target
                )
            } else {
                title = context.getString(R.string.keeper_notif_title)
                text = context.getString(R.string.keeper_notif_text)
            }
            val pi = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build()
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            try {
                nm.notify(NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {
                // 通知权限未授予：前台服务通知由系统兜底展示，忽略
            }
        }
    }
}
