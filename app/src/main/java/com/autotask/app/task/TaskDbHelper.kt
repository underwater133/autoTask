package com.autotask.app.task

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 任务数据库（M2）：原生 SQLite，保持轻量。
 */
class TaskDbHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "autotask.db"
        private const val DB_VERSION = 1

        const val TABLE_TASKS = "tasks"
        const val COL_ID = "id"
        const val COL_NAME = "name"
        const val COL_TARGET_PACKAGE = "target_package"
        const val COL_TARGET_ACTIVITY = "target_activity"
        const val COL_SCHEDULE_MODE = "schedule_mode"
        const val COL_HOUR = "hour"
        const val COL_MINUTE = "minute"
        const val COL_WEEK_DAYS = "week_days"
        const val COL_ENABLED = "enabled"
        const val COL_CREATED_AT = "created_at"
        const val COL_NEXT_TRIGGER_AT = "next_trigger_at"

        val ALL_COLUMNS = arrayOf(
            COL_ID, COL_NAME, COL_TARGET_PACKAGE, COL_TARGET_ACTIVITY,
            COL_SCHEDULE_MODE, COL_HOUR, COL_MINUTE, COL_WEEK_DAYS,
            COL_ENABLED, COL_CREATED_AT, COL_NEXT_TRIGGER_AT
        )
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_TASKS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NAME TEXT NOT NULL,
                $COL_TARGET_PACKAGE TEXT NOT NULL,
                $COL_TARGET_ACTIVITY TEXT NOT NULL DEFAULT '',
                $COL_SCHEDULE_MODE INTEGER NOT NULL,
                $COL_HOUR INTEGER NOT NULL,
                $COL_MINUTE INTEGER NOT NULL,
                $COL_WEEK_DAYS INTEGER NOT NULL DEFAULT 0,
                $COL_ENABLED INTEGER NOT NULL DEFAULT 1,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_NEXT_TRIGGER_AT INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 暂无迁移需求；后续版本按需实现
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TASKS")
        onCreate(db)
    }
}
