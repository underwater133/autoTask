package com.autotask.app

import android.content.Intent
import android.widget.Spinner
import androidx.appcompat.widget.SwitchCompat
import com.autotask.app.task.ScheduleMode
import com.autotask.app.task.Task
import com.autotask.app.task.TaskDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 回归测试：新建任务默认启用；编辑已有任务时调度模式正确回显。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskEditActivityTest {

    @Test
    fun newTask_switchEnabledByDefault() {
        val activity = Robolectric.buildActivity(TaskEditActivity::class.java).setup().get()
        val sw = activity.findViewById<SwitchCompat>(R.id.swEnabled)
        assertTrue("新建任务应默认启用", sw.isChecked)
    }

    @Test
    fun editTask_modeSpinnerReflectsStoredMode() {
        // 曾因 loadTask 在 Spinner adapter 初始化前调用 setSelection，导致模式总显示默认项（单次）
        val dao = TaskDao(RuntimeEnvironment.getApplication())
        val id = dao.insert(
            Task(
                name = "工作日任务",
                targetPackage = "com.example.app",
                scheduleMode = ScheduleMode.WORKDAY,
                hour = 9,
                minute = 0,
            )
        )

        val intent = Intent(RuntimeEnvironment.getApplication(), TaskEditActivity::class.java)
            .putExtra("task_id", id)
        val activity = Robolectric.buildActivity(TaskEditActivity::class.java, intent).setup().get()

        val spinner = activity.findViewById<Spinner>(R.id.spMode)
        assertEquals("编辑任务应回显已设置的调度模式", ScheduleMode.WORKDAY.ordinal, spinner.selectedItemPosition)
    }
}
