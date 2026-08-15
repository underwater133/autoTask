package com.autotask.app

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.autotask.app.databinding.ActivityTaskEditBinding
import com.autotask.app.picker.AppPickerActivity
import com.autotask.app.schedule.Scheduler
import com.autotask.app.task.ScheduleMode
import com.autotask.app.task.Task
import com.autotask.app.task.TaskDao
import com.autotask.app.task.TaskFormat

/**
 * 任务编辑页（M2）：名称 / 目标应用 / 调度模式 / 时间 / 星期 / 启用。
 */
class TaskEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskEditBinding
    private lateinit var dao: TaskDao

    private var taskId: Long = NEW_TASK_ID
    private var targetPackage: String = ""
    private var targetActivity: String = ""
    private var hour: Int = 8
    private var minute: Int = 0

    /** 应用选择器（全量应用 + 拼音排序 + 搜索） */
    private val appPickerLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val pkg = result.data?.getStringExtra(AppPickerActivity.EXTRA_PACKAGE) ?: return@registerForActivityResult
                val act = result.data?.getStringExtra(AppPickerActivity.EXTRA_ACTIVITY).orEmpty()
                targetPackage = pkg
                targetActivity = act
                renderTarget()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        dao = TaskDao(this)
        taskId = intent.getLongExtra(EXTRA_TASK_ID, NEW_TASK_ID)

        if (taskId == NEW_TASK_ID) {
            supportActionBar?.setTitle(R.string.task_new)
            binding.btnDelete.visibility = View.GONE
            // 新任务默认启用（布局中开关默认未勾选，必须显式初始化）
            binding.swEnabled.isChecked = true
        } else {
            supportActionBar?.setTitle(R.string.task_edit)
            loadTask(taskId)
        }

        setupModeSpinner()
        setupListeners()
        renderTime()
        renderWeekdays()
    }

    private fun loadTask(id: Long) {
        val task = dao.getById(id) ?: return
        binding.etName.setText(task.name)
        targetPackage = task.targetPackage
        targetActivity = task.targetActivity
        hour = task.hour
        minute = task.minute
        binding.spMode.setSelection(task.scheduleMode.ordinal)
        for (i in 0..6) {
            binding.weekdayBoxes()[i].isChecked = task.weekDays and (1 shl i) != 0
        }
        binding.swEnabled.isChecked = task.enabled
        renderTarget()
        renderTime()
        renderWeekdays()
    }

    private fun setupModeSpinner() {
        val labels = ScheduleMode.entries.map { TaskFormat.modeLabel(it) }
        binding.spMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
            .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spMode.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long,
            ) {
                renderWeekdays()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun setupListeners() {
        binding.btnPickTarget.setOnClickListener {
            appPickerLauncher.launch(Intent(this, AppPickerActivity::class.java))
        }
        binding.btnInputPackage.setOnClickListener { showPackageInputDialog() }
        binding.btnPickTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                hour = h
                minute = m
                renderTime()
            }, hour, minute, true).show()
        }
        binding.btnSave.setOnClickListener { save() }
        binding.btnDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(R.string.task_delete_confirm)
                .setPositiveButton(R.string.task_delete) { _, _ ->
                    dao.delete(taskId)
                    Scheduler.rescheduleAll(this@TaskEditActivity)
                    finish()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    /**
     * 手动输入包名：vivo 等系统的隐藏应用无法在列表中显示，
     * 可通过包名直接作为任务目标（启动由系统解析，不受隐藏过滤影响）。
     */
    private fun showPackageInputDialog() {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.task_target_pkg_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.task_target_input_pkg)
            .setMessage(R.string.task_target_pkg_desc)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val pkg = input.text?.toString()?.trim().orEmpty()
                if (pkg.isEmpty() || !pkg.contains(".")) {
                    Toast.makeText(this, R.string.task_target_pkg_invalid, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                targetPackage = pkg
                targetActivity = ""
                renderTarget()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun renderTarget() {
        binding.tvTarget.text = if (targetPackage.isEmpty()) {
            getString(R.string.task_target_empty)
        } else {
            // 隐藏应用解析不到 label 时直接显示包名
            runCatching {
                packageManager.getApplicationInfo(targetPackage, 0)
                    .loadLabel(packageManager).toString()
            }.getOrElse { targetPackage }
        }
    }

    private fun renderTime() {
        binding.tvTime.text = String.format("%02d:%02d", hour, minute)
    }

    private fun renderWeekdays() {
        val isWeekly = currentMode() == ScheduleMode.WEEKLY
        binding.llWeekdays.visibility = if (isWeekly) View.VISIBLE else View.GONE
    }

    private fun currentMode(): ScheduleMode = ScheduleMode.entries[binding.spMode.selectedItemPosition]

    private fun save() {
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.task_validate_name, Toast.LENGTH_SHORT).show()
            return
        }
        if (targetPackage.isEmpty()) {
            Toast.makeText(this, R.string.task_validate_target, Toast.LENGTH_SHORT).show()
            return
        }
        val mode = currentMode()
        var weekDays = 0
        if (mode == ScheduleMode.WEEKLY) {
            for (i in 0..6) {
                if (binding.weekdayBoxes()[i].isChecked) weekDays = weekDays or (1 shl i)
            }
            if (weekDays == 0) {
                Toast.makeText(this, R.string.task_validate_weekday, Toast.LENGTH_SHORT).show()
                return
            }
        }

        val task = Task(
            id = if (taskId == NEW_TASK_ID) 0L else taskId,
            name = name,
            targetPackage = targetPackage,
            targetActivity = targetActivity,
            scheduleMode = mode,
            hour = hour,
            minute = minute,
            weekDays = weekDays,
            enabled = binding.swEnabled.isChecked,
        )

        if (taskId == NEW_TASK_ID) {
            dao.insert(task)
        } else {
            dao.update(task)
        }
        // 保存后全量重排闹钟（M3）
        Scheduler.rescheduleAll(this)
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val NEW_TASK_ID = -1L
        private const val EXTRA_TASK_ID = "task_id"

        fun intent(context: Context, taskId: Long): Intent =
            Intent(context, TaskEditActivity::class.java).putExtra(EXTRA_TASK_ID, taskId)
    }
}

/** 七个星期 CheckBox，顺序：周一…周日 */
private fun com.autotask.app.databinding.ActivityTaskEditBinding.weekdayBoxes(): List<CheckBox> =
    listOf(cbMon, cbTue, cbWed, cbThu, cbFri, cbSat, cbSun)
