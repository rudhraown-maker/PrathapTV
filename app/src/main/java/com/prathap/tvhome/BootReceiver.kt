package com.prathap.tvhome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Backup auto-start: opens the dashboard when the TV powers on, even if
 * this app is not the default home screen. Android 10+ only allows this
 * when "Display over other apps" is allowed for Prathap TV.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        SleepScheduler.schedule(c)
        if (!Prefs.isSetUp(c) || !Prefs.autoOpen(c)) return
        if (Prefs.autoOpenedThisBoot(c)) return
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(c)
        if (!allowed) return
        Prefs.markAutoOpened(c)
        try {
            c.startActivity(
                Intent(c, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }
}
