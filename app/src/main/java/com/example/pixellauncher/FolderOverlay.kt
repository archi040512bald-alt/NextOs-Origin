package com.example.pixellauncher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.ceil
import kotlin.math.min

/** Открытая папка: затемнение + панель с иконками (3 колонки). */
class FolderOverlay(
    ctx: Context,
    folder: FolderItem,
    onApp: (AppInfo) -> Unit,
    onClose: () -> Unit
) : FrameLayout(ctx) {

    private val panel: LinearLayout

    init {
        setBackgroundColor(0x99000000.toInt())
        setOnClickListener { onClose() }

        val dm = resources.displayMetrics
        val panelW = (dm.widthPixels * 0.88f).toInt()
        val pad = (panelW * 0.05f).toInt()
        val cols = 3
        val cellW = (panelW - 2 * pad) / cols
        val cellH = (cellW * 1.15f).toInt()

        panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            isClickable = true // не пропускать тапы на фон
            background = GradientDrawable().apply {
                setColor(0xE62B2A3A.toInt())
                cornerRadius = panelW * 0.07f
            }
        }

        val title = TextView(ctx).apply {
            text = folder.title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, pad / 2)
        }
        panel.addView(title, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val grid = GridLayout(ctx).apply { columnCount = cols }
        for (a in folder.apps) {
            val v = AppIconView(ctx, a)
            v.setOnClickListener { onApp(a) }
            val lp = GridLayout.LayoutParams()
            lp.width = cellW
            lp.height = cellH
            grid.addView(v, lp)
        }

        val rows = ceil(folder.apps.size / cols.toFloat()).toInt()
        val scrollH = min(rows * cellH, (dm.heightPixels * 0.6f).toInt())
        val scroll = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false }
        scroll.addView(grid)
        panel.addView(scroll, LinearLayout.LayoutParams(cellW * cols, scrollH))

        addView(panel, LayoutParams(panelW, LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        panel.alpha = 0f
        panel.scaleX = 0.88f
        panel.scaleY = 0.88f
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).start()
    }
}
