package com.prathap.tvhome

import android.content.Context
import android.os.SystemClock
import kotlin.math.abs

/** Settings saved privately on the TV (only this app can read them). */
object Prefs {
    const val DEFAULT_URL = "https://13-232-196-141.sslip.io/algo"
    const val DEFAULT_USER = ""

    /** Set once per power-on so the dashboard auto-opens only at startup. */
    @Volatile var autoOpenedInProcess = false

    private fun p(c: Context) = c.getSharedPreferences("tvhome", Context.MODE_PRIVATE)

    fun url(c: Context): String = p(c).getString("url", DEFAULT_URL) ?: DEFAULT_URL
    fun user(c: Context): String = p(c).getString("user", DEFAULT_USER) ?: DEFAULT_USER
    fun pass(c: Context): String = p(c).getString("pass", "") ?: ""
    fun isSetUp(c: Context): Boolean = p(c).getBoolean("setup", false)
    fun autoOpen(c: Context): Boolean = p(c).getBoolean("autoOpen", true)
    fun autoLogin(c: Context): Boolean = p(c).getBoolean("autoLogin", true)

    fun save(c: Context, url: String, user: String, pass: String, autoOpen: Boolean, autoLogin: Boolean) {
        p(c).edit()
            .putString("url", url.trim().ifEmpty { DEFAULT_URL })
            .putString("user", user.trim())
            .putString("pass", pass)
            .putBoolean("autoOpen", autoOpen)
            .putBoolean("autoLogin", autoLogin)
            .putBoolean("setup", true)
            .apply()
    }

    // Photos, screensaver and night sleep timer
    fun photoBackground(c: Context): Boolean = p(c).getBoolean("photoBg", true)
    fun saverMinutes(c: Context): Int = p(c).getInt("saverMin", 5)          // 0 = off
    fun sleepEnabled(c: Context): Boolean = p(c).getBoolean("sleepOn", false)
    fun sleepMinute(c: Context): Int = p(c).getInt("sleepMin", 23 * 60)     // 23:00

    fun saveExtras(c: Context, photoBg: Boolean, saverMin: Int, sleepOn: Boolean, sleepMin: Int) {
        p(c).edit()
            .putBoolean("photoBg", photoBg)
            .putInt("saverMin", saverMin.coerceIn(0, 240))
            .putBoolean("sleepOn", sleepOn)
            .putInt("sleepMin", sleepMin)
            .apply()
    }

    /** Approximate time the TV booted, in minutes. */
    private fun bootMinute(): Long =
        (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60_000L

    fun autoOpenedThisBoot(c: Context): Boolean =
        autoOpenedInProcess || abs(p(c).getLong("bootMin", -999L) - bootMinute()) <= 1

    fun markAutoOpened(c: Context) {
        autoOpenedInProcess = true
        p(c).edit().putLong("bootMin", bootMinute()).apply()
    }
}
