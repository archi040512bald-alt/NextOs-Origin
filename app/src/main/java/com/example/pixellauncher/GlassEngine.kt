package com.example.pixellauncher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Быстрый кэшированный рендер матового стекла OriginOS без нагрузки на FPS.
 * Размывает обои один раз при загрузке/смене и рисует их через GPU BitmapShader.
 */
object GlassEngine {

    private var shader: BitmapShader? = null
    private var isDarkWallpaper = true
    private var bmpW = 0
    private var bmpH = 0

    private val matrix = Matrix()
    private val loc = IntArray(2)

    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    fun update(ctx: Context) {
        try {
            val wm = WallpaperManager.getInstance(ctx)
            val drawable = wm.drawable ?: return
            val dm = ctx.resources.displayMetrics
            // Сжимаем в 10 раз для мгновенного блюра и экономии памяти (~100 КБ)
            val targetW = max(64, dm.widthPixels / 10)
            val targetH = max(64, dm.heightPixels / 10)

            val small = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            val c = Canvas(small)
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                val src = drawable.bitmap
                val scale = max(targetW.toFloat() / src.width, targetH.toFloat() / src.height)
                val dx = (targetW - src.width * scale) / 2f
                val dy = (targetH - src.height * scale) / 2f
                val m = Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(dx, dy)
                }
                c.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
            } else {
                drawable.setBounds(0, 0, targetW, targetH)
                drawable.draw(c)
            }

            isDarkWallpaper = detectDark(small)
            val blurred = fastBlur(small, 12)
            small.recycle()

            bmpW = blurred.width
            bmpH = blurred.height
            shader = BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            blurPaint.shader = shader
        } catch (_: SecurityException) {
            // Если система на Android 13+ не дала доступ без разрешения — работаем в режиме fallback
            shader = null
        } catch (_: Exception) {
            shader = null
        }
    }

    /** Рисует стеклянную плашку по координатам View на экране. */
    fun drawGlass(c: Canvas, view: View, rect: RectF, radius: Float, alphaFactor: Float = 1f) {
        if (alphaFactor <= 0.01f) return
        val s = shader
        val rootW = view.rootView.width.toFloat()
        val rootH = view.rootView.height.toFloat()

        if (s != null && bmpW > 0 && bmpH > 0 && rootW > 0f && rootH > 0f) {
            view.getLocationOnScreen(loc)
            matrix.reset()
            matrix.setScale(rootW / bmpW, rootH / bmpH)
            matrix.postTranslate(-loc[0].toFloat(), -loc[1].toFloat())
            s.setLocalMatrix(matrix)

            blurPaint.alpha = (255 * alphaFactor).toInt()
            c.drawRoundRect(rect, radius, radius, blurPaint)

            // Адаптивная тонировка поверх размытых обоев (как в OriginOS)
            tintPaint.color = if (isDarkWallpaper) {
                Color.argb((85 * alphaFactor).toInt(), 24, 24, 34)
            } else {
                Color.argb((72 * alphaFactor).toInt(), 255, 255, 255)
            }
            c.drawRoundRect(rect, radius, radius, tintPaint)
        } else {
            // Запасной режим, если доступ к обоям закрыт системой
            tintPaint.color = Color.argb((76 * alphaFactor).toInt(), 255, 255, 255)
            c.drawRoundRect(rect, radius, radius, tintPaint)
        }

        // Тонкий световой блик по краю стекла (Rim Light)
        rimPaint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(
                Color.argb((95 * alphaFactor).toInt(), 255, 255, 255),
                Color.argb((20 * alphaFactor).toInt(), 255, 255, 255),
                Color.argb((55 * alphaFactor).toInt(), 255, 255, 255)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRoundRect(rect, radius, radius, rimPaint)
    }

    private fun detectDark(b: Bitmap): Boolean {
        var lumSum = 0.0
        var count = 0
        for (x in 0 until b.width step 4) {
            for (y in 0 until b.height step 4) {
                val p = b.getPixel(x, y)
                val lum = 0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)
                lumSum += lum
                count++
            }
        }
        return if (count > 0) (lumSum / count) < 135 else true
    }

    /** Сверхбыстрый 3-проходный Box Blur (заменяет тяжелый Gaussian Blur за ~3 мс). */
    private fun fastBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)

        repeat(3) {
            boxBlurH(pix, tmp, w, h, radius)
            boxBlurV(tmp, pix, w, h, radius)
        }

        return Bitmap.createBitmap(pix, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun boxBlurH(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = r * 2 + 1
        for (y in 0 until h) {
            val rowOff = y * w
            var rSum = 0; var gSum = 0; var bSum = 0
            for (i in -r..r) {
                val p = src[rowOff + i.coerceIn(0, w - 1)]
                rSum += Color.red(p); gSum += Color.green(p); bSum += Color.blue(p)
            }
            for (x in 0 until w) {
                dst[rowOff + x] = Color.rgb(rSum / div, gSum / div, bSum / div)
                val pOut = src[rowOff + (x - r).coerceIn(0, w - 1)]
                val pIn = src[rowOff + (x + r + 1).coerceIn(0, w - 1)]
                rSum += Color.red(pIn) - Color.red(pOut)
                gSum += Color.green(pIn) - Color.green(pOut)
                bSum += Color.blue(pIn) - Color.blue(pOut)
            }
        }
    }

    private fun boxBlurV(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = r * 2 + 1
        for (x in 0 until w) {
            var rSum = 0; var gSum = 0; var bSum = 0
            for (i in -r..r) {
                val p = src[i.coerceIn(0, h - 1) * w + x]
                rSum += Color.red(p); gSum += Color.green(p); bSum += Color.blue(p)
            }
            for (y in 0 until h) {
                dst[y * w + x] = Color.rgb(rSum / div, gSum / div, bSum / div)
                val pOut = src[(y - r).coerceIn(0, h - 1) * w + x]
                val pIn = src[(y + r + 1).coerceIn(0, h - 1) * w + x]
                rSum += Color.red(pIn) - Color.red(pOut)
                gSum += Color.green(pIn) - Color.green(pOut)
                bSum += Color.blue(pIn) - Color.blue(pOut)
            }
        }
    }
}
