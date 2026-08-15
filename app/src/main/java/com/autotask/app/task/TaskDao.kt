package com.autotask.app.task

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

/**
 * 任务数据访问层（M2）：CRUD。
 */
class TaskDao(context: Context) {

    private val dbHelper = TaskDbHelper(context)

    private val db: SQLiteDatabase get() = dbHelper.writableDatabase

    // ---------- 增 ----------
    fun insert(task: Task): Long {
        val values = toValues(task)
        // 新任务（id=0）不写 id，交给 AUTOINCREMENT 分配；
        // 显式写 0 会被 SQLite 当作 rowid=0 存储，导致后续插入覆盖同一行
        if (task.id > 0) {
            values.put(TaskDbHelper.COL_ID, task.id)
        }
        return db.insertWithOnConflict(TaskDbHelper.TABLE_TASKS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ---------- 查 ----------
    fun getById(id: Long): Task? =
        db.query(
            TaskDbHelper.TABLE_TASKS, TaskDbHelper.ALL_COLUMNS,
            "${TaskDbHelper.COL_ID}=?", arrayOf(id.toString()), null, null, null
        ).use { c -> if (c.moveToFirst()) fromCursor(c) else null }

    fun getAll(): List<Task> =
        db.query(
            TaskDbHelper.TABLE_TASKS, TaskDbHelper.ALL_COLUMNS,
            null, null, null, null, "${TaskDbHelper.COL_CREATED_AT} ASC"
        ).use { c -> buildList { while (c.moveToNext()) add(fromCursor(c)) } }

    fun getEnabled(): List<Task> =
        db.query(
            TaskDbHelper.TABLE_TASKS, TaskDbHelper.ALL_COLUMNS,
            "${TaskDbHelper.COL_ENABLED}=1", null, null, null, "${TaskDbHelper.COL_CREATED_AT} ASC"
        ).use { c -> buildList { while (c.moveToNext()) add(fromCursor(c)) } }

    // ---------- 改 ----------
    fun update(task: Task): Int =
        db.update(
            TaskDbHelper.TABLE_TASKS, toValues(task),
            "${TaskDbHelper.COL_ID}=?", arrayOf(task.id.toString())
        )

    fun setEnabled(id: Long, enabled: Boolean): Int {
        val values = ContentValues().apply { put(TaskDbHelper.COL_ENABLED, if (enabled) 1 else 0) }
        return db.update(
            TaskDbHelper.TABLE_TASKS, values,
            "${TaskDbHelper.COL_ID}=?", arrayOf(id.toString())
        )
    }

    // ---------- 删 ----------
    fun delete(id: Long): Int =
        db.delete(TaskDbHelper.TABLE_TASKS, "${TaskDbHelper.COL_ID}=?", arrayOf(id.toString()))

    // ---------- 映射 ----------
    private fun toValues(task: Task): ContentValues = ContentValues().apply {
        put(TaskDbHelper.COL_NAME, task.name)
        put(TaskDbHelper.COL_TARGET_PACKAGE, task.targetPackage)
        put(TaskDbHelper.COL_TARGET_ACTIVITY, task.targetActivity)
        put(TaskDbHelper.COL_SCHEDULE_MODE, task.scheduleMode.dbValue)
        put(TaskDbHelper.COL_HOUR, task.hour)
        put(TaskDbHelper.COL_MINUTE, task.minute)
        put(TaskDbHelper.COL_WEEK_DAYS, task.weekDays)
        put(TaskDbHelper.COL_ENABLED, if (task.enabled) 1 else 0)
        put(TaskDbHelper.COL_CREATED_AT, task.createdAt)
        put(TaskDbHelper.COL_NEXT_TRIGGER_AT, task.nextTriggerAt)
    }

    private fun fromCursor(c: Cursor): Task = Task(
        id = c.getLong(c.getColumnIndexOrThrow(TaskDbHelper.COL_ID)),
        name = c.getString(c.getColumnIndexOrThrow(TaskDbHelper.COL_NAME)),
        targetPackage = c.getString(c.getColumnIndexOrThrow(TaskDbHelper.COL_TARGET_PACKAGE)),
        targetActivity = c.getString(c.getColumnIndexOrThrow(TaskDbHelper.COL_TARGET_ACTIVITY)),
        scheduleMode = ScheduleMode.fromDb(c.getInt(c.getColumnIndexOrThrow(TaskDbHelper.COL_SCHEDULE_MODE))),
        hour = c.getInt(c.getColumnIndexOrThrow(TaskDbHelper.COL_HOUR)),
        minute = c.getInt(c.getColumnIndexOrThrow(TaskDbHelper.COL_MINUTE)),
        weekDays = c.getInt(c.getColumnIndexOrThrow(TaskDbHelper.COL_WEEK_DAYS)),
        enabled = c.getInt(c.getColumnIndexOrThrow(TaskDbHelper.COL_ENABLED)) == 1,
        createdAt = c.getLong(c.getColumnIndexOrThrow(TaskDbHelper.COL_CREATED_AT)),
        nextTriggerAt = c.getLong(c.getColumnIndexOrThrow(TaskDbHelper.COL_NEXT_TRIGGER_AT)),
    )
}
