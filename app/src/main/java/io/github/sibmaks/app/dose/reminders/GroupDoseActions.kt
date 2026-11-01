package io.github.sibmaks.app.dose.reminders

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import io.github.sibmaks.app.dose.data.DoseGroups
import io.github.sibmaks.app.dose.data.DoseItem
import io.github.sibmaks.app.dose.data.DoseLogic
import io.github.sibmaks.app.dose.data.DoseStore
import java.security.MessageDigest

/** Immutable membership keeps stale reminder actions from changing newly added or moved doses. */
object GroupDoseActions {
    const val TAKEN = "io.github.sibmaks.app.dose.TAKEN_ALL"
    const val SKIP = "io.github.sibmaks.app.dose.SKIP_ALL"
    const val SNOOZE = "io.github.sibmaks.app.dose.SNOOZE_ALL"
    private const val KEYS = "groupKeys"
    private const val SCHEDULED = "groupScheduled"
    private const val DUE = "groupDue"

    fun pending(
        context: Context,
        action: String,
        items: List<DoseItem>
    ): PendingIntent {
        require(action == TAKEN || action == SKIP || action == SNOOZE)
        val members =
            DoseGroups.snapshot(items)
                .sortedBy { it.key }
        val digest =
            MessageDigest.getInstance("SHA-256")
                .digest(
                    members.toString()
                        .toByteArray()
                )
                .joinToString("") { "%02x".format(it) }
        val intent = Intent(context, ReminderReceiver::class.java).setAction(action)
            .setData("dose://group/$action/$digest".toUri())
            .putStringArrayListExtra(KEYS, ArrayList(members.map { it.key }))
            .putExtra(
                SCHEDULED,
                members.map { it.scheduledAt }
                    .toLongArray()
            )
            .putExtra(
                DUE,
                members.map { it.dueMillis }
                    .toLongArray()
            )
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun receive(
        context: Context,
        intent: Intent
    ) {
        val keys = intent.getStringArrayListExtra(KEYS) ?: return
        val scheduled = intent.getLongArrayExtra(SCHEDULED) ?: return
        val due = intent.getLongArrayExtra(DUE) ?: return
        if (keys.size != scheduled.size || keys.size != due.size) return
        val members = keys.indices.map { DoseGroups.Member(keys[it], scheduled[it], due[it]) }
        when (intent.action) {
            TAKEN -> record(context, members, "taken")
            SKIP -> record(context, members, "skipped")
            SNOOZE -> snooze(context, members)
        }
    }

    fun snooze(context: Context, members: List<DoseGroups.Member>) {
        val now = System.currentTimeMillis()
        val current = DoseLogic.todayItems(
            DoseStore.medications(context), DoseStore.logs(context), DoseStore.snoozes(context), now,
            overrides = DoseStore.timeOverrides(context)
        )
        DoseGroups.eligible(current, members).forEach { item ->
            // One instant keeps the doses grouped when their reminders return.
            ReminderScheduler.snooze(context, item.medication.id, item.time, item.date, now = now)
            VibrationRepeat.stop(context, item.id)
            NotificationManagerCompat.from(context)
                .cancel(ReminderReceiver.notificationId(item.medication.id, item.time, item.date))
        }
        ReminderReceiver.refreshVisible(context)
        io.github.sibmaks.app.dose.widget.DoseWidget.updateAll(context)
    }

    fun record(
        context: Context,
        members: List<DoseGroups.Member>,
        status: String
    ) {
        val recorded = DoseStore.recordGroup(context, members, status)
        recorded.forEach { item ->
            ReminderScheduler.cancelSnooze(context, item.medication.id, item.time, item.date)
            VibrationRepeat.stop(context, DoseLogic.slotKey(item.date, item.medication.id, item.time))
            NotificationManagerCompat.from(context)
                .cancel(ReminderReceiver.notificationId(item.medication.id, item.time, item.date))
            ReminderScheduler.refreshSlot(context, item.medication.id, item.time)
        }
        ReminderReceiver.refreshVisible(context)
        io.github.sibmaks.app.dose.widget.DoseWidget.updateAll(context)
    }
}
