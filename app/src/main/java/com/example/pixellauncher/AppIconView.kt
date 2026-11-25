package com.example.pixellauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.view.View

/** Иконка + подпись. Размеры считаются от ширины ячейки. */
class AppIconView(
    ctx: Context,
    val app: AppInfo,
    private val showLabel: Boolean = true
) : View(ctx) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 0f, 1.5f, 0x66000000)
    }
    private val src = Rect(0, 0, app.icon.width, app.icon.height)
    private val dst = RectF()

    init { isClickable = true }

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
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        val k = if (pressed) 0.92f else 1f
        animate().scaleX(k).scaleY(k).setDuration(120).start()
    }

    companion object { const val ICON = 0.655f }
}
