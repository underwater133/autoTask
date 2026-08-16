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

    @Test
    fun rescheduleAll_restoresLostAlarms() {
        // 模拟"闹钟被系统清掉"（vivo 速冻/清理）：任务启用但无闹钟注册
        val task = insertTask(ScheduleMode.DAILY)
        Scheduler.schedule(context, task)
        Scheduler.cancel(context, task.id) // 清掉闹钟（模拟被系统清理）
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        assertTrue(shadowOf(am).scheduledAlarms.isEmpty())

        // 打开 app（onResume）重排后闹钟恢复
        Scheduler.rescheduleAll(context)
        assertEquals("闹钟丢失后应恢复注册", 1, shadowOf(am).scheduledAlarms.size)
    }

    @Test
    fun rescheduleAll_onceTaskPastDue_rollsForward() {
        // 单次任务时间已过且闹钟丢失：重排后自动顺延到下一次（不永久丢失）
        val id = dao.insert(
            Task(
                name = "过期单次",
                targetPackage = "com.example.app",
                scheduleMode = ScheduleMode.ONCE,
                hour = 6,
                minute = 30,
                enabled = true,
                nextTriggerAt = 0, // 闹钟丢失后 nextTriggerAt 未知
            )
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        Scheduler.rescheduleAll(context)

        val alarms = shadowOf(am).scheduledAlarms
        assertEquals(1, alarms.size)
        assertTrue("过期单次任务应顺延到未来", alarms[0].triggerAtTime > System.currentTimeMillis())
        val updated = dao.getById(id)!!
        assertTrue("nextTriggerAt 应更新为未来", updated.nextTriggerAt > System.currentTimeMillis())
    }
}
