package com.autotask.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.autotask.app.databinding.ActivityLogBinding
import com.autotask.app.log.AppLogger

/**
 * 执行日志页（M5）：展示最近执行记录，支持清空。
 */
class LogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogBinding
    private var adapter: LogAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setTitle(R.string.log_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        adapter = LogAdapter()
        binding.recycler.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        adapter?.submit(AppLogger.readRecent(this, MAX_LINES))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_CLEAR, 0, R.string.log_clear)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == MENU_CLEAR) {
            AppLogger.clear(this)
            adapter?.submit(emptyList())
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        private const val MENU_CLEAR = 1
        private const val MAX_LINES = 200
    }
}

private class LogAdapter : RecyclerView.Adapter<LogAdapter.VH>() {

    private val items = mutableListOf<String>()

    fun submit(list: List<String>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply {
            textSize = 12f
            setPadding(16, 10, 16, 10)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        return VH(tv)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.tv.text = items[position]
    }

    class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)
}
