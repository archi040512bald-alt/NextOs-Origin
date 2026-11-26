package com.example.pixellauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup

/** Одна страница рабочего стола: сетка COLS x ROWS. Пропорции — с референса. */
class GridPage(
    ctx: Context,
    private val pageItems: List<Item>,
    onApp: (AppInfo) -> Unit,
    onFolder: (FolderItem) -> Unit,
    onLong: (Item, View) -> Unit,
    onBadge: (AppInfo) -> Unit
) : ViewGroup(ctx) {

    private val cells = ArrayList<Pair<Item, View>>()
    private var cellW = 0f
    private var cellH = 0f
    private var gridL = 0f
    private var gridT = 0f
    private var editP = 0f

    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val rect = RectF()

    init {
        setWillNotDraw(false)
        for (item in pageItems) {
            val v: View = when (item) {
                is AppItem -> AppIconView(ctx, item.app).also {
                    it.setOnClickListener { onApp(item.app) }
                    it.onBadge = { a -> onBadge(a) }
                }
                is FolderItem -> FolderView(ctx, item).also { it.setOnClickListener { onFolder(item) } }
            }
            v.setOnLongClickListener {
                onLong(item, v)
                true
            }
            cells.add(item to v)
            addView(v)
        }
    }

    fun setEdit(p: Float) {
        editP = p
        for ((_, v) in cells) if (v is AppIconView) v.editP = p
        invalidate()
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

    /** В режиме редактирования рисуем контуры пустых ячеек. */
    override fun onDraw(cv: Canvas) {
        if (editP <= 0f) return
        val occ = Array(HomeLayout.ROWS) { BooleanArray(HomeLayout.COLS) }
        for (item in pageItems) {
            for (dy in 0 until item.spanY) for (dx in 0 until item.spanX) {
                val r = item.row + dy
                val c = item.col + dx
                if (r in 0 until HomeLayout.ROWS && c in 0 until HomeLayout.COLS) occ[r][c] = true
            }
        }
        outline.alpha = (110 * editP).toInt()
        outline.strokeWidth = cellW * 0.012f
        val s = cellW * AppIconView.ICON
        for (r in 0 until HomeLayout.ROWS) for (c in 0 until HomeLayout.COLS) {
            if (occ[r][c]) continue
            val left = gridL + c * cellW + (cellW - s) / 2f
            val top = gridT + r * cellH
            rect.set(left, top, left + s, top + s)
            cv.drawRoundRect(rect, s * 0.3f, s * 0.3f, outline)
        }
    }
}
