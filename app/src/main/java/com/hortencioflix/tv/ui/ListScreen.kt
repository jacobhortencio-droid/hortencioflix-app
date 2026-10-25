package com.hortencioflix.tv.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hortencioflix.tv.R

/** Base para ecrãs de lista simples (add-ons, TorBox, diagnóstico). */
abstract class ListScreen : BaseActivity() {
    protected lateinit var adapter: RowAdapter
    protected lateinit var status: TextView
    protected lateinit var progress: View
    protected lateinit var action: TextView
    protected lateinit var listView: RecyclerView

    abstract val screenTitle: String
    abstract fun onRow(index: Int, row: Row)
    open fun onRowLong(index: Int, row: Row) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        findViewById<TextView>(R.id.list_title).text = screenTitle
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        status = findViewById(R.id.list_status)
        progress = findViewById(R.id.list_progress)
        action = findViewById(R.id.list_action)
        listView = findViewById(R.id.list)
        adapter = RowAdapter(emptyList(), onClick = { i, r -> onRow(i, r) }, onLong = { i, r -> onRowLong(i, r) })
        listView.layoutManager = LinearLayoutManager(this)
        listView.adapter = adapter
    }

    protected fun setStatus(text: String) {
        status.text = text
        status.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }

    protected fun setAction(label: String, onClick: () -> Unit) {
        action.text = label
        action.visibility = View.VISIBLE
        action.setOnClickListener { onClick() }
    }

    protected fun focusFirst() {
        listView.animateIn()
        listView.post { listView.getChildAt(0)?.requestFocus() }
    }
}
