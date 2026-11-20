package com.prathap.tvhome

import android.service.dreams.DreamService

/** System screensaver: pick "Prathap TV photos" in TV Settings › Screen saver. */
class PhotoDream : DreamService() {

    private var show: SlideshowView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        val v = SlideshowView(this, withClock = true, dim = 0f)
        v.setPhotos(PhotoStore.list(this))
        setContentView(v)
        show = v
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        show?.start()
    }

    override fun onDreamingStopped() {
        show?.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        show?.release()
        show = null
        super.onDetachedFromWindow()
    }
}
