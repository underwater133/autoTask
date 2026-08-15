package com.autotask.app.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.autotask.app.service.KeeperService

/**
 * 开机 / 时间变更恢复（M3 + M6）：
 * 重启、App 更新、时间/时区变更后全量重建闹钟，并拉起常驻服务。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                Scheduler.rescheduleAll(context)
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, KeeperService::class.java)
                )
            }
        }
    }
}
