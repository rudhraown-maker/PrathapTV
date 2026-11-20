package com.prathap.tvhome

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Cross-fading, slowly zooming photo slideshow (home background + screensaver). */
class SlideshowView(
    context: Context,
    private val withClock: Boolean,
    private val dim: Float
) : FrameLayout(context) {

    private val a = ImageView(context)
    private val b = ImageView(context)
    private var front: ImageView = a
    private val overlay = View(context)
    private val clock = TextView(context)
    private val date = TextView(context)
    private val handler = Handler(Looper.getMainLooper())
    private val exec: ExecutorService = Executors.newSingleThreadExecutor()
    private var uris: List<Uri> = emptyList()
    private var index = 0
    private var running = false
    var intervalMs = 12_000L

    val hasPhotos: Boolean get() = uris.isNotEmpty()

    init {
        if (withClock) setBackgroundColor(Color.BLACK)
        for (iv in listOf(a, b)) {
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.alpha = 0f
            addView(iv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        overlay.setBackgroundColor(Color.BLACK)
        overlay.alpha = 0f
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        if (withClock) {
            val d = resources.displayMetrics.density
            clock.setTextColor(Color.WHITE); clock.textSize = 56f; clock.typeface = Typeface.DEFAULT_BOLD
            clock.setShadowLayer(12f, 0f, 2f, Color.BLACK)
            date.setTextColor(Color.WHITE); date.textSize = 20f
            date.setShadowLayer(10f, 0f, 2f, Color.BLACK)
            addView(clock, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, (48 * d).toInt(), (64 * d).toInt()) })
            addView(date, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, (52 * d).toInt(), (36 * d).toInt()) })
        }
    }

    fun setPhotos(list: List<Uri>) {
        val changed = list.toSet() != uris.toSet()
        if (!changed) return
        uris = list.shuffled()
        index = 0
        if (running) {
            handler.removeCallbacks(next)
            handler.post(next)
        }
    }

    fun start() {
        if (running) return
        running = true
        handler.post(next)
        if (withClock) handler.post(tick)
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
    }

    fun release() {
        stop()
        exec.shutdownNow()
    }

    private val tick = object : Runnable {
        override fun run() {
            clock.text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            date.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())
            handler.postDelayed(this, 15_000)
        }
    }

    private val next: Runnable = object : Runnable {
        override fun run() {
            if (!running || uris.isEmpty()) return
            val uri = uris[index % uris.size]
            index++
            val w = maxOf(width, 1280)
            val h = maxOf(height, 720)
            val self = this
            try {
                exec.execute {
                    val bmp = PhotoStore.decode(context, uri, w, h)
                    handler.post {
                        if (!running) return@post
                        if (bmp != null) show(bmp)
                        handler.postDelayed(self, if (bmp == null) 300L else intervalMs)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun show(bmp: Bitmap) {
        val back = if (front === a) b else a
        back.setImageBitmap(bmp)
        back.animate().alpha(1f).setDuration(1500).start()
        ObjectAnimator.ofPropertyValuesHolder(
            back,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f)
        ).setDuration(intervalMs + 2000).start()
        front.animate().alpha(0f).setDuration(1500).start()
        front = back
        if (overlay.alpha != dim) overlay.animate().alpha(dim).setDuration(800).start()
    }
}
