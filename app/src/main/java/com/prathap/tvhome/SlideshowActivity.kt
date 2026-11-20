package com.prathap.tvhome

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast

/** Full-screen photo screensaver. Any button on the remote closes it. */
class SlideshowActivity : Activity() {

    private lateinit var show: SlideshowView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        show = SlideshowView(this, withClock = true, dim = 0f)
        setContentView(show)
        val photos = PhotoStore.list(this)
        if (photos.isEmpty()) {
            Toast.makeText(this,
                if (PhotoStore.hasPermission(this)) "No photos on the TV yet – send some with 'Send Files to TV'"
                else "Allow photo access in Prathap TV settings first",
                Toast.LENGTH_LONG).show()
            finish()
            return
        }
        show.setPhotos(photos)
    }

    override fun onResume() {
        super.onResume()
        show.start()
    }

    override fun onPause() {
        show.stop()
        super.onPause()
    }

    override fun onDestroy() {
        show.release()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        finish()
        return true
    }
}
