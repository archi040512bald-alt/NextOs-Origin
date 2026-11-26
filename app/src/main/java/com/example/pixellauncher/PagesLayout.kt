package com.example.pixellauncher

import android.animation.ValueAnimator
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Горизонтальный пейджер страниц рабочего стола (свайп + снап). */
class PagesLayout(ctx: Context) : ViewGroup(ctx) {

    var onPageChanged: (Int) -> Unit = {}
    var onTap: (() -> Unit)? = null
    var onScrolled: (() -> Unit)? = null
    var page = 0
        private set

    private val cfg = ViewConfiguration.get(ctx)
    private val slop = cfg.scaledTouchSlop
    private val minFling = cfg.scaledMinimumFlingVelocity
    private val maxFling = cfg.scaledMaximumFlingVelocity.toFloat()

    private var tracker: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var dragging = false
    private var anim: ValueAnimator? = null

    private fun maxScroll() = ((childCount - 1) * width).coerceAtLeast(0)

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        val h = MeasureSpec.getSize(hSpec)
        setMeasuredDimension(w, h)
        val cw = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
        val ch = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) getChildAt(i).measure(cw, ch)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) getChildAt(i).layout(i * width, 0, (i + 1) * width, height)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        onScrolled?.invoke()
        if (width > 0 && childCount > 0) {
            val p = (l / width.toFloat()).roundToInt().coerceIn(0, childCount - 1)
            if (p != page) {
                page = p
                onPageChanged(p)
            }
        }
    }

    private fun isHorizontalDrag(e: MotionEvent): Boolean {
        val dx = abs(e.rawX - downX)
        val dy = abs(e.rawY - downY)
        return dx > slop && dx > dy
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                lastX = e.rawX
                val moving = anim?.isRunning == true
                anim?.cancel()
                dragging = moving // поймать страницу на лету
                tracker?.recycle()
                tracker = VelocityTracker.obtain()
                tracker!!.addMovement(e)
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(e)
                if (!dragging && isHorizontalDrag(e)) {
                    dragging = true
                    lastX = e.rawX
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) {
                    tracker?.recycle()
                    tracker = null
                }
            }
        }
        return dragging
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (tracker == null) tracker = VelocityTracker.obtain()
        if (e.actionMasked != MotionEvent.ACTION_DOWN) tracker!!.addMovement(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && isHorizontalDrag(e)) {
                    dragging = true
                    lastX = e.rawX
                }
                if (dragging) {
                    val dx = lastX - e.rawX
                    lastX = e.rawX
                    scrollTo((scrollX + dx).roundToInt().coerceIn(0, maxScroll()), 0)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging && width > 0) {
                    tracker!!.computeCurrentVelocity(1000, maxFling)
                    val vx = tracker!!.xVelocity
                    val pos = scrollX / width.toFloat()
                    var target = pos.roundToInt()
                    if (abs(vx) > minFling * 4) {
                        target = if (vx < 0) ceil(pos).toInt() else floor(pos).toInt()
                    }
                    snapTo(target)
                } else if (!dragging && e.actionMasked == MotionEvent.ACTION_UP) {
                    onTap?.invoke()
                }
                dragging = false
                tracker?.recycle()
                tracker = null
            }
        }
        return true
    }

    fun snapTo(p: Int, animate: Boolean = true) {
        val t = p.coerceIn(0, (childCount - 1).coerceAtLeast(0)) * width
        anim?.cancel()
        if (!animate || width == 0) {
            scrollTo(t, 0)
            return
        }
        anim = ValueAnimator.ofInt(scrollX, t).apply {
            duration = 260
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { this@PagesLayout.scrollTo(it.animatedValue as Int, 0) }
            start()
        }
    }
}
