package com.necro.finevolume

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad)
        }

        val label = TextView(this).apply { textSize = 22f }
        val status = TextView(this).apply { textSize = 14f; setPadding(0, pad, 0, 0) }
        val bar = SeekBar(this).apply { max = 600 }   // 0〜-60.0dB を 0.1dB刻み

        fun refresh() {
            label.text = "減衰: -%.1f dB".format(bar.progress / 10.0)
        }

        fun send() {
            val i = Intent(this, VolumeService::class.java)
            i.putExtra("mb", -bar.progress * 10)       // ミリベル
            startForegroundService(i)
        }

        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) = refresh()
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) = send()
        })

        fun btn(text: String, onClick: () -> Unit) = Button(this).apply {
            this.text = text
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        root.addView(label)
        root.addView(bar)
        root.addView(btn("◀ 0.1dB 小さく（さらに下げる）") {
            bar.progress = (bar.progress + 1).coerceAtMost(600); refresh(); send()
        })
        root.addView(btn("▶ 0.1dB 戻す") {
            bar.progress = (bar.progress - 1).coerceAtLeast(0); refresh(); send()
        })
        root.addView(btn("適用") { send() })
        root.addView(btn("解除") {
            val i = Intent(this, VolumeService::class.java)
            i.action = VolumeService.ACTION_STOP
            startForegroundService(i)
        })
        root.addView(status)
        setContentView(root)
        refresh()

        handler.post(object : Runnable {
            override fun run() {
                status.text = "状態: " + VolumeService.status
                handler.postDelayed(this, 500)
            }
        })
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
