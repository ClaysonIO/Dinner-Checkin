package io.clayson.dinnercheckin.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging
import io.clayson.dinnercheckin.R
import io.clayson.dinnercheckin.data.Role
import io.clayson.dinnercheckin.ui.MainActivity
import java.time.LocalDate

object Notifier {
    private const val CHANNEL_REMINDERS = "reminders"
    private const val CHANNEL_UPDATES = "updates"
    const val REMINDER_ID = 1

    private const val TOPIC_DINER = "diner"
    private const val TOPIC_WATCHERS = "watchers"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Dinner reminders", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Asks whether you will be home for dinner" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATES, "Dinner updates", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Changes to dinner attendance or dinner time" }
        )
    }

    /** The diner hears about dinner-time changes; the household also hears about attendance. */
    fun subscribeTopics(role: Role) {
        val fm = Firebase.messaging
        when (role) {
            Role.DINER -> { fm.subscribeToTopic(TOPIC_DINER); fm.unsubscribeFromTopic(TOPIC_WATCHERS) }
            Role.HOUSEHOLD -> { fm.subscribeToTopic(TOPIC_WATCHERS); fm.unsubscribeFromTopic(TOPIC_DINER) }
        }
    }

    private fun canPost(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** "Will you be at dinner tonight?" with Yes / No buttons right on the notification. */
    fun showReminder(context: Context, date: LocalDate) {
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_dinner)
            .setContentTitle("Will you be at dinner tonight?")
            .setContentText("Tap Yes or No to let everyone know.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .addAction(0, "Yes", AnswerReceiver.pendingIntent(context, date, yes = true))
            .addAction(0, "No", AnswerReceiver.pendingIntent(context, date, yes = false))
            .build()
        NotificationManagerCompat.from(context).notify(REMINDER_ID, notification)
    }

    fun cancelReminder(context: Context) =
        NotificationManagerCompat.from(context).cancel(REMINDER_ID)

    fun showUpdate(context: Context, id: Int, title: String, text: String) {
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_dinner)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
