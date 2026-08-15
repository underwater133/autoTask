package com.autotask.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.autotask.app.databinding.ActivityMainBinding
import com.autotask.app.databinding.ItemTaskBinding
import com.autotask.app.schedule.Scheduler
import com.autotask.app.service.KeeperService
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
    }

    override fun onResume() {
        super.onResume()
        refresh()
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

    private fun refresh() {
        adapter?.submit(dao.getAll())
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
