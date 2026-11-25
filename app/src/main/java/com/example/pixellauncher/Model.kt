package com.example.pixellauncher

import android.graphics.Bitmap

class AppInfo(val label: String, val pkg: String, val cls: String, val icon: Bitmap)

sealed class Item(val col: Int, val row: Int, val spanX: Int, val spanY: Int)

class AppItem(val app: AppInfo, col: Int, row: Int) : Item(col, row, 1, 1)

class FolderItem(
    val title: String,
    val apps: List<AppInfo>,
    col: Int, row: Int, spanX: Int, spanY: Int
) : Item(col, row, spanX, spanY)
