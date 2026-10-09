package com.dante.anchoralarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val now = System.currentTimeMillis()
        Store.loadAll(context).forEach { p ->
            val future = Store.loadRings(context, p.id).filter { it > now }
            if (future.isNotEmpty()) Scheduler.schedule(context, p.id, future)
        }
    }
}
