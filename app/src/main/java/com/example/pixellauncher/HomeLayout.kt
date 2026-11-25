package com.example.pixellauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup

/**
 * Рабочий стол: сетка 4x6, док, индикатор страниц.
 * Пропорции сняты с референса (1159x2576 px).
 * Строки 0..2 свободны — под будущий виджет часов.
 */
class HomeLayout(ctx: Context) : ViewGroup(ctx) {

    companion object {
        const val COLS = 4
        const val ROWS = 6
        val PLATE: Int = Color.argb(64, 255, 255, 255)
    }

    var onApp: (AppInfo) -> Unit = {}
    var onFolder: (FolderItem) -> Unit = {}

    private val cells = ArrayList<Pair<Item, View>>()
    private val dockViews = ArrayList<View>()
    private val dockRect = RectF()
    private var cellW = 0f
    private var cellH = 0f
    private var gridL = 0f
    private var gridT = 0f

    private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PLATE }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255)
        strokeCap = Paint.Cap.ROUND
    }

    init { setWillNotDraw(false) }

    fun setContent(items: List<Item>, dock: List<AppInfo>) {
        removeAllViews()
        cells.clear()
        dockViews.clear()
        for (item in items) {
            val v: View = when (item) {
                is AppItem -> AppIconView(context, item.app).also { it.setOnClickListener { onApp(item.app) } }
                is FolderItem -> FolderView(context, item).also { it.setOnClickListener { onFolder(item) } }
            }
            cells.add(item to v)
            addView(v)
        }
        for (a in dock) {
            val v = AppIconView(context, a, showLabel = false)
            v.setOnClickListener { onApp(a) }
            dockViews.add(v)
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
        cellW = (w - 2 * gridL) / COLS
        cellH = (h * 0.806f - gridT) / ROWS
        dockRect.set(w * 0.05f, h * 0.881f, w * 0.95f, h * 0.974f)

        for ((item, v) in cells) v.measure(exact(item.spanX * cellW), exact(item.spanY * cellH))
        val dockCellW = dockRect.width() / COLS
        for (v in dockViews) v.measure(exact(dockCellW), exact(dockRect.height()))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for ((item, v) in cells) {
            val x = (gridL + item.col * cellW).toInt()
            val y = (gridT + item.row * cellH).toInt()
            v.layout(x, y, x + v.measuredWidth, y + v.measuredHeight)
        }
        val dockCellW = dockRect.width() / COLS
        dockViews.forEachIndexed { i, v ->
            val x = (dockRect.left + i * dockCellW).toInt()
            val y = dockRect.top.toInt()
            v.layout(x, y, x + v.measuredWidth, y + v.measuredHeight)
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // док
        val r = dockRect.height() * 0.27f
        c.drawRoundRect(dockRect, r, r, platePaint)

        // индикатор: "=" (меню/поиск) и точка текущей страницы
        val cy = h * 0.835f
        val eqX = w * 0.471f
        val hw = w * 0.007f
        val g = w * 0.0035f
        linePaint.strokeWidth = w * 0.0035f
        c.drawLine(eqX - hw, cy - g, eqX + hw, cy - g, linePaint)
        c.drawLine(eqX - hw, cy + g, eqX + hw, cy + g, linePaint)
        c.drawCircle(w * 0.522f, cy, w * 0.007f, dotPaint)
    }
}
