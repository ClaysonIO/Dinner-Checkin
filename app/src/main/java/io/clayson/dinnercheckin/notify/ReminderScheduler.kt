package io.clayson.dinnercheckin.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.clayson.dinnercheckin.data.Prefs
import io.clayson.dinnercheckin.data.Role
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Daily "are you coming?" reminders for the diner. They run entirely on the phone (no server):
 * one alarm is armed for the next reminder time, and [ReminderReceiver] re-arms it when it fires.
 */
object ReminderScheduler {
    val REMINDER_TIMES: List<LocalTime> = listOf(LocalTime.of(9, 0), LocalTime.of(14, 0), LocalTime.of(16, 0))

    const val ACTION_REMIND = "io.clayson.dinnercheckin.REMIND"

    fun nextReminder(now: LocalDateTime): LocalDateTime =
        REMINDER_TIMES.map { now.toLocalDate().atTime(it) }.firstOrNull { it.isAfter(now) }
            ?: now.toLocalDate().plusDays(1).atTime(REMINDER_TIMES.first())

    private fun pendingIntent(context: Context) = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Arms (or, if this phone should not get reminders, cancels) the next alarm. Safe to call any time. */
    fun scheduleNext(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context)
        if (Prefs.role(context) != Role.DINER || !Prefs.remindersEnabled(context)) {
            alarms.cancel(pi)
            return
        }
        val at = nextReminder(LocalDateTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Inexact-but-doze-proof: needs no special permission and lands within a few minutes.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }
}
