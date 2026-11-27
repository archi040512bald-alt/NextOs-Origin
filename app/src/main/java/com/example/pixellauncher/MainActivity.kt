package com.example.pixellauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.view.WindowCompat

class MainActivity : Activity() {

    private companion object {
        const val REQ_WALLPAPER = 7
    }

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
        loadBackdrop()

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

    // ---- обои лаунчера и размытие ----

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(dm)
        return dm.widthPixels to dm.heightPixels
    }

    private fun loadBackdrop() {
        val (w, h) = screenSize()
        val ctx = applicationContext
        Thread {
            val ok = Backdrop.load(ctx, w, h)
            runOnUiThread { if (ok) home.invalidate() else maybeAskWallpaper() }
        }.start()
    }

    /** Один раз предлагает выбрать обои для лаунчера, если система не отдала свои. */
    private fun maybeAskWallpaper() {
        val prefs = getSharedPreferences("launcher", Context.MODE_PRIVATE)
        if (prefs.getBoolean("wp_asked", false)) return
        prefs.edit().putBoolean("wp_asked", true).apply()
        AlertDialog.Builder(this)
            .setTitle("Размытие под папками и доком")
            .setMessage("Система не даёт лаунчеру прочитать обои. Выбери картинку: она станет обоями лаунчера, и фон папок и дока будет размытым.")
            .setPositiveButton("Выбрать") { _, _ -> pickWallpaper() }
            .setNegativeButton("Позже", null)
            .show()
    }

    private fun pickWallpaper() {
        val i = Intent(Intent.ACTION_GET_CONTENT)
            .setType("image/*")
            .addCategory(Intent.CATEGORY_OPENABLE)
        try {
            startActivityForResult(Intent.createChooser(i, "Обои лаунчера"), REQ_WALLPAPER)
        } catch (_: Exception) {
            Toast.makeText(this, "Нет приложения для выбора картинки", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_WALLPAPER || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val (w, h) = screenSize()
        val ctx = applicationContext
        Thread {
            val ok = Backdrop.setFromUri(ctx, uri, w, h)
            runOnUiThread {
                if (ok) home.invalidate()
                else Toast.makeText(this, "Не удалось загрузить картинку: ${Backdrop.lastError}", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    // ---- приложения ----

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
            0 -> Toast.makeText(this, "Комплект — скоро", Toast.LENGTH_SHORT).show()
            1 -> Toast.makeText(this, "Куб настройки — скоро", Toast.LENGTH_SHORT).show()
            2 -> Toast.makeText(this, "Куб взаимодействия — скоро", Toast.LENGTH_SHORT).show()
            else -> AlertDialog.Builder(this)
                .setTitle("Настройки")
                .setItems(arrayOf<CharSequence>("Обои лаунчера", "Лаунчер по умолчанию")) { _, which ->
                    if (which == 0) pickWallpaper()
                    else try {
                        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                    } catch (_: Exception) {}
                }
                .show()
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
