package com.example.pixellauncher

/**
 * Раскладка как на референсе. Приложения подбираются по пакетам;
 * если нужного нет — ячейка заполняется любым свободным приложением.
 */
object DefaultLayout {

    private class Picker(private val all: List<AppInfo>) {
        private val byPkg = all.associateBy { it.pkg }
        private val used = HashSet<String>()

        fun take(vararg pkgs: String): AppInfo? {
            for (p in pkgs) {
                val a = byPkg[p] ?: continue
                if (used.add(p)) return a
            }
            return null
        }

        fun peek(vararg pkgs: String): List<AppInfo> = pkgs.mapNotNull { byPkg[it] }

        fun fill(n: Int): List<AppInfo> {
            if (n <= 0) return emptyList()
            val r = all.filter { it.pkg !in used }.take(n)
            r.forEach { used.add(it.pkg) }
            return r
        }
    }

    fun build(all: List<AppInfo>): Pair<List<Item>, List<AppInfo>> {
        val p = Picker(all)

        // сначала именованные
        val dockNamed = listOf(
            p.take("com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer", "com.android.contacts"),
            p.take("com.whatsapp"),
            p.take("com.android.chrome"),
            p.take("com.android.camera", "com.android.camera2", "com.google.android.GoogleCamera", "com.sec.android.app.camera", "com.oplus.camera")
        )
        val sugNamed = listOf(
            p.take("com.reddit.frontpage"),
            p.take("com.google.android.youtube"),
            p.take("com.instagram.android"),
            p.take("com.google.android.apps.maps")
        )
        val essNamed = listOf(
            p.take("com.android.settings"),
            p.take("com.google.android.calendar", "com.android.calendar"),
            p.take("com.google.android.deskclock", "com.android.deskclock"),
            p.take("com.google.android.apps.photos", "com.miui.gallery", "com.google.android.apps.nbu.files"),
            p.take("com.google.android.keep", "com.miui.notes")
        )
        val chat = p.take("com.openai.chatgpt")
        val spot = p.take("com.spotify.music")
        val store = p.take("com.android.vending")
        val google = p.peek(
            "com.android.chrome", "com.google.android.gm", "com.google.android.youtube",
            "com.google.android.apps.maps", "com.google.android.apps.docs",
            "com.google.android.googlequicksearchbox", "com.google.android.apps.photos",
            "com.google.android.apps.tachyon", "com.google.android.apps.lens"
        )

        // потом заполнение пустых мест
        val dock = dockNamed.mapNotNull { it ?: p.fill(1).firstOrNull() }
        val sug = sugNamed.filterNotNull().let { it + p.fill(4 - it.size) }
        val ess = essNamed.filterNotNull().let { it + p.fill(9 - it.size) }

        val items = ArrayList<Item>()
        if (sug.isNotEmpty()) items.add(FolderItem("App suggestions", sug, 0, 3, 2, 2))
        if (ess.isNotEmpty()) items.add(FolderItem("Essentials", ess, 2, 3, 2, 2))
        (chat ?: p.fill(1).firstOrNull())?.let { items.add(AppItem(it, 0, 5)) }
        (spot ?: p.fill(1).firstOrNull())?.let { items.add(AppItem(it, 1, 5)) }
        if (google.isNotEmpty()) items.add(FolderItem("Google", google, 2, 5, 1, 1))
        else p.fill(1).firstOrNull()?.let { items.add(AppItem(it, 2, 5)) }
        (store ?: p.fill(1).firstOrNull())?.let { items.add(AppItem(it, 3, 5)) }

        return items to dock
    }
}
