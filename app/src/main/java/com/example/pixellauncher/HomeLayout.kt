package com.example.pixellauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawToBitmap
import kotlin.math.floor
import kotlin.math.min

/**
 * Рабочий стол: страницы (свайп) + неподвижный док + индикатор страниц.
 * Долгое нажатие на иконку — перетаскивание (между ячейками, страницами и доком).
 */
class HomeLayout(ctx: Context) : ViewGroup(ctx) {

    companion object {
        const val COLS = 4
        const val ROWS = 6
        val PLATE: Int = Color.argb(64, 255, 255, 255)
    }

    var onApp: (AppInfo) -> Unit = {}
    var onFolder: (FolderItem) -> Unit = {}
    var onChanged: (List<Item>, List<AppInfo>) -> Unit = { _, _ -> }

    private var items = ArrayList<Item>()
    private var dock = ArrayList<AppInfo>()
    private val pages = PagesLayout(ctx)
    private val dockViews = ArrayList<View>()
    private val dockRect = RectF()
    private var gridL = 0f
    private var gridT = 0f
    private var cellW = 0f
    private var cellH = 0f
    private var currentPage = 0

    // ---- перетаскивание ----
    private sealed class Target {
        class Grid(val page: Int, val col: Int, val row: Int) : Target()
        class Dock(val slot: Int) : Target()
    }

    private var dragging = false
    private var dragItem: Item? = null
    private var dragDockIndex = -1
    private var dragView: View? = null
    private var dragBmp: Bitmap? = null
    private var dragX = 0f
    private var dragY = 0f
    private var grabDx = 0f
    private var grabDy = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var highlight: RectF? = null
    private var flipDir = 0
    private val handler = Handler(Looper.getMainLooper())
    private val flipRunnable = Runnable { flipPage() }

    private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PLATE }
    private val hlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255) }
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = 235 }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255)
        strokeCap = Paint.Cap.ROUND
    }

    init {
        setWillNotDraw(false)
        pages.onPageChanged = {
            currentPage = it
            if (dragging) updateTarget()
            invalidate()
        }
    }

    fun goToFirstPage() = pages.snapTo(0)

    fun setContent(newItems: List<Item>, newDock: List<AppInfo>) {
        items = ArrayList(newItems)
        dock = ArrayList(newDock)
        rebuild(0)
    }

    private fun newPage(list: List<Item>) = GridPage(
        context, list,
        { onApp(it) },
        { onFolder(it) },
        { item, v -> startDrag(item, -1, v) }
    )

    private fun rebuild(keepPage: Int) {
        removeAllViews()
        pages.removeAllViews()
        dockViews.clear()

        val count = (items.maxOfOrNull { it.page } ?: 0) + 1
        for (p in 0 until count) pages.addView(newPage(items.filter { it.page == p }))
        val keep = keepPage.coerceIn(0, count - 1)
        pages.snapTo(keep, false)
        currentPage = keep
        addView(pages)

        dock.forEachIndexed { i, a ->
            val v = AppIconView(context, a, showLabel = false)
            v.setOnClickListener { onApp(a) }
            v.setOnLongClickListener {
                startDrag(null, i, v)
                true
            }
            dockViews.add(v)
            addView(v)
        }
        invalidate()
    }

    // ---- layout ----

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

    // ---- касания ----

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        lastX = e.x
        lastY = e.y
        if (dragging) {
            onDragEvent(e)
            return true
        }
        return super.dispatchTouchEvent(e)
    }

    private fun startDrag(item: Item?, dockIndex: Int, v: View) {
        if (dragging) return
        val bmp = try { v.drawToBitmap() } catch (_: Exception) { return }

        // отменить текущее касание у дочерних view (до включения режима перетаскивания)
        val now = SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, lastX, lastY, 0)
        super.dispatchTouchEvent(cancel)
        cancel.recycle()

        val vl = IntArray(2)
        val hl = IntArray(2)
        v.getLocationOnScreen(vl)
        getLocationOnScreen(hl)
        val cx = vl[0] - hl[0] + v.width / 2f
        val cy = vl[1] - hl[1] + v.height / 2f

        dragging = true
        dragItem = item
        dragDockIndex = dockIndex
        dragView = v
        dragBmp = bmp
        dragX = cx
        dragY = cy
        grabDx = cx - lastX
        grabDy = cy - lastY
        v.alpha = 0f
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        updateTarget()
        invalidate()
    }

    private fun onDragEvent(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                dragX = e.x + grabDx
                dragY = e.y + grabDy
                updateTarget()
                updateEdge(e.x)
                invalidate()
            }
            MotionEvent.ACTION_UP -> finishDrag(true)
            MotionEvent.ACTION_CANCEL -> finishDrag(false)
        }
    }

    private fun updateEdge(x: Float) {
        val dir = when {
            x < width * 0.07f -> -1
            x > width * 0.93f -> 1
            else -> 0
        }
        if (dir != flipDir) {
            handler.removeCallbacks(flipRunnable)
            flipDir = dir
            if (dir != 0) handler.postDelayed(flipRunnable, 550)
        }
    }

    private fun flipPage() {
        if (!dragging || flipDir == 0) return
        val target = pages.page + flipDir
        if (target < 0) return
        if (target >= pages.childCount) {
            // можно добавить одну новую пустую страницу
            val real = (items.maxOfOrNull { it.page } ?: 0) + 1
            if (pages.childCount > real) return
            pages.addView(newPage(emptyList()))
            invalidate()
        }
        pages.snapTo(target)
        handler.postDelayed(flipRunnable, 700)
    }

    private fun finishDrag(commit: Boolean) {
        handler.removeCallbacks(flipRunnable)
        flipDir = 0
        val plan = if (commit) targetAt()?.let { plan(it) } else null
        dragging = false
        dragBmp = null
        highlight = null
        plan?.invoke()
        dragItem = null
        dragDockIndex = -1
        dragView = null
        rebuild(pages.page)
        if (plan != null) onChanged(items, dock)
    }

    // ---- логика цели и правил сброса ----

    private fun spanX() = dragItem?.spanX ?: 1
    private fun spanY() = dragItem?.spanY ?: 1

    private fun targetAt(): Target? {
        if (dragY > height * 0.85f) {
            if (dragItem is FolderItem) return null
            val slot = ((dragX - dockRect.left) / (dockRect.width() / COLS)).toInt().coerceIn(0, COLS - 1)
            return Target.Dock(slot)
        }
        val sx = spanX()
        val sy = spanY()
        val col = floor((dragX - gridL) / cellW - (sx - 1) / 2f).toInt().coerceIn(0, COLS - sx)
        val row = floor((dragY - gridT) / cellH - (sy - 1) / 2f).toInt().coerceIn(0, ROWS - sy)
        return Target.Grid(pages.page, col, row)
    }

    private fun updateTarget() {
        val t = targetAt()
        highlight = if (t != null && plan(t) != null) rectFor(t) else null
    }

    private fun rectFor(t: Target): RectF = when (t) {
        is Target.Grid -> RectF(
            gridL + t.col * cellW,
            gridT + t.row * cellH,
            gridL + (t.col + spanX()) * cellW,
            gridT + (t.row + spanY()) * cellH
        ).apply { inset(cellW * 0.08f, cellW * 0.08f) }
        is Target.Dock -> {
            val cw = dockRect.width() / COLS
            RectF(dockRect.left + t.slot * cw, dockRect.top, dockRect.left + (t.slot + 1) * cw, dockRect.bottom)
                .apply { inset(cw * 0.08f, cw * 0.08f) }
        }
    }

    private fun occupants(page: Int, col: Int, row: Int, sx: Int, sy: Int, exclude: Item?): List<Item> =
        items.filter { o ->
            o !== exclude && o.page == page &&
                o.col < col + sx && col < o.col + o.spanX &&
                o.row < row + sy && row < o.row + o.spanY
        }

    /** Возвращает действие (перемещение/обмен), если сброс сюда допустим, иначе null. */
    private fun plan(t: Target): (() -> Unit)? {
        val d = dragItem
        return when (t) {
            is Target.Grid -> if (d != null) planGridToGrid(d, t) else planDockToGrid(t)
            is Target.Dock -> if (d != null) planGridToDock(d, t) else planDockToDock(t)
        }
    }

    private fun planGridToGrid(d: Item, t: Target.Grid): (() -> Unit)? {
        val occ = occupants(t.page, t.col, t.row, d.spanX, d.spanY, d)
        if (occ.isEmpty()) {
            return {
                d.page = t.page
                d.col = t.col
                d.row = t.row
            }
        }
        val o = occ.singleOrNull() ?: return null
        if (o.spanX == d.spanX && o.spanY == d.spanY &&
            o.page == t.page && o.col == t.col && o.row == t.row
        ) {
            return {
                val p = d.page
                val c = d.col
                val r = d.row
                d.page = o.page
                d.col = o.col
                d.row = o.row
                o.page = p
                o.col = c
                o.row = r
            }
        }
        return null
    }

    private fun planDockToGrid(t: Target.Grid): (() -> Unit)? {
        val i = dragDockIndex
        val a = dock.getOrNull(i) ?: return null
        val o = occupants(t.page, t.col, t.row, 1, 1, null).singleOrNull() as? AppItem ?: return null
        return {
            items.remove(o)
            items.add(AppItem(a, o.col, o.row, o.page))
            dock[i] = o.app
        }
    }

    private fun planGridToDock(d: Item, t: Target.Dock): (() -> Unit)? {
        val da = (d as? AppItem)?.app ?: return null
        if (t.slot < dock.size) {
            val other = dock[t.slot]
            return {
                dock[t.slot] = da
                items.remove(d)
                items.add(AppItem(other, d.col, d.row, d.page))
            }
        }
        if (dock.size < COLS) {
            return {
                dock.add(da)
                items.remove(d)
            }
        }
        return null
    }

    private fun planDockToDock(t: Target.Dock): (() -> Unit)? {
        val i = dragDockIndex
        if (i < 0 || i >= dock.size) return null
        if (t.slot >= dock.size || t.slot == i) return {}
        return {
            val tmp = dock[i]
            dock[i] = dock[t.slot]
            dock[t.slot] = tmp
        }
    }

    // ---- рисование ----

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // док
        val r = dockRect.height() * 0.27f
        c.drawRoundRect(dockRect, r, r, platePaint)

        // подсветка цели перетаскивания
        highlight?.let { c.drawRoundRect(it, it.width() * 0.14f, it.width() * 0.14f, hlPaint) }

        // индикатор: "=" и точки страниц
        val n = pages.childCount.coerceAtLeast(1)
        val cy = h * 0.835f
        val total = n + 1
        val spacing = min(w * 0.051f, w * 0.8f / (total - 1).coerceAtLeast(1))
        val startX = w / 2f - (total - 1) * spacing / 2f
        val hw = w * 0.007f
        val g = w * 0.0035f
        linePaint.strokeWidth = w * 0.0035f
        c.drawLine(startX - hw, cy - g, startX + hw, cy - g, linePaint)
        c.drawLine(startX - hw, cy + g, startX + hw, cy + g, linePaint)
        for (i in 0 until n) {
            dotPaint.alpha = if (i == currentPage) 255 else 90
            c.drawCircle(startX + (i + 1) * spacing, cy, w * 0.007f, dotPaint)
        }
    }

    override fun dispatchDraw(c: Canvas) {
        super.dispatchDraw(c)
        val b = dragBmp ?: return
        c.save()
        c.translate(dragX, dragY)
        c.scale(1.1f, 1.1f)
        c.drawBitmap(b, -b.width / 2f, -b.height / 2f, ghostPaint)
        c.restore()
    }
}
