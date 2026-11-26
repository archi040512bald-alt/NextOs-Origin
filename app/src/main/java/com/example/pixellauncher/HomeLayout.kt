package com.example.pixellauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import kotlin.math.min

/**
 * Рабочий стол: страницы (свайп) + неподвижный док + индикатор страниц.
 * Пропорции сняты с референса (1159x2576 px).
 */
class HomeLayout(ctx: Context) : ViewGroup(ctx) {

    companion object {
        const val COLS = 4
        const val ROWS = 6
        val PLATE: Int = Color.argb(64, 255, 255, 255)
    }

    var onApp: (AppInfo) -> Unit = {}
    var onFolder: (FolderItem) -> Unit = {}

    private val pages = PagesLayout(ctx)
    private val dockViews = ArrayList<View>()
    private val dockRect = RectF()
    private var pageCount = 1
    private var currentPage = 0

    private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PLATE }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255)
        strokeCap = Paint.Cap.ROUND
    }

    init {
        setWillNotDraw(false)
        pages.onPageChanged = {
            currentPage = it
            invalidate()
        }
    }

    fun goToFirstPage() = pages.snapTo(0)

    fun setContent(items: List<Item>, dock: List<AppInfo>) {
        removeAllViews()
        pages.removeAllViews()
        dockViews.clear()

        pageCount = (items.maxOfOrNull { it.page } ?: 0) + 1
        for (p in 0 until pageCount) {
            pages.addView(GridPage(context, items.filter { it.page == p }, { onApp(it) }, { onFolder(it) }))
        }
        pages.snapTo(0, false)
        currentPage = 0
        addView(pages)

        for (a in dock) {
            val v = AppIconView(context, a, showLabel = false)
            v.setOnClickListener { onApp(a) }
            dockViews.add(v)
            addView(v)
        }
        invalidate()
    }

    private fun exact(px: Float) = MeasureSpec.makeMeasureSpec(px.toInt(), MeasureSpec.EXACTLY)

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        val h = MeasureSpec.getSize(hSpec)
        setMeasuredDimension(w, h)
        dockRect.set(w * 0.05f, h * 0.881f, w * 0.95f, h * 0.974f)

        pages.measure(exact(w.toFloat()), exact(h.toFloat()))
        val dockCellW = dockRect.width() / COLS
        for (v in dockViews) v.measure(exact(dockCellW), exact(dockRect.height()))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        pages.layout(0, 0, r - l, b - t)
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

        // индикатор: "=" и точки страниц
        val cy = h * 0.835f
        val total = pageCount + 1
        val spacing = min(w * 0.051f, w * 0.8f / (total - 1).coerceAtLeast(1))
        val startX = w / 2f - (total - 1) * spacing / 2f
        val hw = w * 0.007f
        val g = w * 0.0035f
        linePaint.strokeWidth = w * 0.0035f
        c.drawLine(startX - hw, cy - g, startX + hw, cy - g, linePaint)
        c.drawLine(startX - hw, cy + g, startX + hw, cy + g, linePaint)
        for (i in 0 until pageCount) {
            dotPaint.alpha = if (i == currentPage) 255 else 90
            c.drawCircle(startX + (i + 1) * spacing, cy, w * 0.007f, dotPaint)
        }
    }
}
