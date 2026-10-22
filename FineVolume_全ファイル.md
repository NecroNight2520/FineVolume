# FineVolume 試作ソース一式

GitHubで新しいリポジトリを作り、
**Add file → Create new file** で、下の「パス」をそのまま名前欄に入力
(`/` を入れるとフォルダになります)、中身をコピペして Commit。
全部で8ファイルです。

---

## 1. `settings.gradle`

```groovy
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = 'FineVolume'
include ':app'
```

## 2. `build.gradle`

```groovy
plugins {
    id 'com.android.application' version '8.5.2' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.24' apply false
}
```

## 3. `app/build.gradle`

```groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.necro.finevolume'
    compileSdk 34

    defaultConfig {
        applicationId 'com.necro.finevolume'
        minSdk 26
        targetSdk 34
        versionCode 1
        versionName '1.0'
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = '17'
    }
}
```

## 4. `app/src/main/AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:label="FineVolume"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".VolumeService"
            android:exported="false"
            android:foregroundServiceType="mediaPlayback" />
    </application>
</manifest>
```

## 5. `app/src/main/java/com/necro/finevolume/VolumeService.kt`

```kotlin
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
```

## 6. `app/src/main/java/com/necro/finevolume/MainActivity.kt`

```kotlin
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
```

## 7. `.github/workflows/build.yml`

```yaml
name: Build APK

on:
  workflow_dispatch:
  push:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: 8.7
      - run: gradle assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: FineVolume-apk
          path: app/build/outputs/apk/debug/app-debug.apk
```

## 8. `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx2g
android.useAndroidX=false
```

---

## ビルドのしかた
1. 全ファイルをコミットすると自動でビルドが始まります
2. リポジトリの **Actions** タブ → 最新の実行をタップ
3. 一番下の **Artifacts** の `FineVolume-apk` をダウンロード(zipなので展開)
4. `app-debug.apk` をインストール

## 使い方
スライダーを動かして指を離すと反映されます。音楽や動画を流しながら試してください。
画面の「状態」に「失敗」と出たら、その文章をそのまま教えてください。
