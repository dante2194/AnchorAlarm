package com.dante.anchoralarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val svc = Intent(context, RingService::class.java).putExtras(intent)
        ContextCompat.startForegroundService(context, svc)
    }
}
