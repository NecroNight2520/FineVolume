package com.necro.finevolume

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.IBinder

class VolumeService : Service() {

    companion object {
        const val ACTION_STOP = "stop"
        @Volatile var status: String = "未適用"
    }

    private var effect: LoudnessEnhancer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFg()
        if (intent?.action == ACTION_STOP) {
            release()
            status = "解除しました"
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val mb = intent?.getIntExtra("mb", 0) ?: 0
        applyGain(mb)
        return START_STICKY
    }

    private fun applyGain(mb: Int) {
        try {
            if (effect == null) effect = LoudnessEnhancer(0)
            effect!!.setTargetGain(mb)
            effect!!.enabled = true
            status = "適用中: %.1f dB（実際に小さくなったか耳で確認してください）".format(mb / 100.0)
        } catch (e: Throwable) {
            status = "失敗: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    private fun release() {
        try {
            effect?.enabled = false
            effect?.release()
        } catch (_: Throwable) {
        }
        effect = null
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("vol", "FineVolume", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "vol")
            .setContentTitle("FineVolume 動作中")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, n)
        }
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }
}
