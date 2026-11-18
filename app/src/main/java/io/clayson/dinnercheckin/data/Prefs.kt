package io.clayson.dinnercheckin.data

import android.content.Context

/** Settings that belong to this phone only (everything shared lives in Firestore). */
object Prefs {
    private fun sp(context: Context) =
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    fun role(context: Context): Role? =
        sp(context).getString("role", null)?.let { runCatching { Role.valueOf(it) }.getOrNull() }

    fun setRole(context: Context, role: Role) =
        sp(context).edit().putString("role", role.name).apply()

    fun remindersEnabled(context: Context): Boolean = sp(context).getBoolean("reminders", true)

    fun setRemindersEnabled(context: Context, enabled: Boolean) =
        sp(context).edit().putBoolean("reminders", enabled).apply()
}
