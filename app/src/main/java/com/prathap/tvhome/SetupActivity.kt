package com.prathap.tvhome

import android.app.Activity
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

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.START
            setPadding(0, dp(20), 0, 0)
        }
        fun button(t: String, onClick: () -> Unit) = Button(this).apply {
            text = t; isAllCaps = false; textSize = 16f; setOnClickListener { onClick() }
            row.addView(this, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(12) })
        }

        val save = button("Save") {
            Prefs.save(this, url.text.toString(), user.text.toString(), pass.text.toString(),
                auto.isChecked, autoLogin.isChecked)
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
        col.addView(label("Tip: in the dashboard press MENU on the remote to come back here, BACK for home.", 14f).apply {
            alpha = 0.7f
        })

        setContentView(scroll)
        (if (firstRun) pass else save as View).requestFocus()
    }
}
