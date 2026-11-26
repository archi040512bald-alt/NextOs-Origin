package com.example.pixellauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * Нижняя часть режима редактирования: счётчик страниц "2/6",
 * миниатюры страниц и панель действий. Тапы мимо элементов пропускаются дальше.
 */
class EditBar(ctx: Context) : View(ctx) {

    var items: List<Item> = emptyList()
    var pageCount = 1
    var current = 0
    var onPage: (Int) -> Unit = {}
    var onAction: (Int) -> Unit = {}

    private val labels = arrayOf("Обои", "Виджеты", "Анимации", "Настройки")

    private val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255) }
    private val plateSel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(140, 255, 255, 255) }
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val iconP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(240, 255, 255, 255) }
    private val glyphStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(90, 90, 100)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glyphFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(90, 90, 100) }
    private val bmpP = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val pill = RectF()
    private val panel = RectF()
    private val thumbs = ArrayList<Pair<Int, RectF>>()
    private val slots = ArrayList<RectF>()
    private val src = Rect()
    private val dst = RectF()
    private val tmp = RectF()
    private val path = Path()

    private var downKind = 0   // 0 — мимо, 1 — миниатюра, 2 — кнопка
    private var downIdx = -1

    private fun computeRects() {
        val w = width.toFloat()
        val h = height.toFloat()
        pill.set(w * 0.4375f, h * 0.609f, w * 0.5625f, h * 0.641f)

        thumbs.clear()
        val n = minOf(pageCount, 5)
        val first = (current - 2).coerceIn(0, (pageCount - n).coerceAtLeast(0))
        val tw = w * 0.128f
        val th = h * 0.135f
        val gap = w * 0.043f
        val total = n * tw + (n - 1).coerceAtLeast(0) * gap
        var x = (w - total) / 2f
        for (i in 0 until n) {
            thumbs.add((first + i) to RectF(x, h * 0.665f, x + tw, h * 0.665f + th))
            x += tw + gap
        }

        panel.set(w * 0.09f, h * 0.835f, w * 0.91f, h * 0.975f)
        slots.clear()
        val sw = panel.width() / 4f
        for (i in 0 until 4) {
            slots.add(RectF(panel.left + i * sw, panel.top, panel.left + (i + 1) * sw, panel.bottom))
        }
    }

    override fun onDraw(c: Canvas) {
        computeRects()
        val w = width.toFloat()

        // счётчик страниц
        c.drawRoundRect(pill, pill.height() / 2f, pill.height() / 2f, plate)
        textP.textSize = w * 0.03f
        c.drawText("${current + 1}/$pageCount", pill.centerX(), pill.centerY() - (textP.ascent() + textP.descent()) / 2f, textP)

        // миниатюры
        for ((idx, r) in thumbs) {
            val rad = r.width() * 0.16f
            c.drawRoundRect(r, rad, rad, if (idx == current) plateSel else plate)
            drawThumbContent(c, idx, r)
        }

        // панель действий
        c.drawRoundRect(panel, w * 0.06f, w * 0.06f, plate)
        val iconS = w * 0.065f
        textP.textSize = w * 0.03f
        for (i in 0 until 4) {
            val s = slots[i]
            val cx = s.centerX()
            drawIcon(c, i, cx, panel.top + panel.height() * 0.38f, iconS)
            c.drawText(labels[i], cx, panel.top + panel.height() * 0.82f, textP)
        }
    }

    private fun drawThumbContent(c: Canvas, page: Int, r: RectF) {
        val pad = r.width() * 0.1f
        val left = r.left + pad
        val top = r.top + pad * 1.2f
        val iw = r.width() - 2 * pad
        val ih = r.height() - 2 * pad * 1.2f
        val cw = iw / HomeLayout.COLS
        val ch = ih / HomeLayout.ROWS
        for (item in items) {
            if (item.page != page) continue
            val x = left + item.col * cw
            val y = top + item.row * ch
            when (item) {
                is AppItem -> {
                    val s = minOf(cw, ch) * 0.82f
                    val l = x + (cw - s) / 2f
                    val t = y + (ch - s) / 2f
                    dst.set(l, t, l + s, t + s)
                    src.set(0, 0, item.app.icon.width, item.app.icon.height)
                    c.drawBitmap(item.app.icon, src, dst, bmpP)
                }
                is FolderItem -> {
                    tmp.set(x + cw * 0.1f, y + ch * 0.1f, x + item.spanX * cw - cw * 0.1f, y + item.spanY * ch - ch * 0.1f)
                    c.drawRoundRect(tmp, cw * 0.3f, cw * 0.3f, plateSel)
                }
            }
        }
    }

    private fun drawIcon(c: Canvas, i: Int, cx: Float, cy: Float, s: Float) {
        when (i) {
            0 -> { // обои
                tmp.set(cx - s * 0.5f, cy - s * 0.42f, cx + s * 0.5f, cy + s * 0.42f)
                c.drawRoundRect(tmp, s * 0.18f, s * 0.18f, iconP)
                c.drawCircle(cx + s * 0.15f, cy - s * 0.1f, s * 0.1f, glyphFill)
            }
            1 -> { // виджеты
                c.drawCircle(cx, cy, s * 0.5f, iconP)
                glyphStroke.strokeWidth = s * 0.1f
                c.drawLine(cx - s * 0.2f, cy, cx + s * 0.2f, cy, glyphStroke)
                c.drawLine(cx, cy - s * 0.2f, cx, cy + s * 0.2f, glyphStroke)
            }
            2 -> { // анимации
                c.drawCircle(cx, cy, s * 0.5f, iconP)
                path.reset()
                path.moveTo(cx - s * 0.12f, cy - s * 0.2f)
                path.lineTo(cx - s * 0.12f, cy + s * 0.2f)
                path.lineTo(cx + s * 0.2f, cy)
                path.close()
                c.drawPath(path, glyphFill)
            }
            else -> { // настройки
                c.drawCircle(cx, cy, s * 0.5f, iconP)
                glyphStroke.strokeWidth = s * 0.1f
                c.drawCircle(cx, cy, s * 0.2f, glyphStroke)
            }
        }
    }

    /** kind: 1 — миниатюра (idx = номер страницы), 2 — кнопка (idx = номер), 0 — мимо */
    private fun hit(x: Float, y: Float) {
        downKind = 0
        downIdx = -1
        for ((idx, r) in thumbs) {
            if (r.contains(x, y)) {
                downKind = 1
                downIdx = idx
                return
            }
        }
        for (i in 0 until slots.size) {
            if (slots[i].contains(x, y)) {
                downKind = 2
                downIdx = i
                return
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                computeRects()
                hit(e.x, e.y)
                return downKind != 0 // мимо — пропускаем касание вниз
            }
            MotionEvent.ACTION_UP -> {
                val k = downKind
                val i = downIdx
                hit(e.x, e.y)
                if (k != 0 && k == downKind && i == downIdx) {
                    if (k == 1) onPage(i) else onAction(i)
                }
                downKind = 0
                downIdx = -1
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                downKind = 0
                downIdx = -1
            }
        }
        return downKind != 0
    }
}
