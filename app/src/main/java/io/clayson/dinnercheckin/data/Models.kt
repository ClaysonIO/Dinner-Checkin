package io.clayson.dinnercheckin.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Who is using this phone. */
enum class Role { DINER, HOUSEHOLD }

enum class Status { UNKNOWN, YES, NO }

/** One calendar day. Every field is optional: an absent day means "unknown, default dinner time". */
data class Day(
    val status: Status = Status.UNKNOWN,
    /** When the diner expects to be home (only meaningful when status is YES). */
    val arrivalTime: LocalTime? = null,
    /** Dinner time override for this day; null means the household default applies. */
    val dinnerTime: LocalTime? = null,
)

val DEFAULT_DINNER_TIME: LocalTime = LocalTime.of(17, 30)

private val storedTime = DateTimeFormatter.ofPattern("HH:mm")

fun LocalTime.toStored(): String = format(storedTime)

fun String?.toLocalTimeOrNull(): LocalTime? =
    this?.let { runCatching { LocalTime.parse(it, storedTime) }.getOrNull() }

fun String?.toLocalDateOrNull(): LocalDate? =
    this?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

fun String?.toStatus(): Status = when (this) {
    "yes" -> Status.YES
    "no" -> Status.NO
    else -> Status.UNKNOWN
}

fun LocalTime.display(): String = format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

/** "tonight", "tomorrow" or "on Fri, Nov 20". */
fun LocalDate.relativeLabel(today: LocalDate = LocalDate.now()): String = when (this) {
    today -> "tonight"
    today.plusDays(1) -> "tomorrow"
    else -> "on " + format(DateTimeFormatter.ofPattern("EEE, MMM d"))
}
