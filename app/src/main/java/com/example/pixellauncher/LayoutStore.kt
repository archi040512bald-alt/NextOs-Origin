package com.example.pixellauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Сохраняет расположение иконок (после перетаскивания) и восстанавливает при запуске. */
object LayoutStore {
    private const val KEY = "layout_v1"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun save(ctx: Context, items: List<Item>, dock: List<AppInfo>) {
        val arr = JSONArray()
        for (item in items) {
            val o = JSONObject()
                .put("page", item.page)
                .put("col", item.col)
                .put("row", item.row)
            when (item) {
                is AppItem -> o.put("t", "a").put("k", item.app.pkg)
                is FolderItem -> o.put("t", "f").put("k", item.title)
            }
            arr.put(o)
        }
        val d = JSONArray()
        dock.forEach { d.put(it.pkg) }
        val root = JSONObject().put("items", arr).put("dock", d)
        prefs(ctx).edit().putString(KEY, root.toString()).apply()
    }

    fun restore(
        ctx: Context,
        all: List<AppInfo>,
        defItems: List<Item>,
        defDock: List<AppInfo>
    ): Pair<List<Item>, List<AppInfo>> {
        val s = prefs(ctx).getString(KEY, null) ?: return defItems to defDock
        return try {
            parse(s, all, defItems)
        } catch (e: Exception) {
            defItems to defDock
        }
    }

    private fun cellKey(p: Int, c: Int, r: Int) = p * 1000 + r * 10 + c

    private fun parse(
        s: String,
        all: List<AppInfo>,
        defItems: List<Item>
    ): Pair<List<Item>, List<AppInfo>> {
        val root = JSONObject(s)
        val byPkg = all.associateBy { it.pkg }
        val folders = defItems.filterIsInstance<FolderItem>().associateBy { it.title }

        val items = ArrayList<Item>()
        val arr = root.getJSONArray("items")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val k = o.getString("k")
            val page = o.getInt("page")
            val col = o.getInt("col")
            val row = o.getInt("row")
            if (o.getString("t") == "a") {
                val a = byPkg[k] ?: continue
                items.add(AppItem(a, col, row, page))
            } else {
                val f = folders[k] ?: continue
                items.add(FolderItem(f.title, f.apps, col, row, f.spanX, f.spanY, page))
            }
        }

        val dock = ArrayList<AppInfo>()
        val d = root.getJSONArray("dock")
        for (i in 0 until d.length()) byPkg[d.getString(i)]?.let { dock.add(it) }

        // новые приложения (установлены после сохранения) — в первые свободные ячейки со страницы 1
        val shown = HashSet<String>()
        dock.forEach { shown.add(it.pkg) }
        for (item in items) {
            when (item) {
                is AppItem -> shown.add(item.app.pkg)
                is FolderItem -> item.apps.forEach { shown.add(it.pkg) }
            }
        }
        val occ = HashSet<Int>()
        for (item in items) {
            for (dy in 0 until item.spanY) for (dx in 0 until item.spanX) {
                occ.add(cellKey(item.page, item.col + dx, item.row + dy))
            }
        }
        val perPage = HomeLayout.COLS * HomeLayout.ROWS
        var idx = 0
        for (app in all.filter { it.pkg !in shown }) {
            while (true) {
                val p = 1 + idx / perPage
                val c = idx % HomeLayout.COLS
                val r = (idx / HomeLayout.COLS) % HomeLayout.ROWS
                idx++
                if (cellKey(p, c, r) !in occ) {
                    occ.add(cellKey(p, c, r))
                    items.add(AppItem(app, c, r, p))
                    break
                }
            }
        }
        return items to dock
    }
}
