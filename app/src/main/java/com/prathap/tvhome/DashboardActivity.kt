package com.prathap.tvhome

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONObject

/** Full-screen trading dashboard with saved login. */
class DashboardActivity : Activity() {

    private lateinit var web: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var submitTries = 0
    private var httpAuthTries = 0
    private var offline = false

    private val retry = Runnable { if (offline) loadDashboard() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        web = WebView(this)
        web.setBackgroundColor(Color.BLACK)
        setContentView(web)

        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(web, true)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(true)
        }
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (url?.startsWith("http") == true) offline = false
            }

            override fun onPageFinished(view: WebView, url: String?) {
                CookieManager.getInstance().flush()
                if (!offline) autofillLogin()
            }

            // Pop-up style (HTTP Basic) password
            override fun onReceivedHttpAuthRequest(
                view: WebView, h: HttpAuthHandler, host: String?, realm: String?
            ) {
                val pass = Prefs.pass(this@DashboardActivity)
                if (pass.isNotEmpty() && httpAuthTries < 2) {
                    httpAuthTries++
                    h.proceed(Prefs.user(this@DashboardActivity), pass)
                } else {
                    h.cancel()
                    toast("Login failed – press MENU on the remote to fix the password")
                }
            }

            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                if (request.isForMainFrame) showOffline(error.description?.toString() ?: "")
            }

            override fun onReceivedSslError(view: WebView, h: SslErrorHandler, error: SslError?) {
                // Never accept a bad certificate – protects your password.
                h.cancel()
                showOffline("Security certificate problem")
            }
        }

        if (savedInstanceState != null) web.restoreState(savedInstanceState) else loadDashboard()
    }

    private fun loadDashboard() {
        submitTries = 0
        httpAuthTries = 0
        web.loadUrl(Prefs.url(this))
    }

    /** Fills username + password on the dashboard's login page and presses Login. */
    private fun autofillLogin() {
        if (!Prefs.autoLogin(this)) return
        val pass = Prefs.pass(this)
        if (pass.isEmpty()) return
        val submit = submitTries < 2
        val js = """
            (function(u,p,submit){
              var pw=document.querySelector('input[type=password]');
              if(!pw) return 'nologin';
              var all=[].slice.call(document.querySelectorAll('input'));
              var idx=all.indexOf(pw), us=null;
              for(var i=idx-1;i>=0;i--){var t=(all[i].getAttribute('type')||'text').toLowerCase();
                if(t=='text'||t=='email'||t=='tel'){us=all[i];break;}}
              function setv(el,v){var s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;
                s.call(el,v);el.dispatchEvent(new Event('input',{bubbles:true}));
                el.dispatchEvent(new Event('change',{bubbles:true}));}
              if(us&&u) setv(us,u);
              setv(pw,p);
              if(!submit) return 'filled';
              var f=pw.form;
              var b=(f||document).querySelector('button[type=submit],input[type=submit],button:not([type])');
              if(b){b.click();} else if(f){ if(f.requestSubmit) f.requestSubmit(); else f.submit(); }
              return 'submitted';
            })(${JSONObject.quote(Prefs.user(this))},${JSONObject.quote(pass)},$submit)
        """.trimIndent()
        web.evaluateJavascript(js) { result ->
            when {
                result?.contains("nologin") == true -> submitTries = 0   // logged in
                result?.contains("submitted") == true -> submitTries++
                result?.contains("filled") == true ->
                    toast("Login not accepted – press MENU on the remote to check the password")
            }
        }
    }

    private fun showOffline(reason: String) {
        offline = true
        val html = """
            <html><body style="background:#0b1020;color:#fff;font-family:sans-serif;
            display:flex;align-items:center;justify-content:center;height:100vh;margin:0;text-align:center">
            <div><h1 style="font-weight:600">Connecting to your dashboard…</h1>
            <p style="opacity:.7">${reason.replace("<", "")}</p>
            <p style="opacity:.7">Retrying automatically. Press BACK for the home screen.</p></div>
            </body></html>
        """.trimIndent()
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        handler.removeCallbacks(retry)
        handler.postDelayed(retry, 8_000)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            startActivity(Intent(this, SetupActivity::class.java))
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!offline && web.canGoBack()) web.goBack() else goHome()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun goHome() {
        startActivity(
            Intent(this, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        web.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onDestroy() {
        handler.removeCallbacks(retry)
        web.destroy()
        super.onDestroy()
    }
}
