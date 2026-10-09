package com.dante.anchoralarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object Scheduler {

    private const val MAX_RINGS = 60

    /** Unique PendingIntent request code per (checklist, ring). Plan ids start at 1. */
    private fun code(planId: Int, i: Int) = planId * 100 + i

    private fun anchorOn(dayOffset: Int, h: Int, m: Int): Long =
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** Anchor time as a millisecond timestamp today (for display only). */
    fun anchorMillis(h: Int, m: Int): Long = anchorOn(0, h, m)

    /**
     * BEFORE: N rings ending exactly at the anchor.
     * AFTER : N rings starting exactly at the anchor.
     * If every ring of today's pattern is already past, uses tomorrow.
     */
    fun ringTimes(p: Plan, now: Long = System.currentTimeMillis()): List<Long> {
        val step = p.intervalMin * 60_000L
        fun build(a: Long): List<Long> = List(p.count) { k ->
            if (p.after) a + k * step else a - (p.count - 1 - k) * step
        }
        val today = build(anchorOn(0, p.hour, p.minute))
        return if (today.last() > now) today.filter { it > now }
        else build(anchorOn(1, p.hour, p.minute))
    }

    fun schedule(ctx: Context, planId: Int, times: List<Long>) {
        cancel(ctx, planId)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val show = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        times.take(MAX_RINGS).forEachIndexed { i, t ->
            val pi = PendingIntent.getBroadcast(
                ctx, code(planId, i),
                Intent(ctx, AlarmReceiver::class.java)
                    .putExtra("planId", planId)
                    .putExtra("index", i)
                    .putExtra("total", times.size),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(t, show), pi)
        }
    }

    /** Cancels only this checklist's rings. */
    fun cancel(ctx: Context, planId: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        for (i in 0 until MAX_RINGS) {
            val pi = PendingIntent.getBroadcast(
                ctx, code(planId, i), Intent(ctx, AlarmReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )
            if (pi != null) {
                am.cancel(pi)
                pi.cancel()
            }
        }
    }

    /** Removes alarms scheduled by v1.x (request codes 0..59). */
    fun cancelLegacy(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        for (i in 0 until MAX_RINGS) {
            val pi = PendingIntent.getBroadcast(
                ctx, i, Intent(ctx, AlarmReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )
            if (pi != null) {
                am.cancel(pi)
                pi.cancel()
            }
        }
    }
}
