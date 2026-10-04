package app.jonaki.phone

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.jonaki.JonakiApplication
import app.jonaki.R
import app.jonaki.core.ui.JonakiTheme
import app.jonaki.ui.themeModeOf
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The sheet behind the notification's "Later…" button: 1 hour, 1 day or a
 * date and time of the user's own. It is a see-through activity, so the
 * app or screen the user was on stays visible around it.
 */
class ReminderLaterActivity : ComponentActivity() {
    private var reminderId by mutableStateOf<String?>(null)

    private val reminders
        get() = (application as JonakiApplication).reminders

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reminderId = intent.getStringExtra(ReminderReceiver.EXTRA_REMINDER_ID)
        val jonaki = application as JonakiApplication
        setContent {
            val themeChoice = jonaki.settings.snapshot.value.theme
            JonakiTheme(themeMode = themeModeOf(themeChoice)) {
                Sheet()
            }
        }
    }

    /** The activity is single-task, so a second reminder's "Later…" arrives here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        reminderId = intent.getStringExtra(ReminderReceiver.EXTRA_REMINDER_ID)
    }

    @Composable
    private fun Sheet() {
        val id = reminderId ?: return finish()
        // Done on another screen can remove the reminder while this sheet is open.
        val reminder = reminders.waiting(id) ?: return finish()
        AlertDialog(
            onDismissRequest = ::finish,
            title = { Text(stringResource(R.string.reminder_later_title)) },
            text = {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Text(reminder.text, style = MaterialTheme.typography.bodyLarge)
                    Column(Modifier.padding(top = 8.dp), horizontalAlignment = Alignment.Start) {
                        ChoiceButton(R.string.reminder_later_hour) { snoozeFor(id, SnoozeChoice.ONE_HOUR) }
                        ChoiceButton(R.string.reminder_later_day) { snoozeFor(id, SnoozeChoice.ONE_DAY) }
                        ChoiceButton(R.string.reminder_later_pick) { pickDateAndTime(id) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = ::finish) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    @Composable
    private fun ChoiceButton(label: Int, onClick: () -> Unit) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(label), modifier = Modifier.fillMaxWidth())
        }
    }

    private fun snoozeFor(id: String, choice: SnoozeChoice) {
        val untilMillis = ReminderSchedule.snoozeTime(choice, System.currentTimeMillis(), ZoneId.systemDefault())
        snoozeUntil(id, untilMillis)
    }

    /** Android's own date dialog, then its time dialog; Cancel on either leaves the reminder as it was. */
    private fun pickDateAndTime(id: String) {
        val today = LocalDate.now()
        DatePickerDialog(
            this,
            { _, year, monthIndex, day -> pickTime(id, LocalDate.of(year, monthIndex + 1, day)) },
            today.year,
            today.monthValue - 1,
            today.dayOfMonth,
        ).show()
    }

    private fun pickTime(id: String, date: LocalDate) {
        val inOneHour = LocalTime.now().plusHours(1)
        TimePickerDialog(
            this,
            { _, hour, minute ->
                val picked = ReminderSchedule.pickedTime(LocalDateTime.of(date, LocalTime.of(hour, minute)), ZoneId.systemDefault())
                if (ReminderSchedule.isUsablePick(picked, System.currentTimeMillis())) {
                    snoozeUntil(id, picked)
                } else {
                    Toast.makeText(this, R.string.reminder_later_past, Toast.LENGTH_SHORT).show()
                }
            },
            inOneHour.hour,
            inOneHour.minute,
            android.text.format.DateFormat.is24HourFormat(this),
        ).show()
    }

    private fun snoozeUntil(id: String, untilMillis: Long) {
        reminders.snooze(id, untilMillis)
        finish()
    }
}
