package com.example.pixellauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.view.drawToBitmap
import kotlin.math.floor
import kotlin.math.min

/**
 * Рабочий стол: страницы (свайп) + неподвижный док + индикатор страниц.
 * Долгое нажатие — перетаскивание. Щипок двумя пальцами — режим редактирования.
 */
class HomeLayout(ctx: Context) : ViewGroup(ctx) {

    companion object {
        const val COLS = 4
        const val ROWS = 6
        val PLATE: Int = Color.argb(64, 255, 255, 255)
        private const val EDIT_SCALE = 0.30f   // на сколько уменьшаются страницы
        private const val BLUR_MAX = 100       // px
    }

    var onApp: (AppInfo) -> Unit = {}
    var onFolder: (FolderItem) -> Unit = {}
    var onChanged: (List<Item>, List<AppInfo>) -> Unit = { _, _ -> }
    var onBlur: (Int) -> Unit = {}
    var onAction: (Int) -> Unit = {}
    var onUninstall: (AppInfo) -> Unit = {}

    private var items = ArrayList<Item>()
    private var dock = ArrayList<AppInfo>()
    private val pages = PagesLayout(ctx)
    private val bar = EditBar(ctx)
    private val dockViews = ArrayList<View>()
    private val dockRect = RectF()
    private var gridL = 0f
    private var gridT = 0f
    private var cellW = 0f
    private var cellH = 0f
    private var currentPage = 0

    // ---- режим редактирования ----
    private var editTarget = false
    private var editP = 0f
    private var editAnim: ValueAnimator? = null
    private var pinching = false

    val editing: Boolean get() = editTarget

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

    private val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var startSpan = 0f

        override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
            startSpan = d.currentSpan
            return true
        }

        override fun onScale(d: ScaleGestureDetector): Boolean {
            if (pinching) return true
            if (!editTarget && d.currentSpan < startSpan * 0.82f) {
                pinching = true
                cancelChildren()
                enterEdit()
            } else if (editTarget && d.currentSpan > startSpan * 1.2f) {
                pinching = true
                cancelChildren()
                exitEdit()
            }
            return true
        }
    }).apply { isQuickScaleEnabled = false }

    init {
        setWillNotDraw(false)
        pages.onPageChanged = {
            currentPage = it
            if (dragging) updateTarget()
            updateBar()
            invalidate()
        }
        pages.onTap = { if (editTarget) exitEdit() }
        pages.onScrolled = { invalidate() } // плашки привязаны к обоям, перерисовываем при прокрутке
        bar.onPage = { pages.snapTo(it) }
        bar.onAction = { onAction(it) }
        bar.visibility = INVISIBLE
    }

    fun goToFirstPage() = pages.snapTo(0)

    fun setContent(newItems: List<Item>, newDock: List<AppInfo>) {
        items = ArrayList(newItems)
        dock = ArrayList(newDock)
        rebuild(0)
    }

    private fun newPage(list: List<Item>) = GridPage(
        context, list,
        { if (!editTarget) onApp(it) },
        { if (!editTarget) onFolder(it) },
        { item, v -> startDrag(item, -1, v) },
        { onUninstall(it) }
    )

    private fun updateBar() {
        bar.items = items
        bar.pageCount = pages.childCount.coerceAtLeast(1)
        bar.current = currentPage
        bar.invalidate()
    }

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
            v.setOnClickListener { if (!editTarget) onApp(a) }
            v.setOnLongClickListener {
                if (editTarget) false else {
                    startDrag(null, i, v)
                    true
                }
            }
            dockViews.add(v)
            addView(v)
        }
        addView(bar)
        updateBar()
        applyEdit()
        invalidate()
    }

    // ---- режим редактирования ----

    fun enterEdit() {
        if (editTarget || dragging) return
        editTarget = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        animateEdit(1f)
    }

    fun exitEdit() {
        if (!editTarget) return
        editTarget = false
        animateEdit(0f)
    }

    private fun animateEdit(to: Float) {
        editAnim?.cancel()
        editAnim = ValueAnimator.ofFloat(editP, to).apply {
            duration = 280
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                editP = it.animatedValue as Float
                applyEdit()
            }
            start()
        }
    }

    private fun applyEdit() {
        val p = editP
        val sc = 1f - EDIT_SCALE * p
        pages.pivotX = width / 2f
        pages.pivotY = height * 0.10f
        pages.scaleX = sc
        pages.scaleY = sc
        for (v in dockViews) {
            v.alpha = 1f - p
            v.visibility = if (p > 0.99f) INVISIBLE else VISIBLE
        }
        for (i in 0 until pages.childCount) (pages.getChildAt(i) as? GridPage)?.setEdit(p)
        bar.alpha = p
        bar.visibility = if (p > 0.01f) VISIBLE else INVISIBLE
        onBlur((p * BLUR_MAX).toInt())
        invalidate()
    }

    private fun cancelChildren() {
        val now = SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, lastX, lastY, 0)
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
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
        bar.measure(exact(w.toFloat()), exact(h.toFloat()))
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
        bar.layout(0, 0, r - l, b - t)
        applyEdit() // пересчитать pivot после получения размеров
    }

    // ---- касания ----

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        lastX = e.x
        lastY = e.y
        if (dragging) {
            onDragEvent(e)
            return true
        }
        scaleDetector.onTouchEvent(e)
        if (pinching) {
            if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
                pinching = false
            }
            return true
        }
        return super.dispatchTouchEvent(e)
    }

    private fun startDrag(item: Item?, dockIndex: Int, v: View) {
        if (dragging || pinching) return
        val bmp = try { v.drawToBitmap() } catch (_: Exception) { return }

        cancelChildren() // до включения режима перетаскивания

        val sc = if (editP > 0f) pages.scaleX else 1f
        val vl = IntArray(2)
        val hl = IntArray(2)
        v.getLocationOnScreen(vl)
        getLocationOnScreen(hl)
        val cx = vl[0] - hl[0] + v.width * sc / 2f
        val cy = vl[1] - hl[1] + v.height * sc / 2f

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
            val real = (items.maxOfOrNull { it.page } ?: 0) + 1
            if (pages.childCount > real) return
            val np = newPage(emptyList())
            pages.addView(np)
            np.setEdit(editP)
            updateBar()
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

    /** Переводит экранные координаты в координаты страницы (с учётом уменьшения в режиме редактирования). */
    private fun toPage(x: Float, y: Float): FloatArray {
        val pt = floatArrayOf(x, y)
        if (editP > 0f) {
            val inv = Matrix()
            if (pages.matrix.invert(inv)) inv.mapPoints(pt)
        }
        return pt
    }

    private fun targetAt(): Target? {
        if (editP < 0.5f && dragY > height * 0.85f) {
            if (dragItem is FolderItem) return null
            val slot = ((dragX - dockRect.left) / (dockRect.width() / COLS)).toInt().coerceIn(0, COLS - 1)
            return Target.Dock(slot)
        }
        val pt = toPage(dragX, dragY)
        val sx = spanX()
        val sy = spanY()
        val col = floor((pt[0] - gridL) / cellW - (sx - 1) / 2f).toInt().coerceIn(0, COLS - sx)
        val row = floor((pt[1] - gridT) / cellH - (sy - 1) / 2f).toInt().coerceIn(0, ROWS - sy)
        return Target.Grid(pages.page, col, row)
    }

    private fun updateTarget() {
        val t = targetAt()
        highlight = if (t != null && plan(t) != null) rectFor(t) else null
    }

    private fun rectFor(t: Target): RectF {
        val r = when (t) {
            is Target.Grid -> {
                val rr = RectF(
                    gridL + t.col * cellW,
                    gridT + t.row * cellH,
                    gridL + (t.col + spanX()) * cellW,
                    gridT + (t.row + spanY()) * cellH
                )
                rr.inset(cellW * 0.08f, cellW * 0.08f)
                if (editP > 0f) pages.matrix.mapRect(rr)
                rr
            }
            is Target.Dock -> {
                val cw = dockRect.width() / COLS
                val rr = RectF(dockRect.left + t.slot * cw, dockRect.top, dockRect.left + (t.slot + 1) * cw, dockRect.bottom)
                rr.inset(cw * 0.08f, cw * 0.08f)
                rr
            }
        }
        return r
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

    private val tmpRect = RectF()
    private val wallDst = RectF()
    private val wallPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** Плашки папок рисуются здесь (под страницами), чтобы размытие совпадало с обоями при прокрутке. */
    private fun drawFolderPlates(c: Canvas) {
        val sc = if (editP > 0f) pages.scaleX else 1f
        for (item in items) {
            if (item !is FolderItem) continue
            if (dragging && item === dragItem) continue
            val x = gridL + item.col * cellW
            val y = gridT + item.row * cellH
            FolderView.plateRect(item.spanX, item.spanY, item.spanX * cellW, item.spanY * cellH, tmpRect)
            val radius = FolderView.plateRadius(item.spanX, item.spanY, tmpRect) * sc
            tmpRect.offset(x + item.page * width - pages.scrollX, y)
            if (editP > 0f) pages.matrix.mapRect(tmpRect)
            if (tmpRect.right < 0f || tmpRect.left > width) continue
            Backdrop.drawPlate(c, tmpRect, tmpRect, radius)
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val fade = 1f - editP

        // обои лаунчера (если выбраны); в режиме редактирования плавно переходят в размытые
        val wall = Backdrop.sharp
        if (wall != null) {
            wallDst.set(0f, 0f, w, h)
            wallPaint.alpha = 255
            c.drawBitmap(wall, null, wallDst, wallPaint)
            val bl = Backdrop.blurred
            if (editP > 0f && bl != null) {
                wallPaint.alpha = (255 * editP).toInt()
                c.drawBitmap(bl, null, wallDst, wallPaint)
                wallPaint.alpha = 255
            }
        }

        // затемнение в режиме редактирования (поверх размытых обоев)
        if (editP > 0f) c.drawColor(Color.argb((editP * 90).toInt(), 0, 0, 0))

        // плашки папок и док (размытые обои)
        drawFolderPlates(c)
        if (fade > 0f) Backdrop.drawPlate(c, dockRect, dockRect, dockRect.height() * 0.27f, fade)

        // подсветка цели перетаскивания
        highlight?.let { c.drawRoundRect(it, it.width() * 0.14f, it.width() * 0.14f, hlPaint) }

        // индикатор: "=" и точки страниц (в режиме редактирования скрыт)
        if (fade > 0f) {
            val n = pages.childCount.coerceAtLeast(1)
            val cy = h * 0.835f
            val total = n + 1
            val spacing = min(w * 0.051f, w * 0.8f / (total - 1).coerceAtLeast(1))
            val startX = w / 2f - (total - 1) * spacing / 2f
            val hw = w * 0.007f
            val g = w * 0.0035f
            linePaint.color = Color.argb((170 * fade).toInt(), 255, 255, 255)
            linePaint.strokeWidth = w * 0.0035f
            c.drawLine(startX - hw, cy - g, startX + hw, cy - g, linePaint)
            c.drawLine(startX - hw, cy + g, startX + hw, cy + g, linePaint)
            for (i in 0 until n) {
                dotPaint.alpha = ((if (i == currentPage) 255 else 90) * fade).toInt()
                c.drawCircle(startX + (i + 1) * spacing, cy, w * 0.007f, dotPaint)
            }
        }
    }

    override fun dispatchDraw(c: Canvas) {
        super.dispatchDraw(c)
        val b = dragBmp ?: return
        val k = 1.1f * (if (editP > 0f) pages.scaleX else 1f)
        c.save()
        c.translate(dragX, dragY)
        c.scale(k, k)
        c.drawBitmap(b, -b.width / 2f, -b.height / 2f, ghostPaint)
        c.restore()
    }
}
