package com.autotask.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.autotask.app.databinding.ActivityPermissionBinding
import com.autotask.app.databinding.ItemPermissionBinding
import com.autotask.app.permission.GuidanceCatalog
import com.autotask.app.permission.PermissionCatalog

/**
 * 权限引导页（M1 + M6）：权限状态检测 + 系统引导（防清理）。
 * 从系统设置返回时 onResume 自动刷新状态。
 */
class PermissionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setTitle(R.string.perm_page_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        binding.container.removeAllViews()

        // 1) 可检测的权限项
        PermissionCatalog.all(this).forEach { item ->
            val row = ItemPermissionBinding.inflate(layoutInflater, binding.container, false)
            row.tvTitle.text = item.title
            row.tvDesc.text = item.description
            val granted = item.isGranted(this)
            row.tvStatus.text = getString(
                if (granted) R.string.perm_status_granted else R.string.perm_status_missing
            )
            row.tvStatus.setTextColor(
                getColor(if (granted) R.color.status_granted else R.color.status_missing)
            )
            row.btnAction.text = getString(
                if (granted) R.string.perm_action_done else R.string.perm_action_setting
            )
            row.btnAction.isEnabled = !granted
            row.btnAction.setOnClickListener { item.action(this) }
            binding.container.addView(row.root)
        }

        // 2) 系统引导区（不可自动检测）
        binding.container.addView(sectionHeader(R.string.perm_guide_title))
        GuidanceCatalog.all(this).forEach { item ->
            val row = ItemPermissionBinding.inflate(layoutInflater, binding.container, false)
            row.tvTitle.text = item.title
            row.tvDesc.text = item.description
            row.tvStatus.text = getString(R.string.guide_status_advice)
            row.tvStatus.setTextColor(getColor(R.color.status_advice))
            row.btnAction.text = item.actionLabel
            row.btnAction.isEnabled = true
            row.btnAction.setOnClickListener { item.action(this) }
            binding.container.addView(row.root)
        }
    }

    private fun sectionHeader(titleRes: Int): View {
        val tv = TextView(this)
        tv.text = getString(titleRes)
        tv.setTextColor(getColor(R.color.status_advice))
        tv.textSize = 14f
        tv.setPadding(12, 24, 12, 8)
        return tv
    }
}
