package io.clayson.dinnercheckin.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.clayson.dinnercheckin.data.Repo
import io.clayson.dinnercheckin.data.Status
import io.clayson.dinnercheckin.data.toLocalDateOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/** Handles the Yes / No buttons on the reminder notification. */
class AnswerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val date = intent.getStringExtra(EXTRA_DATE).toLocalDateOrNull() ?: return
        val status = if (intent.getBooleanExtra(EXTRA_YES, false)) Status.YES else Status.NO
        Notifier.cancelReminder(context)

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // Wait briefly for the server so the others get notified promptly; if we are
                // offline the write is already queued on disk and syncs later.
                withTimeoutOrNull(8_000) { runCatching { Repo.setStatus(date, status).await() } }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_DATE = "date"
        private const val EXTRA_YES = "yes"

        fun pendingIntent(context: Context, date: LocalDate, yes: Boolean): PendingIntent =
            PendingIntent.getBroadcast(
                context, if (yes) 1 else 2,
                Intent(context, AnswerReceiver::class.java)
                    .putExtra(EXTRA_DATE, date.toString())
                    .putExtra(EXTRA_YES, yes),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
