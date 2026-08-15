package com.autotask.app.settings

import android.content.Context

/**
 * 全局设置存储（SharedPreferences，轻量无依赖）。
 */
object SettingsStore {

    private const val PREFS = "settings"
    private const val KEY_VIBRATE_ON_SUCCESS = "vibrate_on_success"
    private const val KEY_VIBRATE_ON_FAILURE = "vibrate_on_failure"

    /** 执行成功时震动（默认关闭，避免打扰） */
    fun vibrateOnSuccess(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VIBRATE_ON_SUCCESS, false)

    fun setVibrateOnSuccess(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VIBRATE_ON_SUCCESS, enabled).apply()
    }

    /** 重试达到最大次数（放弃）时震动（默认开启，保持既有行为） */
    fun vibrateOnFailure(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VIBRATE_ON_FAILURE, true)

    fun setVibrateOnFailure(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VIBRATE_ON_FAILURE, enabled).apply()
    }

    private fun prefs(context: Context): android.content.SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
