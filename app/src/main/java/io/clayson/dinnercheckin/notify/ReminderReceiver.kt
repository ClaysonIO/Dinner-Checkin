package io.clayson.dinnercheckin.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.clayson.dinnercheckin.data.Prefs
import io.clayson.dinnercheckin.data.Repo
import io.clayson.dinnercheckin.data.Role
import io.clayson.dinnercheckin.data.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/**
 * Fires at each reminder time, and also on boot / app update / clock changes (which wipe or
 * invalidate alarms). Always re-arms the next alarm; only a real reminder alarm may notify.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.scheduleNext(context)
        if (intent.action != ReminderScheduler.ACTION_REMIND) return
        if (Prefs.role(context) != Role.DINER || !Prefs.remindersEnabled(context)) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val today = LocalDate.now()
                // If we cannot find out (no network, nothing cached), ask anyway.
                val status = withTimeoutOrNull(8_000) {
                    runCatching { Repo.getDay(today).status }.getOrNull()
                } ?: Status.UNKNOWN
                if (status == Status.UNKNOWN) Notifier.showReminder(context, today)
            } finally {
                pending.finish()
            }
        }
    }
}
