package com.example.pixellauncher

import android.content.Context
import android.view.View
import android.view.ViewGroup

/** Одна страница рабочего стола: сетка COLS x ROWS. Пропорции — с референса. */
class GridPage(
    ctx: Context,
    items: List<Item>,
    onApp: (AppInfo) -> Unit,
    onFolder: (FolderItem) -> Unit
) : ViewGroup(ctx) {

    private val cells = ArrayList<Pair<Item, View>>()
    private var cellW = 0f
    private var cellH = 0f
    private var gridL = 0f
    private var gridT = 0f

    init {
        for (item in items) {
            val v: View = when (item) {
                is AppItem -> AppIconView(ctx, item.app).also { it.setOnClickListener { onApp(item.app) } }
                is FolderItem -> FolderView(ctx, item).also { it.setOnClickListener { onFolder(item) } }
            }
            cells.add(item to v)
            addView(v)
        }
    }

    private fun exact(px: Float) = MeasureSpec.makeMeasureSpec(px.toInt(), MeasureSpec.EXACTLY)

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        val h = MeasureSpec.getSize(hSpec)
        setMeasuredDimension(w, h)
        gridL = w * 0.052f
        gridT = h * 0.091f
        cellW = (w - 2 * gridL) / HomeLayout.COLS
        cellH = (h * 0.806f - gridT) / HomeLayout.ROWS
        for ((item, v) in cells) v.measure(exact(item.spanX * cellW), exact(item.spanY * cellH))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for ((item, v) in cells) {
            val x = (gridL + item.col * cellW).toInt()
            val y = (gridT + item.row * cellH).toInt()
            v.layout(x, y, x + v.measuredWidth, y + v.measuredHeight)
        }
    }
}
