package io.clayson.dinnercheckin.notify

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.clayson.dinnercheckin.data.Repo
import io.clayson.dinnercheckin.data.Status
import io.clayson.dinnercheckin.data.display
import io.clayson.dinnercheckin.data.relativeLabel
import io.clayson.dinnercheckin.data.toLocalDateOrNull
import io.clayson.dinnercheckin.data.toLocalTimeOrNull
import io.clayson.dinnercheckin.data.toStatus
import java.time.LocalDate

/**
 * Receives the data messages sent by the Cloud Function (see functions/index.js) whenever
 * something changes in Firestore, and turns them into notifications worded for this phone's
 * own date and time format.
 */
class PushService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        // Don't notify people about their own changes.
        if (data["by"] != null && data["by"] == Repo.uidOrNull()) return

        when (data["kind"]) {
            "status" -> {
                val date = data["date"].toLocalDateOrNull() ?: return
                if (date.isBefore(LocalDate.now())) return
                val whenLabel = date.relativeLabel()
                val arrival = data["arrivalTime"].toLocalTimeOrNull()
                val (title, text) = when (data["status"].toStatus()) {
                    Status.YES -> "Coming to dinner $whenLabel" to
                        (arrival?.let { "Expected home by ${it.display()}." } ?: "No arrival time given yet.")
                    Status.NO -> "Not coming to dinner $whenLabel" to "They won't be home for dinner."
                    Status.UNKNOWN -> "Dinner $whenLabel is undecided again" to "The answer was cleared."
                }
                Notifier.showUpdate(this, ("status" + date).hashCode(), title, text)
                // If the diner answered on another device, a pending reminder here is stale.
                if (date == LocalDate.now()) Notifier.cancelReminder(this)
            }
            "dinnerTime" -> {
                val date = data["date"].toLocalDateOrNull() ?: return
                if (date.isBefore(LocalDate.now())) return
                val time = data["dinnerTime"].toLocalTimeOrNull()
                val text = time?.let { "Dinner is now at ${it.display()}." } ?: "Dinner is back to the usual time."
                Notifier.showUpdate(this, ("time" + date).hashCode(), "Dinner time changed ${date.relativeLabel()}", text)
            }
            "defaultDinnerTime" -> {
                val time = data["dinnerTime"].toLocalTimeOrNull() ?: return
                Notifier.showUpdate(this, "default".hashCode(), "Usual dinner time changed", "Dinner is now usually at ${time.display()}.")
            }
        }
    }
}
