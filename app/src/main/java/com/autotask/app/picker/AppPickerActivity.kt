package com.autotask.app.picker

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.autotask.app.R
import com.autotask.app.databinding.ActivityAppPickerBinding
import com.autotask.app.databinding.ItemAppBinding
import com.autotask.app.databinding.ItemAppGroupBinding

/**
 * 应用选择器（M2 增强）：全量已安装应用（getInstalledApplications），
 * 按拼音首字母分组排序（通讯录式 + 右侧索引条），支持按名称/拼音搜索。
 */
class AppPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppPickerBinding
    private lateinit var adapter: AppAdapter
    private var allEntries: List<AppEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setTitle(R.string.app_picker_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = AppAdapter { entry -> onEntryClick(entry) }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                render(s?.toString().orEmpty())
            }
        })

        binding.btnClear.setOnClickListener { binding.etSearch.text?.clear() }

        setupIndexBar()

        allEntries = loadApps()
        render("")
    }

    /**
     * 扫描应用：只保留有启动入口（launcher）的应用，排除纯系统服务组件。
     * 使用最大包容 flag 组合，尽量包含停用/隐藏的应用（如被系统停用的 QQ）。
     */
    private fun loadApps(): List<AppEntry> {
        val pm = packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        var queryFlags = PackageManager.MATCH_DISABLED_COMPONENTS or
            PackageManager.MATCH_UNINSTALLED_PACKAGES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            queryFlags = queryFlags or PackageManager.MATCH_ALL
        }
        val launchInfos = pm.queryIntentActivities(launcherIntent, queryFlags)
        val activityByPackage = mutableMapOf<String, String>()
        for (info in launchInfos) {
            activityByPackage.putIfAbsent(info.activityInfo.packageName, info.activityInfo.name)
        }

        // 兜底：部分 ROM 会从批量查询中过滤停用/隐藏的应用（如被停用的 QQ、飞书），
        // 对白名单外的包逐个用 resolveActivity 探测（resolveActivity 不受该过滤影响）
        val allInstalled = pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
        allInstalled
            .filter { it.packageName != packageName && it.packageName !in activityByPackage }
            .forEach { ai ->
                val probe = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setPackage(ai.packageName)
                val resolved = runCatching { pm.resolveActivity(probe, queryFlags) }.getOrNull()
                val cls = resolved?.activityInfo?.name
                if (cls != null) {
                    activityByPackage.putIfAbsent(ai.packageName, cls)
                }
            }

        return pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
            .mapNotNull { ai ->
                if (ai.packageName == packageName) return@mapNotNull null // 排除自身
                // 无启动入口的系统服务/组件类应用不显示
                val activityName = activityByPackage[ai.packageName] ?: return@mapNotNull null
                val label = runCatching { ai.loadLabel(pm).toString() }
                    .getOrElse { ai.packageName }
                val enabledSetting = pm.getApplicationEnabledSetting(ai.packageName)
                val enabled = enabledSetting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                    enabledSetting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                AppEntry(
                    packageName = ai.packageName,
                    activityName = activityName,
                    label = label,
                    groupLetter = AppPinyin.groupLetter(label),
                    pinyinKey = AppPinyin.fullPinyin(label),
                    launchable = enabled,
                    enabled = enabled,
                    icon = runCatching { pm.getApplicationIcon(ai.packageName) }.getOrNull(),
                )
            }
            .sortedWith(
                compareBy<AppEntry> { it.groupLetter }
                    .thenBy { it.pinyinKey }
                    .thenBy { it.label }
                    .thenBy { it.packageName }
            )
    }

    private fun render(query: String) {
        val q = query.trim()
        val filtered = if (q.isEmpty()) {
            allEntries
        } else {
            val ql = q.lowercase()
            allEntries.filter {
                it.label.contains(q, ignoreCase = true) ||
                    it.pinyinKey.contains(ql) ||
                    (q.length == 1 && it.groupLetter == q.uppercase())
            }
        }
        adapter.submit(buildRows(filtered))
        binding.tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    /** 展开为"分组头 + 应用行"的扁平列表 */
    private fun buildRows(entries: List<AppEntry>): List<Any> {
        val rows = mutableListOf<Any>()
        var lastLetter: String? = null
        for (e in entries) {
            if (e.groupLetter != lastLetter) {
                rows.add(GroupHeader(e.groupLetter))
                lastLetter = e.groupLetter
            }
            rows.add(e)
        }
        return rows
    }

    private fun onEntryClick(entry: AppEntry) {
        if (!entry.launchable) {
            val msg = if (entry.enabled) {
                R.string.app_picker_not_launchable
            } else {
                R.string.app_picker_disabled
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            return
        }
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(EXTRA_PACKAGE, entry.packageName)
                .putExtra(EXTRA_ACTIVITY, entry.activityName)
        )
        finish()
    }

    private fun setupIndexBar() {
        binding.indexBar.removeAllViews()
        val letters = ('A'..'Z').map { it.toString() } + "#"
        val density = resources.displayMetrics.density
        for (letter in letters) {
            val tv = TextView(this).apply {
                text = letter
                textSize = 11f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 0, 0, 0)
                setOnClickListener { scrollToLetter(letter) }
            }
            binding.indexBar.addView(
                tv,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun scrollToLetter(letter: String) {
        val pos = adapter.findFirstPositionOf(letter)
        if (pos >= 0) binding.recycler.scrollToPosition(pos)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_ACTIVITY = "extra_activity"
        private const val TAG = "AppPicker"
    }
}

/** 已安装应用条目 */
data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val groupLetter: String,
    val pinyinKey: String,
    val launchable: Boolean,
    val enabled: Boolean,
    val icon: Drawable?,
)

/** 分组头 */
data class GroupHeader(val letter: String)

/**
 * 适配器：分组头 + 应用行。
 */
private class AppAdapter(
    private val onClick: (AppEntry) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val rows = mutableListOf<Any>()

    fun submit(newRows: List<Any>) {
        rows.clear()
        rows.addAll(newRows)
        notifyDataSetChanged()
    }

    fun findFirstPositionOf(letter: String): Int =
        rows.indexOfFirst { it is GroupHeader && it.letter == letter }

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is GroupHeader) TYPE_HEADER else TYPE_APP

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == TYPE_HEADER) {
            HeaderVH(ItemAppGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        } else {
            AppVH(ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is GroupHeader -> (holder as HeaderVH).bind(row)
            is AppEntry -> (holder as AppVH).bind(row, onClick)
        }
    }

    class HeaderVH(private val binding: ItemAppGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(header: GroupHeader) {
            binding.tvLetter.text = header.letter
        }
    }

    class AppVH(private val binding: ItemAppBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: AppEntry, onClick: (AppEntry) -> Unit) {
            binding.tvBadge.text = entry.groupLetter
            binding.ivIcon.setImageDrawable(entry.icon)
            binding.tvName.text = entry.label
            binding.tvPackage.text = entry.packageName
            val dimmed = !entry.launchable
            binding.root.alpha = if (dimmed) 0.45f else 1f
            if (!entry.enabled) {
                binding.tvName.text = "${entry.label}（已停用）"
            }
            binding.root.setOnClickListener { onClick(entry) }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_APP = 1
    }
}
