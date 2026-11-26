package com.example.pixellauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable

/** Приводит все иконки к единой форме (скруглённый квадрат) без искусственного белого фона. */
object IconShaper {
    const val SIZE = 192
    private const val RADIUS = 0.30f

    fun shape(d: Drawable): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = SIZE.toFloat()
        val path = Path().apply {
            addRoundRect(RectF(0f, 0f, s, s), s * RADIUS, s * RADIUS, Path.Direction.CW)
        }
        c.clipPath(path)

        if (d is AdaptiveIconDrawable) {
            // слои 108dp, видимая часть 72dp
            val layer = (SIZE * 108f / 72f).toInt()
            val off = -(layer - SIZE) / 2
            d.background?.let { it.setBounds(off, off, off + layer, off + layer); it.draw(c) }
            d.foreground?.let { it.setBounds(off, off, off + layer, off + layer); it.draw(c) }
        } else {
            // Рисуем на всю площадь скругленного квадрата без заливки фона
            d.setBounds(0, 0, SIZE, SIZE)
            d.draw(c)
        }
        return bmp
    }
}
