package io.clayson.dinnercheckin

import android.app.Application
import io.clayson.dinnercheckin.data.Prefs
import io.clayson.dinnercheckin.notify.Notifier
import io.clayson.dinnercheckin.notify.ReminderScheduler

class DinnerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        Prefs.role(this)?.let { Notifier.subscribeTopics(it) }
        ReminderScheduler.scheduleNext(this)
    }
}
