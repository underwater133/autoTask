package com.autotask.app.schedule

import android.app.AlarmManager
import android.content.Context
import com.autotask.app.task.ScheduleMode
import com.autotask.app.task.Task
import com.autotask.app.task.TaskDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Scheduler 运行时测试（Robolectric）：验证 AlarmManager 注册/取消、nextTriggerAt 持久化。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SchedulerTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var dao: TaskDao

    @Before
    fun setUp() {
        dao = TaskDao(context)
        // 允许精确闹钟路径
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    private fun insertTask(mode: ScheduleMode, hour: Int = 8, minute: Int = 30): Task {
        val id = dao.insert(
            Task(
                name = "测试任务",
                targetPackage = "com.example.app",
                scheduleMode = mode,
                hour = hour,
                minute = minute,
                weekDays = if (mode == ScheduleMode.WEEKLY) (1 shl 0) else 0,
                enabled = true,
            )
        )
        return dao.getById(id)!!
    }

    @Test
    fun schedule_registersOneAlarm_andPersistsNextTrigger() {
        val task = insertTask(ScheduleMode.DAILY)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val next = Scheduler.schedule(context, task)

        assertTrue(next > System.currentTimeMillis())
        val alarms = shadowOf(am).scheduledAlarms
        assertEquals(1, alarms.size)
        assertEquals(next, alarms[0].triggerAtTime)
        // 持久化到数据库
        assertEquals(next, dao.getById(task.id)!!.nextTriggerAt)
    }

    @Test
    fun schedule_twice_replacesAlarm() {
        val task = insertTask(ScheduleMode.DAILY)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        Scheduler.schedule(context, task)
        val next2 = Scheduler.schedule(context, task)

        // 同一 PendingIntent 更新，只保留一个闹钟
        assertEquals(1, shadowOf(am).scheduledAlarms.size)
        assertEquals(next2, shadowOf(am).scheduledAlarms[0].triggerAtTime)
    }

    @Test
    fun cancel_removesAlarm() {
        val task = insertTask(ScheduleMode.DAILY)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        Scheduler.schedule(context, task)
        Scheduler.cancel(context, task.id)

        assertTrue(shadowOf(am).scheduledAlarms.isEmpty())
    }

    @Test
    fun rescheduleAll_disabledTask_notScheduled() {
        val task = insertTask(ScheduleMode.DAILY)
        dao.setEnabled(task.id, false)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        Scheduler.rescheduleAll(context)

        assertTrue(shadowOf(am).scheduledAlarms.isEmpty())
    }

    @Test
    fun rescheduleAll_schedulesEnabledTask() {
        insertTask(ScheduleMode.DAILY)
        insertTask(ScheduleMode.WEEKLY, hour = 9)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        Scheduler.rescheduleAll(context)

        assertEquals(2, shadowOf(am).scheduledAlarms.size)
    }
}
