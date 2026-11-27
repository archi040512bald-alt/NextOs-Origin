package com.example.pixellauncher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Обои лаунчера и их размытая копия.
 * Плашки папок, дока и панелей рисуют из размытой копии свой кусок (матовое стекло).
 *
 * Источник обоев:
 *  1) картинка, выбранная пользователем в лаунчере (хранится у приложения, права не нужны);
 *  2) иначе — пробуем системные обои (на многих телефонах система это запрещает).
 */
object Backdrop {
    /** Обои лаунчера в размер экрана (null — рисуются системные обои). */
    @Volatile
    var sharp: Bitmap? = null

    @Volatile
    var blurred: Bitmap? = null

    /** Почему не удалось загрузить обои (для сообщения пользователю). */
    @Volatile
    var lastError: String = ""

    private var screenW = 1f
    private var screenH = 1f

    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint()
    private val path = Path()
    private val matrix = Matrix()

    private fun file(ctx: Context) = File(ctx.filesDir, "wallpaper.jpg")

    /** Загружает обои. Вызывать НЕ из UI-потока. */
    fun load(ctx: Context, sw: Int, sh: Int): Boolean {
        return try {
            val f = file(ctx)
            if (f.exists()) {
                val bmp = BitmapFactory.decodeFile(f.absolutePath)
                if (bmp != null) {
                    applyWallpaper(bmp, sw, sh)
                    return true
                }
            }
            loadSystem(ctx, sw, sh)
        } catch (t: Throwable) {
            lastError = (t.javaClass.simpleName + ": " + (t.message ?: "")).take(120)
            false
        }
    }

    /** Берёт картинку по uri как обои лаунчера и сохраняет её. НЕ из UI-потока. */
    fun setFromUri(ctx: Context, uri: Uri, sw: Int, sh: Int): Boolean {
        return try {
            val src = decodeUri(ctx, uri, max(sw, sh))
            if (src == null) {
                lastError = "не удалось открыть картинку"
                return false
            }
            applyWallpaper(src, sw, sh)
            val s = sharp ?: return false
            FileOutputStream(file(ctx)).use { s.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            true
        } catch (t: Throwable) {
            lastError = (t.javaClass.simpleName + ": " + (t.message ?: "")).take(120)
            false
        }
    }

    private fun decodeUri(ctx: Context, uri: Uri, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    /** Обрезает картинку по центру под пропорции экрана и готовит размытую копию. */
    private fun applyWallpaper(src: Bitmap, sw: Int, sh: Int) {
        val target = sw.toFloat() / sh
        val sa = src.width.toFloat() / src.height
        val cw: Int
        val ch: Int
        if (sa > target) {
            cw = (src.height * target).toInt().coerceAtLeast(1)
            ch = src.height
        } else {
            cw = src.width
            ch = (src.width / target).toInt().coerceAtLeast(1)
        }
        val x = (src.width - cw) / 2
        val y = (src.height - ch) / 2
        val cropped = Bitmap.createBitmap(src, x, y, cw, ch)
        val fin = if (cropped.width == sw && cropped.height == sh) cropped
        else Bitmap.createScaledBitmap(cropped, sw, sh, true)

        val blur = makeBlur(fin, sw, sh)
        screenW = sw.toFloat()
        screenH = sh.toFloat()
        blurred = blur
        sharp = fin
    }

    /** Запасной вариант: системные обои (нужен доступ, на многих телефонах недоступен). */
    private fun loadSystem(ctx: Context, sw: Int, sh: Int): Boolean {
        val d = WallpaperManager.getInstance(ctx).drawable
        if (d == null) {
            lastError = "система не отдаёт обои"
            return false
        }
        var bmp = d.toBitmap()
        var copied = false
        if (bmp.config == Bitmap.Config.HARDWARE) {
            val copy = bmp.copy(Bitmap.Config.ARGB_8888, false)
            if (copy == null) {
                lastError = "не хватило памяти под обои"
                return false
            }
            bmp = copy
            copied = true
        }
        val out = makeBlur(bmp, sw, sh)
        if (copied) bmp.recycle()
        screenW = sw.toFloat()
        screenH = sh.toFloat()
        blurred = out
        sharp = null
        return true
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
