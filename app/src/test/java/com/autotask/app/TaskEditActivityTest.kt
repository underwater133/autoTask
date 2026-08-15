package com.autotask.app

import androidx.appcompat.widget.SwitchCompat
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 回归测试：新建任务默认启用。
 * （曾因布局开关默认未勾选、新建模式未初始化，导致新任务保存后处于停用状态不触发）
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
}
