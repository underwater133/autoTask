package com.autotask.app.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * SettingsStore 单元测试：默认值与读写。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsStoreTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun defaults() {
        // 成功震动默认关闭（不打扰），失败震动默认开启（保持既有行为）
        assertFalse(SettingsStore.vibrateOnSuccess(context))
        assertTrue(SettingsStore.vibrateOnFailure(context))
    }

    @Test
    fun setAndRead() {
        SettingsStore.setVibrateOnSuccess(context, true)
        SettingsStore.setVibrateOnFailure(context, false)
        assertTrue(SettingsStore.vibrateOnSuccess(context))
        assertFalse(SettingsStore.vibrateOnFailure(context))

        SettingsStore.setVibrateOnSuccess(context, false)
        SettingsStore.setVibrateOnFailure(context, true)
        assertFalse(SettingsStore.vibrateOnSuccess(context))
        assertTrue(SettingsStore.vibrateOnFailure(context))
    }

    @Test
    fun firstRun_flag() {
        // 默认是首次启动
        assertTrue(SettingsStore.isFirstRun(context))
        SettingsStore.setFirstRunDone(context)
        assertFalse(SettingsStore.isFirstRun(context))
    }
}
