package com.example.pixellauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

object AppRepo {
    fun load(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { ri ->
                val system = (ri.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                AppInfo(
                    ri.loadLabel(pm).toString(),
                    ri.activityInfo.packageName,
                    ri.activityInfo.name,
                    IconShaper.shape(ri.loadIcon(pm)),
                    system
                )
            }
            .sortedBy { it.label.lowercase() }
    }
}
