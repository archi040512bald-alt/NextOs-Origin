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

/** Папка на рабочем столе: большая (2x2, превью 2x2 или 3x3) или маленькая (1x1, превью 3x3). */
class FolderView(ctx: Context, val folder: FolderItem) : View(ctx) {

    private val big = folder.spanX > 1 || folder.spanY > 1
    private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HomeLayout.PLATE }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 0f, 1.5f, 0x66000000)
    }
    private val plate = RectF()
    private val dst = RectF()

    init { isClickable = true }

    private fun cellW() = width / folder.spanX.toFloat()

    private fun updatePlate() {
        val w = width.toFloat()
        if (big) {
            val inset = cellW() * 0.175f
            plate.set(inset, 0f, w - inset, height - cellW() * 0.515f)
        } else {
            val s = w * AppIconView.ICON
            plate.set((w - s) / 2f, 0f, (w + s) / 2f, s)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        updatePlate()
        pivotX = w / 2f
        pivotY = plate.centerY()
    }

    override fun onDraw(c: Canvas) {
        updatePlate()
        val pw = plate.width()
        val ph = plate.height()
        val r = if (big) 0.14f * minOf(pw, ph) else 0.27f * pw
        c.drawRoundRect(plate, r, r, platePaint)

        // параметры превью: k x k иконок
        val k: Int
        val s: Float
        val pad: Float
        when {
            !big -> { k = 3; s = pw * 0.22f; pad = pw * 0.115f }
            folder.apps.size <= 4 -> { k = 2; s = pw * 0.378f; pad = pw * 0.075f }
            else -> { k = 3; s = pw * 0.262f; pad = pw * 0.056f }
        }
        val gapX = (pw - 2 * pad - k * s) / (k - 1)
        val gapY = (ph - 2 * pad - k * s) / (k - 1)

        folder.apps.take(k * k).forEachIndexed { i, app ->
            val x = plate.left + pad + (i % k) * (s + gapX)
            val y = plate.top + pad + (i / k) * (s + gapY)
            dst.set(x, y, x + s, y + s)
            c.drawBitmap(app.icon, Rect(0, 0, app.icon.width, app.icon.height), dst, iconPaint)
        }

        text.textSize = cellW() * 0.14f
        val label = TextUtils.ellipsize(folder.title, text, plate.width(), TextUtils.TruncateAt.END).toString()
        c.drawText(label, width / 2f, plate.bottom + cellW() * 0.18f, text)
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        val k = if (pressed) 0.94f else 1f
        animate().scaleX(k).scaleY(k).setDuration(120).start()
    }
}
