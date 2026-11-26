package com.example.pixellauncher

import android.graphics.Bitmap

class AppInfo(
    val label: String,
    val pkg: String,
    val cls: String,
    val icon: Bitmap,
    val system: Boolean = false
)

sealed class Item(var col: Int, var row: Int, val spanX: Int, val spanY: Int, var page: Int)

class AppItem(val app: AppInfo, col: Int, row: Int, page: Int = 0) : Item(col, row, 1, 1, page)

class FolderItem(
    val title: String,
    val apps: List<AppInfo>,
    col: Int, row: Int, spanX: Int, spanY: Int,
    page: Int = 0
) : Item(col, row, spanX, spanY, page)
