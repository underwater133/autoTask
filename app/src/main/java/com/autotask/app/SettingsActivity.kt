package com.autotask.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.autotask.app.databinding.ActivitySettingsBinding
import com.autotask.app.settings.SettingsStore

/**
 * 设置页：震动提醒开关（成功 / 失败）。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setTitle(R.string.settings_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // 显式绑定返回按钮（部分 ROM 上 supportActionBar 的 home 点击路由失效）
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.swVibrateSuccess.isChecked = SettingsStore.vibrateOnSuccess(this)
        binding.swVibrateFailure.isChecked = SettingsStore.vibrateOnFailure(this)

        binding.swVibrateSuccess.setOnCheckedChangeListener { _, checked ->
            SettingsStore.setVibrateOnSuccess(this, checked)
        }
        binding.swVibrateFailure.setOnCheckedChangeListener { _, checked ->
            SettingsStore.setVibrateOnFailure(this, checked)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
