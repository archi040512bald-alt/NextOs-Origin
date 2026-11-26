package com.example.pixellauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View

/** Иконка + подпись. Размеры считаются от ширины ячейки. */
class AppIconView(
    ctx: Context,
    val app: AppInfo,
    private val showLabel: Boolean = true
) : View(ctx) {

    /** 0..1 — прогресс режима редактирования (показывает бейдж удаления) */
    var editP = 0f
        set(value) {
            field = value
            invalidate()
        }
    var onBadge: ((AppInfo) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 0f, 1.5f, 0x66000000)
        runCatching {
            val fontId = ctx.resources.getIdentifier("origin_font", "font", ctx.packageName)
            if (fontId != 0) typeface = ctx.resources.getFont(fontId)
        }
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(70, 70, 80)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val src = Rect(0, 0, app.icon.width, app.icon.height)
    private val dst = RectF()
    private val badgeRect = RectF()
    private var badgeDown = false

    init { isClickable = true }

    private fun badgeActive() = showLabel && !app.system

    private fun badgeR() = width * ICON * 0.19f
    private fun badgeCx() = (width + width * ICON) / 2f - badgeR() * 0.4f
    private fun badgeCy() = badgeR()

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        pivotX = w / 2f
        pivotY = if (showLabel) w * ICON / 2f else h / 2f
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val s = w * ICON
        val top = if (showLabel) 0f else (height - s) / 2f
        dst.set((w - s) / 2f, top, (w + s) / 2f, top + s)
        c.drawBitmap(app.icon, src, dst, paint)
        if (showLabel) {
            text.textSize = w * 0.14f
            val label = TextUtils.ellipsize(app.label, text, w * 0.96f, TextUtils.TruncateAt.END).toString()
            c.drawText(label, w / 2f, s + w * 0.19f, text)
        }
        if (badgeActive() && editP > 0f) {
            val r = badgeR()
            val cx = badgeCx()
            val cy = badgeCy()
            badgePaint.alpha = (235 * editP).toInt()
            c.drawCircle(cx, cy, r, badgePaint)
            crossPaint.alpha = (255 * editP).toInt()
            crossPaint.strokeWidth = r * 0.18f
            val d = r * 0.38f
            c.drawLine(cx - d, cy - d, cx + d, cy + d, crossPaint)
            c.drawLine(cx - d, cy + d, cx + d, cy - d, crossPaint)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (badgeActive() && editP > 0.5f) {
            val r = badgeR()
            badgeRect.set(badgeCx() - r * 1.6f, badgeCy() - r * 1.6f, badgeCx() + r * 1.6f, badgeCy() + r * 1.6f)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> if (badgeRect.contains(e.x, e.y)) {
                    badgeDown = true
                    return true
                }
                MotionEvent.ACTION_UP -> if (badgeDown) {
                    badgeDown = false
                    if (badgeRect.contains(e.x, e.y)) onBadge?.invoke(app)
                    return true
                }
                MotionEvent.ACTION_CANCEL -> if (badgeDown) {
                    badgeDown = false
                    return true
                }
            }
            if (badgeDown) return true
        }
        return super.onTouchEvent(e)
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        val k = if (pressed) 0.92f else 1f
        animate().scaleX(k).scaleY(k).setDuration(120).start()
    }

    companion object { const val ICON = 0.655f }
}
