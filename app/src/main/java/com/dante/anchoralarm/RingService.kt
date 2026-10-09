package com.dante.anchoralarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class RingService : Service() {

    companion object {
        const val CHANNEL = "ring"
        const val NOTIF_ID = 42
        const val ACTION_STOP = "com.dante.anchoralarm.STOP"
        private const val RING_MS = 60_000L
    }

    private var ringtone: Ringtone? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val planId = intent?.getIntExtra("planId", -1) ?: -1
        val index = intent?.getIntExtra("index", 0) ?: 0
        val total = intent?.getIntExtra("total", 1) ?: 1

        val plan = Store.load(this, planId)

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Rings", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )

        val open = Intent(this, RingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("planId", planId)
            .putExtra("index", index)
            .putExtra("total", total)
        val openPi = PendingIntent.getActivity(
            this, 1000 + planId, open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val items = plan?.items ?: emptyList()
        val lines = items.map { (if (it.done) "✓ " else "○ ") + it.text }
        val compact = if (lines.isEmpty()) "No checklist items" else lines.joinToString("  ·  ")
        val expanded = if (lines.isEmpty()) "No checklist items" else lines.joinToString("\n")
        val name = plan?.name?.ifBlank { null } ?: "Anchor"

        val notif = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("$name · ring ${index + 1} of $total")
            .setContentText(compact)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(openPi)
            .setFullScreenIntent(openPi, true)
            .build()

        ServiceCompat.startForeground(
            this, NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )

        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "anchor:ring")
            .also { it.acquire(RING_MS + 10_000L) }

        startSound()
        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, RING_MS)
        return START_NOT_STICKY
    }

    private fun startSound() {
        stopSound()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= 28) isLooping = true
            play()
        }
        vibrator().vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0))
    }

    private fun stopSound() {
        ringtone?.stop()
        ringtone = null
        vibrator().cancel()
    }

    @Suppress("DEPRECATION")
    private fun vibrator(): Vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    override fun onDestroy() {
        handler.removeCallbacks(autoStop)
        stopSound()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
