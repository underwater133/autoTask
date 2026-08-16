package com.autotask.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.autotask.app.databinding.ActivityMainBinding
import com.autotask.app.databinding.ItemTaskBinding
import com.autotask.app.schedule.Scheduler
import com.autotask.app.service.KeeperService
import com.autotask.app.settings.SettingsStore
import com.autotask.app.task.Task
import com.autotask.app.task.TaskDao
import com.autotask.app.task.TaskFormat

/**
 * 主界面：任务列表（M2）。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dao: TaskDao
    private var adapter: TaskAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setTitle(R.string.task_list_title)

        dao = TaskDao(this)

        // 拉起常驻前台服务（M6，防后台清理）
        ContextCompat.startForegroundService(this, Intent(this, KeeperService::class.java))

        binding.recycler.layoutManager = LinearLayoutManager(this)
        adapter = TaskAdapter(
            onToggle = { task, checked ->
                dao.setEnabled(task.id, checked)
                Scheduler.rescheduleAll(this)
                refresh()
            },
            onClick = { task ->
                startActivity(TaskEditActivity.intent(this, task.id))
            }
        )
        binding.recycler.adapter = adapter

        binding.fabAdd.setOnClickListener {
            startActivity(TaskEditActivity.intent(this, TaskEditActivity.NEW_TASK_ID))
        }

        // 首次启动标记（实际请求在 onResume 执行，确保系统弹窗时机正确）
        if (SettingsStore.isFirstRun(this)) {
            SettingsStore.setFirstRunDone(this)
            pendingFirstRunPermissions = true
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        // 全量重建闹钟：部分 ROM（vivo 速冻/一键清理）会清掉第三方应用的闹钟，
        // 打开 app 时恢复所有启用任务的调度（幂等，已过期的单次任务自动顺延）
        Scheduler.rescheduleAll(this)
        // 常驻通知刷新：有启用任务时在系统通知栏显示"即将执行"详情
        KeeperService.updateTaskNotification(this)
        if (pendingFirstRunPermissions) {
            pendingFirstRunPermissions = false
            requestNotificationPermission()
            requestBatteryOptimizationExemption()
        }
    }

    private fun refresh() {
        adapter?.submit(dao.getAll())
    }

    private var pendingFirstRunPermissions = false

    /** 首次启动：自动请求需要的所有权限（在 onResume 中执行，弹窗时机可靠） */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    private fun requestBatteryOptimizationExemption() {
        if (com.autotask.app.permission.PermissionHelper.isBatteryOptimizationExempt(this)) return
        val intent = Intent(
            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            android.net.Uri.parse("package:$packageName")
        )
        if (intent.resolveActivity(packageManager) != null) {
            runCatching { startActivity(intent) }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_LOGS, 0, R.string.menu_logs)
        menu.add(Menu.NONE, MENU_PERMISSIONS, 1, R.string.menu_permissions)
        menu.add(Menu.NONE, MENU_SETTINGS, 2, R.string.menu_settings)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            MENU_PERMISSIONS -> {
                startActivity(Intent(this, PermissionActivity::class.java))
                return true
            }
            MENU_LOGS -> {
                startActivity(Intent(this, LogActivity::class.java))
                return true
            }
            MENU_SETTINGS -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val MENU_PERMISSIONS = 1
        private const val MENU_LOGS = 2
        private const val MENU_SETTINGS = 3
    }
}

/**
 * 任务列表适配器：名称 + 调度摘要 + 启用开关。
 */
private class TaskAdapter(
    private val onToggle: (Task, Boolean) -> Unit,
    private val onClick: (Task) -> Unit,
) : RecyclerView.Adapter<TaskAdapter.VH>() {

    private val items = mutableListOf<Task>()

    fun submit(list: List<Task>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(private val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(task: Task) {
            binding.tvName.text = task.name
            binding.tvSummary.text = TaskFormat.summary(task)
            binding.tvTarget.text = TaskFormat.appLabel(binding.root.context, task.targetPackage)
            binding.swEnabled.setOnCheckedChangeListener(null)
            binding.swEnabled.isChecked = task.enabled
            binding.swEnabled.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                onToggle(task, checked)
            }
            binding.root.setOnClickListener { onClick(task) }
        }
    }
}
