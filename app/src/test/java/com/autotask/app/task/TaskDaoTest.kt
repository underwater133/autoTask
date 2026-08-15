package com.autotask.app.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * TaskDao 运行时测试（Robolectric）：真实 SQLite 增删改查。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskDaoTest {

    private lateinit var dao: TaskDao

    @Before
    fun setUp() {
        dao = TaskDao(RuntimeEnvironment.getApplication())
    }

    private fun sampleTask() = Task(
        name = "打开微信",
        targetPackage = "com.tencent.mm",
        scheduleMode = ScheduleMode.WORKDAY,
        hour = 8,
        minute = 30,
        enabled = true,
    )

    @Test
    fun insert_getById_roundTrip() {
        val id = dao.insert(sampleTask())
        assertTrue(id > 0)

        val loaded = dao.getById(id)
        assertNotNull(loaded)
        loaded!!
        assertEquals("打开微信", loaded.name)
        assertEquals("com.tencent.mm", loaded.targetPackage)
        assertEquals(ScheduleMode.WORKDAY, loaded.scheduleMode)
        assertEquals(8, loaded.hour)
        assertEquals(30, loaded.minute)
        assertTrue(loaded.enabled)
    }

    @Test
    fun getAll_returnsInsertionOrder() {
        dao.insert(sampleTask().copy(name = "A"))
        dao.insert(sampleTask().copy(name = "B", scheduleMode = ScheduleMode.DAILY))
        val all = dao.getAll()
        assertEquals(2, all.size)
        assertEquals(listOf("A", "B"), all.map { it.name })
    }

    @Test
    fun update_persistsChanges() {
        val id = dao.insert(sampleTask())
        val updated = dao.getById(id)!!.copy(name = "改名", hour = 9, weekDays = (1 shl 0) or (1 shl 4))
        dao.update(updated)

        val loaded = dao.getById(id)!!
        assertEquals("改名", loaded.name)
        assertEquals(9, loaded.hour)
        assertEquals((1 shl 0) or (1 shl 4), loaded.weekDays)
    }

    @Test
    fun setEnabled_toggle() {
        val id = dao.insert(sampleTask())
        assertTrue(dao.getEnabled().any { it.id == id })

        dao.setEnabled(id, false)
        assertFalse(dao.getEnabled().any { it.id == id })
        assertFalse(dao.getById(id)!!.enabled)

        dao.setEnabled(id, true)
        assertTrue(dao.getEnabled().any { it.id == id })
    }

    @Test
    fun delete_removesRow() {
        val id = dao.insert(sampleTask())
        dao.delete(id)
        assertNull(dao.getById(id))
        assertTrue(dao.getAll().isEmpty())
    }
}
