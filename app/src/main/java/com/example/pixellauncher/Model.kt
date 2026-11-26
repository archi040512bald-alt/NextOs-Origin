package com.example.pixellauncher

import android.graphics.Bitmap

class AppInfo(val label: String, val pkg: String, val cls: String, val icon: Bitmap)

sealed class Item(val col: Int, val row: Int, val spanX: Int, val spanY: Int, val page: Int)

class AppItem(val app: AppInfo, col: Int, row: Int, page: Int = 0) : Item(col, row, 1, 1, page)

class FolderItem(
    val title: String,
    val apps: List<AppInfo>,
    col: Int, row: Int, spanX: Int, spanY: Int,
    page: Int = 0
) : Item(col, row, spanX, spanY, page)
