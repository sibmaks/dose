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

/** Immutable membership keeps a stale notification from accepting a newly added or moved dose. */
object GroupDoseActions {
    const val TAKEN = "io.github.sibmaks.app.dose.TAKEN_ALL"
    const val SKIP = "io.github.sibmaks.app.dose.SKIP_ALL"
    private const val KEYS = "groupKeys"
    private const val SCHEDULED = "groupScheduled"
    private const val DUE = "groupDue"

    fun pending(
        context: Context,
        action: String,
        items: List<DoseItem>
    ): PendingIntent {
        require(action == TAKEN || action == SKIP)
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
        record(
            context, keys.indices.map { DoseGroups.Member(keys[it], scheduled[it], due[it]) },
            if (intent.action == TAKEN) "taken" else "skipped"
        )
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
