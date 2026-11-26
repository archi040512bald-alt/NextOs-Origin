package com.example.pixellauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

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

    private val labels = arrayOf("Комплект", "Куб настройки", "Куб взаимодействия", "Настройки")

    private val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255) }
    private val plateSel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(140, 255, 255, 255) }
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
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
            val label = TextUtils.ellipsize(labels[i], textP, s.width() * 0.96f, TextUtils.TruncateAt.END).toString()
            c.drawText(label, cx, panel.top + panel.height() * 0.82f, textP)
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

    private fun fillFace(
        c: Canvas, color: Int,
        x1: Float, y1: Float, x2: Float, y2: Float,
        x3: Float, y3: Float, x4: Float, y4: Float
    ) {
        path.reset()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        path.lineTo(x3, y3)
        path.lineTo(x4, y4)
        path.close()
        facePaint.color = color
        c.drawPath(path, facePaint)
    }

    /** Изометрический куб: верхняя, левая и правая грани. */
    private fun drawCube(c: Canvas, cx: Float, cy: Float, r: Float, top: Int, left: Int, right: Int) {
        val dx = r * 0.866f
        fillFace(c, top, cx, cy - r, cx + dx, cy - r / 2, cx, cy, cx - dx, cy - r / 2)
        fillFace(c, left, cx - dx, cy - r / 2, cx, cy, cx, cy + r, cx - dx, cy + r / 2)
        fillFace(c, right, cx, cy, cx + dx, cy - r / 2, cx + dx, cy + r / 2, cx, cy + r)
    }

    private fun drawIcon(c: Canvas, i: Int, cx: Float, cy: Float, s: Float) {
        when (i) {
            0 -> { // Комплект: белый круг с плюсом
                c.drawCircle(cx, cy, s * 0.5f, iconP)
                glyphStroke.strokeWidth = s * 0.1f
                c.drawLine(cx - s * 0.2f, cy, cx + s * 0.2f, cy, glyphStroke)
                c.drawLine(cx, cy - s * 0.2f, cx, cy + s * 0.2f, glyphStroke)
            }
            1 -> drawCube( // Куб настройки: тёплый
                c, cx, cy, s * 0.56f,
                Color.rgb(255, 224, 190), Color.rgb(255, 170, 105), Color.rgb(238, 128, 78)
            )
            2 -> drawCube( // Куб взаимодействия: серый
                c, cx, cy, s * 0.56f,
                Color.rgb(240, 240, 246), Color.rgb(196, 198, 208), Color.rgb(150, 153, 166)
            )
            else -> { // Настройки: шестиугольник с отверстием
                path.reset()
                for (k in 0 until 6) {
                    val a = Math.toRadians(60.0 * k + 30.0).toFloat()
                    val x = cx + s * 0.54f * cos(a)
                    val y = cy + s * 0.54f * sin(a)
                    if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                c.drawPath(path, iconP)
                c.drawCircle(cx, cy, s * 0.2f, glyphFill)
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
