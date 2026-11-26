package com.example.pixellauncher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.view.WindowCompat

class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var home: HomeLayout
    private var overlay: FolderOverlay? = null

    private val reloadRunnable = Runnable {
        closeFolder()
        loadApps()
    }

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // при обновлении приложения приходит REMOVED+ADDED — пропускаем лишнее
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED &&
                intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            ) return
            home.removeCallbacks(reloadRunnable)
            home.postDelayed(reloadRunnable, 400)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // системные панели полностью прозрачные, без затемнения
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 28) window.navigationBarDividerColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        if (Build.VERSION.SDK_INT >= 30) window.isStatusBarContrastEnforced = false

        root = FrameLayout(this)
        home = HomeLayout(this)
        root.addView(home, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        home.onApp = ::launch
        home.onFolder = ::openFolder
        home.onChanged = { i, d -> LayoutStore.save(this, i, d) }
        home.onBlur = { r -> if (Build.VERSION.SDK_INT >= 31) window.setBackgroundBlurRadius(r) }
        home.onAction = ::onEditAction
        home.onUninstall = ::uninstall

        loadApps()

        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED)
        else registerReceiver(pkgReceiver, f)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(pkgReceiver) } catch (_: Exception) {}
    }

    private fun loadApps() {
        val apps = AppRepo.load(this)
        val (defItems, defDock) = DefaultLayout.build(apps)
        val (items, dock) = LayoutStore.restore(this, apps, defItems, defDock)
        home.setContent(items, dock)
    }

    private fun launch(a: AppInfo) {
        val i = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(a.pkg, a.cls)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try { startActivity(i) } catch (_: Exception) {}
        closeFolder()
    }

    private fun uninstall(a: AppInfo) {
        try {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}")))
        } catch (_: Exception) {
            Toast.makeText(this, "Не удалось удалить", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onEditAction(i: Int) {
        when (i) {
            0 -> try {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Обои"))
            } catch (_: Exception) {}
            1 -> Toast.makeText(this, "Виджеты — скоро", Toast.LENGTH_SHORT).show()
            2 -> Toast.makeText(this, "Анимации — скоро", Toast.LENGTH_SHORT).show()
            else -> try {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            } catch (_: Exception) {}
        }
    }

    private fun openFolder(f: FolderItem) {
        closeFolder()
        overlay = FolderOverlay(this, f, ::launch, ::closeFolder).also {
            root.addView(it, FrameLayout.LayoutParams(-1, -1))
        }
    }

    private fun closeFolder() {
        overlay?.let { root.removeView(it) }
        overlay = null
    }

    private fun back() {
        when {
            overlay != null -> closeFolder()
            home.editing -> home.exitEdit()
            else -> home.goToFirstPage()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = back()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        back() // нажатие Home
    }
}
