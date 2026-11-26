package com.example.pixellauncher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.max

/**
 * Размытая копия обоев. Плашки папок, дока и панелей рисуют из неё свой кусок
 * (эффект матового стекла). Если обои прочитать нельзя — рисуется обычная полупрозрачная плашка.
 */
object Backdrop {
    @Volatile
    var blurred: Bitmap? = null
    private var screenW = 1f
    private var screenH = 1f

    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint()
    private val path = Path()
    private val matrix = Matrix()

    /** Читает обои и готовит размытую копию. Вызывать НЕ из UI-потока. */
    fun load(ctx: Context, sw: Int, sh: Int): Boolean {
        return try {
            val d = WallpaperManager.getInstance(ctx).drawable ?: return false
            var bmp = d.toBitmap()
            var copied = false
            if (bmp.config == Bitmap.Config.HARDWARE) {
                bmp = bmp.copy(Bitmap.Config.ARGB_8888, false) ?: return false
                copied = true
            }
            val out = makeBlur(bmp, sw, sh)
            if (copied) bmp.recycle()
            screenW = sw.toFloat()
            screenH = sh.toFloat()
            blurred = out
            true
        } catch (t: Throwable) {
            false
        }
    }

    private fun makeBlur(src: Bitmap, sw: Int, sh: Int): Bitmap {
        val bw = (sw / 8).coerceAtLeast(16)
        val bh = (sh / 8).coerceAtLeast(16)
        val small = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(small)
        // обои показываются по центру с заполнением экрана (center-crop)
        val s = max(bw / src.width.toFloat(), bh / src.height.toFloat())
        val m = Matrix()
        m.setScale(s, s)
        m.postTranslate((bw - src.width * s) / 2f, (bh - src.height * s) / 2f)
        c.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))

        val px = IntArray(bw * bh)
        small.getPixels(px, 0, bw, 0, 0, bw, bh)
        val tmp = IntArray(px.size)
        repeat(3) {
            pass(px, tmp, bw, bh, 4, true)
            pass(tmp, px, bw, bh, 4, false)
        }
        small.setPixels(px, 0, bw, 0, 0, bw, bh)
        return small
    }

    /** Один проход box-blur (скользящее окно по строкам или столбцам). */
    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horiz: Boolean) {
        val lines = if (horiz) h else w
        val len = if (horiz) w else h
        val div = 2 * r + 1
        for (l in 0 until lines) {
            var rs = 0
            var gs = 0
            var bs = 0
            for (i in -r..r) {
                val ci = i.coerceIn(0, len - 1)
                val p = if (horiz) src[l * w + ci] else src[ci * w + l]
                rs += (p shr 16) and 0xFF
                gs += (p shr 8) and 0xFF
                bs += p and 0xFF
            }
            for (i in 0 until len) {
                val o = if (horiz) l * w + i else i * w + l
                dst[o] = (0xFF shl 24) or ((rs / div) shl 16) or ((gs / div) shl 8) or (bs / div)
                val io = (i - r).coerceIn(0, len - 1)
                val ii = (i + r + 1).coerceIn(0, len - 1)
                val po = if (horiz) src[l * w + io] else src[io * w + l]
                val pi = if (horiz) src[l * w + ii] else src[ii * w + l]
                rs += ((pi shr 16) and 0xFF) - ((po shr 16) and 0xFF)
                gs += ((pi shr 8) and 0xFF) - ((po shr 8) and 0xFF)
                bs += (pi and 0xFF) - (po and 0xFF)
            }
        }
    }

    /**
     * Рисует плашку со скруглением в dst (координаты canvas).
     * screen — где эта область находится на экране (чтобы взять нужный кусок обоев).
     * alpha — общая прозрачность, dark — дополнительное затемнение 0..255.
     */
    fun drawPlate(c: Canvas, dst: RectF, screen: RectF, radius: Float, alpha: Float = 1f, dark: Int = 0) {
        if (alpha <= 0f) return
        val b = blurred
        c.save()
        path.reset()
        path.addRoundRect(dst, radius, radius, Path.Direction.CW)
        c.clipPath(path)
        if (b != null) {
            val k = if (screen.width() > 0f) dst.width() / screen.width() else 1f
            matrix.reset()
            matrix.setScale(k * screenW / b.width, k * screenH / b.height)
            matrix.postTranslate(dst.left - screen.left * k, dst.top - screen.top * k)
            bmpPaint.alpha = (255 * alpha).toInt()
            c.drawBitmap(b, matrix, bmpPaint)
        }
        if (dark > 0) {
            fillPaint.color = Color.argb((dark * alpha).toInt(), 0, 0, 0)
            c.drawRect(dst, fillPaint)
        }
        val tint = if (b != null) 40 else 64
        fillPaint.color = Color.argb((tint * alpha).toInt(), 255, 255, 255)
        c.drawRect(dst, fillPaint)
        c.restore()
    }
}
