package com.example.pixellauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.FrameLayout
import androidx.core.view.WindowCompat

class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var home: HomeLayout
    private var overlay: FolderOverlay? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false

        root = FrameLayout(this)
        home = HomeLayout(this)
        root.addView(home, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        // TODO: грузить в фоне и обновлять по PACKAGE_ADDED/REMOVED
        val apps = AppRepo.load(this)
        val (items, dock) = DefaultLayout.build(apps)
        home.onApp = ::launch
        home.onFolder = ::openFolder
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // лаунчер не закрывается по Back; закрываем только папку
        closeFolder()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        closeFolder() // нажатие Home
    }
}
