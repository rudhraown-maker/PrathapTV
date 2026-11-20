package com.prathap.tvhome

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

/** Night sleep timer: every day at the chosen time the TV goes to sleep. */
object SleepScheduler {

    fun adminComponent(c: Context) = ComponentName(c, SleepAdmin::class.java)

    fun isAdminActive(c: Context): Boolean {
        val dpm = c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(adminComponent(c))
    }

    fun schedule(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            c, 7, Intent(c, SleepAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
        if (!Prefs.sleepEnabled(c)) return
        val m = Prefs.sleepMinute(c)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, m / 60)
            set(Calendar.MINUTE, m % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 5_000) add(Calendar.DAY_OF_YEAR, 1)
        }
        try {
            val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } catch (_: Exception) {
            am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }

    /** "23:00" -> minutes after midnight, or null if not a valid time. */
    fun parse(text: String): Int? {
        val parts = text.trim().replace('.', ':').split(":")
        if (parts.size != 2) return null
        val h = parts[0].trim().toIntOrNull() ?: return null
        val min = parts[1].trim().toIntOrNull() ?: return null
        if (h !in 0..23 || min !in 0..59) return null
        return h * 60 + min
    }

    fun format(minutes: Int): String = String.format("%02d:%02d", minutes / 60, minutes % 60)
}

/** Lets the app switch the TV screen off (user allows it once). */
class SleepAdmin : DeviceAdminReceiver()

class SleepAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        SleepScheduler.schedule(c)   // tomorrow
        try {
            c.startActivity(Intent(c, SleepActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
}

/** 60-second countdown, then the TV sleeps. Any button cancels. */
class SleepActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var text: TextView
    private var left = 60
    private var black = false

    private val tick = object : Runnable {
        override fun run() {
            left--
            if (left <= 0) sleepNow() else {
                update()
                handler.postDelayed(this, 1_000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        text = TextView(this).apply {
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
            textSize = 32f
            gravity = Gravity.CENTER
        }
        setContentView(text)
        update()
        handler.postDelayed(tick, 1_000)
    }

    private fun update() {
        text.text = "Good night 🌙\n\nTV will sleep in $left seconds\nPress any button to cancel"
    }

    private fun sleepNow() {
        if (SleepScheduler.isAdminActive(this)) {
            try {
                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                dpm.lockNow()
                finish()
                return
            } catch (_: Exception) {
            }
        }
        // Not allowed to switch off: show a black screen at lowest brightness.
        black = true
        text.text = ""
        window.attributes = window.attributes.apply { screenBrightness = 0f }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        handler.removeCallbacks(tick)
        if (!black) Toast.makeText(this, "Sleep cancelled", Toast.LENGTH_SHORT).show()
        finish()
        return true
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }
}
