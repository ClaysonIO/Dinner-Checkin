package io.clayson.dinnercheckin.data

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.snapshots
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * All shared data, stored in Cloud Firestore:
 *
 *   days/{yyyy-MM-dd}  { status: "yes"|"no", arrivalTime: "HH:mm", dinnerTime: "HH:mm", updatedBy: uid }
 *   config/household   { defaultDinnerTime: "HH:mm", updatedBy: uid }
 *
 * Writes return the Firestore [Task] without waiting for it: Firestore applies them locally at
 * once and syncs when online, so the UI never blocks on the network.
 */
object Repo {
    private val db get() = Firebase.firestore
    private val days get() = db.collection("days")
    private val config get() = db.collection("config").document("household")

    /** Signs in anonymously on first use; the session persists afterwards. Returns our uid. */
    suspend fun ensureSignedIn(): String {
        val auth = Firebase.auth
        return (auth.currentUser ?: auth.signInAnonymously().await().user!!).uid
    }

    fun uidOrNull(): String? = Firebase.auth.currentUser?.uid

    /** Keep trying (e.g. first launch with no network) instead of failing the screen. */
    private fun <T> Flow<T>.resilient(): Flow<T> = retryWhen { _, _ -> delay(5_000); true }

    private fun DocumentSnapshot.toDay() = Day(
        status = getString("status").toStatus(),
        arrivalTime = getString("arrivalTime").toLocalTimeOrNull(),
        dinnerTime = getString("dinnerTime").toLocalTimeOrNull(),
    )

    fun day(date: LocalDate): Flow<Day> = flow {
        ensureSignedIn()
        emitAll(days.document(date.toString()).snapshots().map { it.toDay() })
    }.resilient()

    fun month(month: YearMonth): Flow<Map<LocalDate, Day>> = flow {
        ensureSignedIn()
        val query = days
            .whereGreaterThanOrEqualTo(FieldPath.documentId(), month.atDay(1).toString())
            .whereLessThanOrEqualTo(FieldPath.documentId(), month.atEndOfMonth().toString())
        emitAll(query.snapshots().map { snap ->
            snap.documents.mapNotNull { doc -> doc.id.toLocalDateOrNull()?.let { it to doc.toDay() } }.toMap()
        })
    }.resilient()

    fun defaultDinnerTime(): Flow<LocalTime> = flow {
        ensureSignedIn()
        emitAll(config.snapshots().map {
            it.getString("defaultDinnerTime").toLocalTimeOrNull() ?: DEFAULT_DINNER_TIME
        })
    }.resilient()

    /** One-shot read (falls back to the local cache when offline). */
    suspend fun getDay(date: LocalDate): Day {
        ensureSignedIn()
        return days.document(date.toString()).get().await().toDay()
    }

    suspend fun setStatus(date: LocalDate, status: Status): Task<Void> {
        val uid = ensureSignedIn()
        val data = when (status) {
            Status.YES -> mapOf("status" to "yes")
            // An arrival time makes no sense unless the answer is yes.
            Status.NO -> mapOf("status" to "no", "arrivalTime" to FieldValue.delete())
            Status.UNKNOWN -> mapOf("status" to FieldValue.delete(), "arrivalTime" to FieldValue.delete())
        }
        return days.document(date.toString()).set(data + ("updatedBy" to uid), SetOptions.merge())
    }

    suspend fun setArrivalTime(date: LocalDate, time: LocalTime?): Task<Void> =
        setDayField(date, "arrivalTime", time)

    /** Pass null to go back to the household default for that day. */
    suspend fun setDinnerTime(date: LocalDate, time: LocalTime?): Task<Void> =
        setDayField(date, "dinnerTime", time)

    private suspend fun setDayField(date: LocalDate, field: String, time: LocalTime?): Task<Void> {
        val uid = ensureSignedIn()
        val data = mapOf(field to (time?.toStored() ?: FieldValue.delete()), "updatedBy" to uid)
        return days.document(date.toString()).set(data, SetOptions.merge())
    }

    suspend fun setDefaultDinnerTime(time: LocalTime): Task<Void> {
        val uid = ensureSignedIn()
        return config.set(mapOf("defaultDinnerTime" to time.toStored(), "updatedBy" to uid), SetOptions.merge())
    }
}
