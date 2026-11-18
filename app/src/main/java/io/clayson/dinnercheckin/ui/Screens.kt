package io.clayson.dinnercheckin.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.clayson.dinnercheckin.data.DEFAULT_DINNER_TIME
import io.clayson.dinnercheckin.data.Day
import io.clayson.dinnercheckin.data.Prefs
import io.clayson.dinnercheckin.data.Repo
import io.clayson.dinnercheckin.data.Role
import io.clayson.dinnercheckin.data.Status
import io.clayson.dinnercheckin.data.display
import io.clayson.dinnercheckin.notify.Notifier
import io.clayson.dinnercheckin.notify.ReminderScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private val Green = Color(0xFF2E7D32)
private val Red = Color(0xFFC62828)
private val Gray = Color(0xFF9E9E9E)

private fun Status.color() = when (this) {
    Status.YES -> Green
    Status.NO -> Red
    Status.UNKNOWN -> Gray
}

/** Runs a Firestore write, telling the user if it could not even be started (e.g. not signed in yet). */
private fun CoroutineScope.save(context: Context, block: suspend () -> Any?) {
    launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Toast.makeText(context, "Couldn't save. Check your internet connection.", Toast.LENGTH_LONG).show()
        }
    }
}

private fun pickTime(context: Context, initial: LocalTime, onPicked: (LocalTime) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) },
        initial.hour, initial.minute, DateFormat.is24HourFormat(context),
    ).show()
}

// ---------------------------------------------------------------------------------------------
// App shell
// ---------------------------------------------------------------------------------------------

@Composable
fun App(today: LocalDate) {
    val context = LocalContext.current
    var role by remember { mutableStateOf(Prefs.role(context)) }
    val chooseRole: (Role) -> Unit = {
        Prefs.setRole(context, it)
        Notifier.subscribeTopics(it)
        ReminderScheduler.scheduleNext(context)
        role = it
    }

    val current = role
    if (current == null) {
        Surface(Modifier.fillMaxSize()) { RoleScreen(chooseRole) }
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) }, label = { Text("Today") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = null) }, label = { Text("Calendar") },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) }, label = { Text("Settings") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> TodayScreen(current, today)
                1 -> CalendarScreen(current, today)
                else -> SettingsScreen(current, chooseRole)
            }
        }
    }
}

@Composable
private fun RoleScreen(onChoose: (Role) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Who is using this phone?", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(onClick = { onChoose(Role.DINER) }, modifier = Modifier.fillMaxWidth().height(72.dp)) {
            Text("I'm the one coming home for dinner", textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onChoose(Role.HOUSEHOLD) }, modifier = Modifier.fillMaxWidth().height(72.dp)) {
            Text("I'm at home and want to know", textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        Text("You can change this later in Settings.", style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------------------------------------------------------------------------------------
// Today
// ---------------------------------------------------------------------------------------------

@Composable
private fun TodayScreen(role: Role, today: LocalDate) {
    val day by remember(today) { Repo.day(today) }.collectAsState(initial = null)
    val defaultTime by remember { Repo.defaultDinnerTime() }.collectAsState(initial = DEFAULT_DINNER_TIME)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val d = day
        if (d == null) {
            Text("Connecting…", style = MaterialTheme.typography.titleMedium)
        } else if (role == Role.DINER) {
            Text(
                "Will you be at dinner tonight?",
                style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text("Dinner is at ${(d.dinnerTime ?: defaultTime).display()}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(32.dp))
            DinerControls(today, d, large = true)
        } else {
            Text("Dinner tonight", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(24.dp))
            StatusCard(d)
            Spacer(Modifier.height(32.dp))
            DinnerTimeControls(today, d, defaultTime)
        }
    }
}

/** Yes / No buttons, the "home by" time and a way to clear the answer. Used on Today and in the calendar. */
@Composable
private fun DinerControls(date: LocalDate, day: Day, large: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val buttonHeight = if (large) 96.dp else 56.dp

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        AnswerButton("Yes", Green, selected = day.status == Status.YES, dimmed = day.status == Status.NO,
            modifier = Modifier.weight(1f).height(buttonHeight), large = large,
        ) { scope.save(context) { Repo.setStatus(date, Status.YES) } }
        AnswerButton("No", Red, selected = day.status == Status.NO, dimmed = day.status == Status.YES,
            modifier = Modifier.weight(1f).height(buttonHeight), large = large,
        ) { scope.save(context) { Repo.setStatus(date, Status.NO) } }
    }

    if (day.status == Status.YES) {
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = {
            pickTime(context, day.arrivalTime ?: LocalTime.of(17, 0)) { picked ->
                scope.save(context) { Repo.setArrivalTime(date, picked) }
            }
        }) {
            Text(day.arrivalTime?.let { "I'll be home by ${it.display()}" } ?: "What time will you be home?")
        }
    }
    if (day.status != Status.UNKNOWN) {
        TextButton(onClick = { scope.save(context) { Repo.setStatus(date, Status.UNKNOWN) } }) {
            Text("Clear my answer")
        }
    }
}

@Composable
private fun AnswerButton(
    label: String, color: Color, selected: Boolean, dimmed: Boolean,
    modifier: Modifier, large: Boolean, onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (dimmed) color.copy(alpha = 0.3f) else color,
            contentColor = Color.White,
        ),
    ) {
        Text(
            if (selected) "✓ $label" else label,
            fontSize = if (large) 28.sp else 18.sp, fontWeight = FontWeight.Bold,
        )
    }
}

/** What the household sees: a big coloured answer. */
@Composable
private fun StatusCard(day: Day) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(day.status.color()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            when (day.status) {
                Status.YES -> "Coming to dinner"
                Status.NO -> "Not coming to dinner"
                Status.UNKNOWN -> "No answer yet"
            },
            color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        )
        if (day.status == Status.YES) {
            Spacer(Modifier.height(8.dp))
            Text(
                day.arrivalTime?.let { "Home by ${it.display()}" } ?: "No arrival time given",
                color = Color.White, fontSize = 18.sp,
            )
        }
    }
}

/** Lets the household move dinner for one day, or put it back to the usual time. */
@Composable
private fun DinnerTimeControls(date: LocalDate, day: Day, defaultTime: LocalTime) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val time = day.dinnerTime ?: defaultTime

    Text("Dinner is at ${time.display()}", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = {
        pickTime(context, time) { picked ->
            // Picking the usual time is the same as having no override.
            scope.save(context) { Repo.setDinnerTime(date, picked.takeIf { it != defaultTime }) }
        }
    }) { Text("Change dinner time") }
    if (day.dinnerTime != null) {
        TextButton(onClick = { scope.save(context) { Repo.setDinnerTime(date, null) } }) {
            Text("Back to the usual ${defaultTime.display()}")
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Calendar
// ---------------------------------------------------------------------------------------------

@Composable
private fun CalendarScreen(role: Role, today: LocalDate) {
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val days by remember(month) { Repo.month(month) }.collectAsState(initial = emptyMap())
    val defaultTime by remember { Repo.defaultDinnerTime() }.collectAsState(initial = DEFAULT_DINNER_TIME)

    val firstDayOfWeek = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val leadingBlanks = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val cells: List<LocalDate?> =
        List(leadingBlanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Text(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                Text(
                    firstDayOfWeek.plus(i.toLong()).getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        for (week in cells.chunked(7)) {
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until 7) {
                    val date = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            val status = days[date]?.status ?: Status.UNKNOWN
                            Box(
                                Modifier.fillMaxSize().clip(CircleShape).background(status.color())
                                    .then(
                                        if (date == today)
                                            Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { selected = date },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(date.dayOfMonth.toString(), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LegendItem(Green, "Coming")
            LegendItem(Red, "Not coming")
            LegendItem(Gray, "Not set")
        }
    }

    selected?.let { date ->
        val day = days[date] ?: Day()
        AlertDialog(
            onDismissRequest = { selected = null },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Done") } },
            title = { Text(date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))) },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (role == Role.DINER) {
                        Text("Dinner is at ${(day.dinnerTime ?: defaultTime).display()}")
                        Spacer(Modifier.height(16.dp))
                        DinerControls(date, day, large = false)
                    } else {
                        StatusCard(day)
                        Spacer(Modifier.height(16.dp))
                        DinnerTimeControls(date, day, defaultTime)
                    }
                }
            },
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------------------------

@Composable
private fun SettingsScreen(role: Role, onChooseRole: (Role) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val defaultTime by remember { Repo.defaultDinnerTime() }.collectAsState(initial = DEFAULT_DINNER_TIME)
    var reminders by remember { mutableStateOf(Prefs.remindersEnabled(context)) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        Text("This phone belongs to", style = MaterialTheme.typography.titleMedium)
        RoleOption("The person coming home for dinner", role == Role.DINER) { onChooseRole(Role.DINER) }
        RoleOption("Someone at home who wants to know", role == Role.HOUSEHOLD) { onChooseRole(Role.HOUSEHOLD) }
        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        if (role == Role.DINER) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Remind me", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "At " + ReminderScheduler.REMINDER_TIMES.joinToString(", ") { it.display() } +
                            ", until I've answered for the day.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(checked = reminders, onCheckedChange = {
                    reminders = it
                    Prefs.setRemindersEnabled(context, it)
                    ReminderScheduler.scheduleNext(context)
                })
            }
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Usual dinner time", style = MaterialTheme.typography.titleMedium)
            Text(defaultTime.display(), style = MaterialTheme.typography.bodyLarge)
        } else {
            Text("Usual dinner time", style = MaterialTheme.typography.titleMedium)
            Text(
                "Used for every day unless you change that day's time.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                pickTime(context, defaultTime) { picked -> scope.save(context) { Repo.setDefaultDinnerTime(picked) } }
            }) { Text(defaultTime.display()) }
        }
        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        TextButton(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }) { Text("Notification settings") }
    }
}

@Composable
private fun RoleOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}
