package com.prathap.tvhome

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Settings screen: dashboard address, login, auto-open. */
class SetupActivity : Activity() {

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val firstRun = !Prefs.isSetUp(this)

        val scroll = ScrollView(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#0B1020"), Color.parseColor("#1E1B4B"))
            )
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(120), dp(40), dp(120), dp(40))
        }
        scroll.addView(col)

        fun label(t: String, size: Float = 16f) = TextView(this).apply {
            text = t; setTextColor(Color.WHITE); textSize = size
            setPadding(0, dp(14), 0, dp(4))
        }
        fun field(value: String, type: Int) = EditText(this).apply {
            setText(value); inputType = type; setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY); textSize = 18f; isSingleLine = true
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat(); setColor(0x22FFFFFF)
            }
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        col.addView(label("Prathap TV – Settings", 28f))
        col.addView(label("Dashboard address"))
        val url = field(Prefs.url(this), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        col.addView(url)
        col.addView(label("Username"))
        val user = field(Prefs.user(this), InputType.TYPE_CLASS_TEXT)
        col.addView(user)
        col.addView(label("Password (saved only on this TV)"))
        val pass = field(Prefs.pass(this),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        col.addView(pass)

        val auto = CheckBox(this).apply {
            text = "Open the dashboard automatically when the TV turns on"
            setTextColor(Color.WHITE); textSize = 16f; isChecked = Prefs.autoOpen(this@SetupActivity)
        }
        val autoLogin = CheckBox(this).apply {
            text = "Log in automatically with the saved password"
            setTextColor(Color.WHITE); textSize = 16f; isChecked = Prefs.autoLogin(this@SetupActivity)
        }
        col.addView(auto)
        col.addView(autoLogin)

        // ---- Family photos, screensaver, night sleep timer
        col.addView(label("Family photos & sleep time", 22f).apply { setPadding(0, dp(28), 0, dp(4)) })
        val photoBg = CheckBox(this).apply {
            text = "Show family photos behind the home screen"
            setTextColor(Color.WHITE); textSize = 16f; isChecked = Prefs.photoBackground(this@SetupActivity)
        }
        col.addView(photoBg)
        col.addView(label("Photo screensaver starts after this many idle minutes (0 = off)"))
        val saver = field(Prefs.saverMinutes(this).toString(), InputType.TYPE_CLASS_NUMBER)
        col.addView(saver)
        val sleepOn = CheckBox(this).apply {
            text = "Night sleep timer – switch the TV off every night"
            setTextColor(Color.WHITE); textSize = 16f; isChecked = Prefs.sleepEnabled(this@SetupActivity)
        }
        col.addView(sleepOn)
        col.addView(label("Sleep time (24-hour clock, e.g. 23:00)"))
        val sleepAt = field(SleepScheduler.format(Prefs.sleepMinute(this)),
            InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME)
        col.addView(sleepAt)

        fun newRow() = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.START
            setPadding(0, dp(16), 0, 0)
        }
        val row = newRow()
        val row2 = newRow()
        fun buttonIn(target: LinearLayout, t: String, onClick: () -> Unit) = Button(this).apply {
            text = t; isAllCaps = false; textSize = 16f; setOnClickListener { onClick() }
            target.addView(this, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(12) })
        }
        fun button(t: String, onClick: () -> Unit) = buttonIn(row, t, onClick)

        buttonIn(row2, "Allow photo access") {
            if (PhotoStore.hasPermission(this)) {
                Toast.makeText(this, "Photo access already allowed", Toast.LENGTH_SHORT).show()
            } else {
                requestPermissions(arrayOf(PhotoStore.permission()), 21)
            }
        }
        buttonIn(row2, "Play slideshow now") {
            startActivity(Intent(this, SlideshowActivity::class.java))
        }
        buttonIn(row2, "Allow TV to switch off") {
            if (SleepScheduler.isAdminActive(this)) {
                Toast.makeText(this, "Already allowed", Toast.LENGTH_SHORT).show()
            } else {
                try {
                    startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, SleepScheduler.adminComponent(this))
                        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Lets Prathap TV switch the screen off at your sleep time."))
                } catch (_: Exception) {
                    Toast.makeText(this, "This TV hides that screen – see the setup guide (adb command)",
                        Toast.LENGTH_LONG).show()
                }
            }
        }

        val save = button("Save") {
            val sleepMin = SleepScheduler.parse(sleepAt.text.toString())
            if (sleepOn.isChecked && sleepMin == null) {
                Toast.makeText(this, "Sleep time must look like 23:00", Toast.LENGTH_LONG).show()
                return@button
            }
            Prefs.save(this, url.text.toString(), user.text.toString(), pass.text.toString(),
                auto.isChecked, autoLogin.isChecked)
            Prefs.saveExtras(this, photoBg.isChecked,
                saver.text.toString().trim().toIntOrNull() ?: 5,
                sleepOn.isChecked, sleepMin ?: Prefs.sleepMinute(this))
            SleepScheduler.schedule(this)
            if (sleepOn.isChecked && !SleepScheduler.isAdminActive(this)) {
                Toast.makeText(this,
                    "Tip: press 'Allow TV to switch off' so the timer can turn the screen off",
                    Toast.LENGTH_LONG).show()
            }
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            if (firstRun) {
                Prefs.markAutoOpened(this)
                startActivity(Intent(this, DashboardActivity::class.java))
            }
            finish()
        }
        button("Log out / clear saved session") {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            Toast.makeText(this, "Session cleared", Toast.LENGTH_SHORT).show()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !Settings.canDrawOverlays(this)) {
            button("Allow auto-start at power-on") {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                } catch (_: Exception) {
                    Toast.makeText(this,
                        "This TV hides that setting – set Prathap TV as home screen instead",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
        col.addView(row)
        col.addView(row2)
        col.addView(label("Tip: in the dashboard press MENU on the remote to come back here, BACK for home.", 14f).apply {
            alpha = 0.7f
        })

        setContentView(scroll)
        (if (firstRun) pass else save as View).requestFocus()
    }
}
