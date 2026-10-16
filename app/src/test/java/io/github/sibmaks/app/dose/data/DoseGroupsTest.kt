package io.github.sibmaks.app.dose.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DoseGroupsTest {
    private val date = LocalDate.of(2026, 10, 16)
    private val now = DoseLogic.scheduledMillis(date, "09:00")
    private val a = Medication("a", "A", "1", "mg", listOf(DoseSchedule(times = listOf("08:00", "12:00"), createdOn = date.toString())))
    private val b = a.copy(id = "b", name = "B")
    private fun items(meds: List<Medication> = listOf(a, b), logs: List<DoseLog> = emptyList(),
                      snoozes: Map<String, Long> = emptyMap(), overrides: Map<String, String> = emptyMap()) =
        DoseLogic.todayItems(meds, logs, snoozes, now, date, overrides)

    @Test fun `group contains only pending doses at the same instant`() {
        val current = items()
        val group = DoseGroups.together(current, current.first())
        assertEquals(setOf("a", "b"), group.map { it.medication.id }.toSet())
        assertEquals(listOf("08:00", "08:00"), group.map { it.time })
        val log = DoseGroups.logs(group.take(1), "taken", now).single()
        assertEquals(1, DoseGroups.together(items(logs = listOf(log)), items(logs = listOf(log))[1]).size)
        assertTrue(DoseGroups.together(current, null).isEmpty())
    }

    @Test fun `stale group cannot overwrite recorded moved snoozed removed or new doses`() {
        val current = items()
        val group = DoseGroups.together(current, current.first())
        val snapshot = DoseGroups.snapshot(group)
        val log = DoseGroups.logs(group.take(1), "skipped", now).single()
        assertEquals(listOf("b"), DoseGroups.eligible(items(logs = listOf(log)), snapshot).map { it.medication.id })
        assertEquals(listOf("b"), DoseGroups.eligible(items(overrides = mapOf(group.first().id to "10:00")), snapshot).map { it.medication.id })
        assertEquals(listOf("b"), DoseGroups.eligible(items(snoozes = mapOf(group.first().id to now + 600_000)), snapshot).map { it.medication.id })
        assertEquals(listOf("b"), DoseGroups.eligible(items(listOf(b, a.copy(id = "new"))), snapshot).map { it.medication.id })
        assertTrue(DoseGroups.eligible(items(listOf(a.copy(active = false))), snapshot).isEmpty())
    }

    @Test fun `bulk acceptance records click time and preserves each original deadline`() {
        val current = items(meds = listOf(a, b.copy(schedules = listOf(DoseSchedule(times = listOf("07:00"), createdOn = date.toString())))),
            snoozes = mapOf("$date|a|08:00" to now, "$date|b|07:00" to now))
        val group = DoseGroups.together(current, current.first { it.medication.id == "a" && it.time == "08:00" })
        assertEquals(2, group.size)
        val logs = DoseGroups.logs(group, "taken", now + 1_000)
        assertEquals(setOf(now + 1_000), logs.map { it.recordedAt }.toSet())
        assertEquals(setOf(DoseLogic.scheduledMillis(date, "08:00"), DoseLogic.scheduledMillis(date, "07:00")), logs.map { it.scheduledAt }.toSet())
        assertTrue(logs.all { it.clickedAt == now + 1_000 && !it.onTime })
        assertTrue(DoseGroups.eligible(items(logs = logs), DoseGroups.snapshot(group)).isEmpty())
    }

    @Test fun `overrides group by actual reminder time and duplicate snapshots record once`() {
        val current = items(overrides = mapOf("$date|a|08:00" to "10:00", "$date|b|12:00" to "10:00"))
        val anchor = current.first { it.scheduledTime == "10:00" }
        val group = DoseGroups.together(current, anchor)
        assertEquals(2, group.size)
        assertEquals(2, DoseGroups.eligible(current, DoseGroups.snapshot(group + group)).size)
        val tomorrow = DoseLogic.todayItems(listOf(a, b), emptyList(), emptyMap(), now, date.plusDays(1))
        assertTrue(DoseGroups.eligible(tomorrow, DoseGroups.snapshot(group)).isEmpty())
    }
}
