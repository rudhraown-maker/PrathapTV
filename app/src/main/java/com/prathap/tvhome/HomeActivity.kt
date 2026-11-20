package com.prathap.tvhome

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** The TV home screen: phone/tablet-style tiles. */
class HomeActivity : Activity() {

    private data class AppEntry(
        val label: String, val pkg: String, val intent: Intent,
        val banner: Drawable?, val icon: Drawable?
    )

    private data class Pinned(val name: String, val color: String, val pkgs: List<String>)

    // Big cards at the top (shown even if not installed -> opens Play Store)
    private val streaming = listOf(
        Pinned("YouTube", "#E62117", listOf("com.google.android.youtube.tv", "com.google.android.youtube")),
        Pinned("Prime Video", "#00A8E1", listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient")),
        Pinned("Netflix", "#B20710", listOf("com.netflix.ninja", "com.netflix.mediaclient")),
    )

    private val browsers = listOf(
        Pinned("Chrome", "#1A73E8", listOf("com.android.chrome", "com.chrome.beta")),
        Pinned("Brave", "#FB542B", listOf("com.brave.browser")),
        Pinned("Opera", "#FF1B2D", listOf("com.opera.browser", "com.opera.mini.native")),
    )

    // Anything matching these goes into "More OTT"
    private val ottWords = listOf(
        "hotstar", "jio", "sony", "liv", "zee5", "zee", "aha", "sun nxt", "sunnxt", "mx player",
        "hoichoi", "eros", "altt", "alt balaji", "discovery", "apple tv", "disney", "mubi",
        "lionsgate", "shemaroo", "hungama", "airtel", "xstream", "tata play", "dangal", "voot",
        "plex", "kodi", "vlc", "spotify", "gaana", "wynk", "prime", "netflix", "youtube",
        "patchwall", "mi tv", "live tv", "chingari", "stage", "chaupal", "manorama", "etv win",
        "kooku", "ullu", "epic on", "docubay", "times play", "distro", "pluto", "tubi", "crunchyroll"
    )
    private val ottPkgHints = listOf(
        "hotstar", "jio", "sonyliv", "graymatrix", "zee5", "aha", "sunnxt", "mxtech", "hoichoi",
        "erosnow", "altbalaji", "discovery", "disney", "mubi", "lionsgate", "shemaroo", "hungama",
        "airtel", "tataplay", "tatasky", "viola", "plexapp", "xbmc", "videolan", "spotify"
    )

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var clock: TextView
    private lateinit var greeting: TextView
    private var firstFocus: View? = null
    private lateinit var bg: SlideshowView
    private var askedPhotoPermission = false

    // Photo screensaver after a few idle minutes on the home screen
    private val idle = Runnable {
        if (PhotoStore.list(this).isNotEmpty()) open(Intent(this, SlideshowActivity::class.java))
    }

    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 20_000)
        }
    }

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = buildContent()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.parseColor("#0B1020"),
                    Color.parseColor("#1E1B4B"),
                    Color.parseColor("#3B0F45")
                )
            )
        }
        scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            isVerticalScrollBarEnabled = false
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(28), dp(48), dp(40))
            clipChildren = false
            clipToPadding = false
        }

        // Header: greeting + clock
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        greeting = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 28f; typeface = Typeface.DEFAULT_BOLD
        }
        clock = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 28f; gravity = Gravity.END
        }
        header.addView(greeting, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(clock)
        page.addView(header)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        page.addView(content)
        scroll.addView(page)
        bg = SlideshowView(this, withClock = false, dim = 0.55f).apply { intervalMs = 20_000L }
        root.addView(bg, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(scroll)
        setContentView(root)
        SleepScheduler.schedule(this)

        buildContent()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, filter, Context.RECEIVER_EXPORTED)
        else registerReceiver(pkgReceiver, filter)

        // First start: settings; after power-on: dashboard straight away
        when {
            !Prefs.isSetUp(this) -> startActivity(Intent(this, SetupActivity::class.java))
            Prefs.autoOpen(this) && !Prefs.autoOpenedThisBoot(this) -> {
                Prefs.markAutoOpened(this)
                startActivity(Intent(this, DashboardActivity::class.java))
            }
            else -> Prefs.markAutoOpened(this)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
        if (Prefs.isSetUp(this) && !PhotoStore.hasPermission(this) && !askedPhotoPermission) {
            askedPhotoPermission = true
            requestPermissions(arrayOf(PhotoStore.permission()), 11)
        }
        refreshPhotos()
        resetIdle()
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        handler.removeCallbacks(idle)
        bg.stop()
        super.onPause()
    }

    override fun onDestroy() {
        try { unregisterReceiver(pkgReceiver) } catch (_: Exception) {}
        bg.release()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshPhotos()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        resetIdle()
        return super.dispatchKeyEvent(event)
    }

    private fun refreshPhotos() {
        if (Prefs.photoBackground(this)) {
            bg.visibility = View.VISIBLE
            bg.setPhotos(PhotoStore.list(this))
            bg.start()
        } else {
            bg.stop()
            bg.visibility = View.GONE
        }
    }

    private fun resetIdle() {
        handler.removeCallbacks(idle)
        val minutes = Prefs.saverMinutes(this)
        if (minutes > 0) handler.postDelayed(idle, minutes * 60_000L)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        scroll.smoothScrollTo(0, 0)
        firstFocus?.requestFocus()
    }

    @Deprecated("Launcher ignores BACK")
    override fun onBackPressed() {
        scroll.smoothScrollTo(0, 0)
        firstFocus?.requestFocus()
    }

    private fun updateClock() {
        clock.text = SimpleDateFormat("hh:mm a  ·  EEE, d MMM", Locale.getDefault()).format(Date())
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        greeting.text = when (h) {
            in 5..11 -> "Good morning, Prathap"
            in 12..16 -> "Good afternoon, Prathap"
            else -> "Good evening, Prathap"
        }
    }

    // ------------------------------------------------------------ content

    private fun buildContent() {
        content.removeAllViews()
        firstFocus = null
        val apps = loadApps()
        val used = HashSet<String>()

        // 1. Dashboard
        val top = grid(bigCols())
        top.addView(bigCard("Trading Dashboard", null, getDrawable(R.drawable.ic_dashboard),
            "#4F46E5") { open(Intent(this, DashboardActivity::class.java)) }.also { firstFocus = it })
        top.addView(bigCard("Family photos", null, getDrawable(R.drawable.ic_photo),
            "#BE185D") { open(Intent(this, SlideshowActivity::class.java)) })
        section("Home", top)

        // 2. YouTube, Prime Video, Netflix
        val stream = grid(bigCols())
        for (p in streaming) stream.addView(pinnedCard(p, apps, used, big = true))
        section("Streaming", stream)

        // 3. Remaining OTT apps installed on the TV
        val ott = apps.filter { it.pkg !in used && isOtt(it) }
        if (ott.isNotEmpty()) {
            val g = grid(smallCols())
            for (a in ott) { g.addView(appTile(a.label, a.icon ?: a.banner) { open(a.intent) }); used += a.pkg }
            section("More OTT", g)
        }

        // 4. HDMI inputs
        val inputs = grid(smallCols())
        val hdmi = loadHdmiInputs()
        if (hdmi.isNotEmpty()) {
            for ((label, intent) in hdmi) inputs.addView(appTile(label, getDrawable(R.drawable.ic_hdmi), "#0F766E") { open(intent) })
        } else {
            // TV did not list its ports – offer the TV's own input switcher
            for (n in 1..3) inputs.addView(appTile("HDMI $n", getDrawable(R.drawable.ic_hdmi), "#0F766E") { openInputPicker() })
        }
        section("HDMI / Inputs", inputs)

        // 5. Browsers
        val br = grid(smallCols())
        for (p in browsers) br.addView(pinnedCard(p, apps, used, big = false))
        section("Browsers", br)

        // 6. Everything else
        val rest = apps.filter { it.pkg !in used }
        val g = grid(smallCols())
        for (a in rest) g.addView(appTile(a.label, a.icon ?: a.banner) { open(a.intent) })
        g.addView(appTile("Prathap TV settings", getDrawable(R.drawable.ic_settings), "#334155") {
            open(Intent(this, SetupActivity::class.java))
        })
        g.addView(appTile("TV settings", getDrawable(R.drawable.ic_settings), "#334155") {
            open(Intent(android.provider.Settings.ACTION_SETTINGS))
        })
        section("All apps", g)

        firstFocus?.requestFocus()
    }

    private fun isOtt(a: AppEntry): Boolean {
        val l = a.label.lowercase(Locale.ROOT)
        val p = a.pkg.lowercase(Locale.ROOT)
        return ottWords.any { l.contains(it) } || ottPkgHints.any { p.contains(it) }
    }

    private fun pinnedCard(p: Pinned, apps: List<AppEntry>, used: MutableSet<String>, big: Boolean): View {
        val found = apps.firstOrNull { it.pkg in p.pkgs }
        if (found != null) used.add(found.pkg)
        val onClick: () -> Unit = {
            if (found != null) open(found.intent) else openStore(p.pkgs.first(), p.name)
        }
        return if (big) bigCard(p.name, found?.banner, found?.icon, p.color, onClick)
        else appTile(p.name, found?.icon ?: getDrawable(R.drawable.ic_globe),
            if (found == null) p.color else null, onClick)
    }

    // ------------------------------------------------------------ data

    private fun loadApps(): List<AppEntry> {
        val pm = packageManager
        val map = LinkedHashMap<String, AppEntry>()
        val tv = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
        for (ri in tv) {
            val pkg = ri.activityInfo.packageName
            if (pkg == packageName || map.containsKey(pkg)) continue
            val launch = pm.getLeanbackLaunchIntentForPackage(pkg) ?: continue
            map[pkg] = AppEntry(ri.loadLabel(pm).toString(), pkg, launch,
                safe { ri.activityInfo.loadBanner(pm) }, safe { ri.loadIcon(pm) })
        }
        // Phone-style apps (Chrome, Brave, Opera…) that were side-loaded
        val phone = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        for (ri in phone) {
            val pkg = ri.activityInfo.packageName
            if (pkg == packageName || map.containsKey(pkg)) continue
            val launch = pm.getLaunchIntentForPackage(pkg) ?: continue
            map[pkg] = AppEntry(ri.loadLabel(pm).toString(), pkg, launch, null, safe { ri.loadIcon(pm) })
        }
        return map.values.sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    private fun loadHdmiInputs(): List<Pair<String, Intent>> {
        val tim = getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
        return try {
            tim.tvInputList
                .filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId == null }
                .map { info ->
                    val custom = info.loadCustomLabel(this)?.toString()
                    val label = if (!custom.isNullOrBlank()) custom else info.loadLabel(this).toString()
                    label to Intent(Intent.ACTION_VIEW,
                        TvContract.buildChannelUriForPassthroughInput(info.id))
                }
                .sortedBy { it.first }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ------------------------------------------------------------ actions

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Can't open this on this TV", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Can't open this on this TV", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openStore(pkg: String, name: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
        } catch (_: Exception) {
            Toast.makeText(this, "$name isn't installed – install it from the Play Store or Downloader",
                Toast.LENGTH_LONG).show()
        }
    }

    /** Falls back to the TV's own live-TV / input app (Mi TV, Android TV). */
    private fun openInputPicker() {
        val candidates = listOf(
            "com.xiaomi.mitv.tvplayer", "com.mitv.tvplayer", "com.xiaomi.tv.player",
            "com.google.android.tv", "com.android.tv", "com.droidlogic.tvinput",
            "com.mediatek.wwtv.tvcenter", "com.android.tv.settings"
        )
        for (pkg in candidates) {
            val i = packageManager.getLeanbackLaunchIntentForPackage(pkg)
                ?: packageManager.getLaunchIntentForPackage(pkg) ?: continue
            open(i); return
        }
        Toast.makeText(this, "Press the INPUT / SOURCE button on the remote", Toast.LENGTH_LONG).show()
    }

    // ------------------------------------------------------------ UI helpers

    private fun section(title: String, body: View) {
        content.addView(TextView(this).apply {
            text = title; setTextColor(Color.WHITE); alpha = 0.85f; textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(22), 0, dp(4))
        })
        content.addView(body)
    }

    private fun usableWidthDp() = resources.configuration.screenWidthDp - 96
    private fun bigCols() = maxOf(1, usableWidthDp() / 228)
    private fun smallCols() = maxOf(1, usableWidthDp() / 128)

    private fun grid(cols: Int) = GridLayout(this).apply {
        columnCount = cols
        clipChildren = false
        clipToPadding = false
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius.toFloat()
    }

    private fun bigCard(label: String, banner: Drawable?, icon: Drawable?, color: String, onClick: () -> Unit): View {
        val card = FrameLayout(this).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = dp(208); height = dp(117); setMargins(dp(10), dp(10), dp(10), dp(10))
            }
            background = rounded(Color.parseColor(color), dp(18))
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            contentDescription = label
        }
        if (banner != null) {
            card.addView(ImageView(this).apply {
                setImageDrawable(banner); scaleType = ImageView.ScaleType.CENTER_CROP
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } else {
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            }
            if (icon != null) col.addView(ImageView(this).apply { setImageDrawable(icon) },
                LinearLayout.LayoutParams(dp(44), dp(44)))
            col.addView(TextView(this).apply {
                text = label; setTextColor(Color.WHITE); textSize = 18f
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
            card.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        focusify(card, card, onClick)
        return card
    }

    private fun appTile(label: String, icon: Drawable?, bgColor: String? = null, onClick: () -> Unit): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = GridLayout.LayoutParams().apply {
                width = dp(116); setMargins(dp(6), dp(8), dp(6), dp(8))
            }
            contentDescription = label
            clipChildren = false
        }
        val box = FrameLayout(this).apply {
            background = rounded(if (bgColor != null) Color.parseColor(bgColor) else 0x26FFFFFF, dp(24))
        }
        box.addView(ImageView(this).apply {
            setImageDrawable(icon); scaleType = ImageView.ScaleType.FIT_CENTER
        }, FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER))
        col.addView(box, LinearLayout.LayoutParams(dp(88), dp(88)))
        col.addView(TextView(this).apply {
            text = label; setTextColor(Color.WHITE); textSize = 13f; maxLines = 1
            ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        focusify(col, box, onClick)
        return col
    }

    private fun focusify(v: View, highlight: View, onClick: () -> Unit) {
        v.isFocusable = true
        v.isClickable = true
        v.setOnClickListener { onClick() }
        v.setOnFocusChangeListener { _, has ->
            val s = if (has) 1.1f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(130).start()
            (highlight.background as? GradientDrawable)?.setStroke(if (has) dp(3) else 0, Color.WHITE)
            v.elevation = if (has) dp(10).toFloat() else 0f
        }
    }

    private inline fun <T> safe(block: () -> T): T? = try { block() } catch (_: Exception) { null }
}
