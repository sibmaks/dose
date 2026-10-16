package io.github.sibmaks.app.dose.data

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class DoseWidgetStateTest {
    private val now = ZonedDateTime.of(2026, 10, 16, 9, 0, 0, 0, ZoneId.systemDefault())
    private val med = Medication("id", "Test", "1", "mg", listOf(DoseSchedule(times = listOf("08:00", "12:00"), createdOn = "2026-10-16")))
    private fun at(time: String) = DoseLogic.scheduledMillis(now.toLocalDate(), time)
    private fun items(logs: List<DoseLog> = emptyList(), snoozes: Map<String, Long> = emptyMap(), overrides: Map<String, String> = emptyMap()) =
        DoseLogic.todayItems(listOf(med), logs, snoozes, now.toInstant().toEpochMilli(), now.toLocalDate(), overrides)
    @Test fun `overdue stays visible until taken or skipped`() {
        assertEquals("08:00", DoseWidgetState.next(items())!!.scheduledTime)
        val log = DoseLogic.acceptedAt(items().first(), at("08:30"), at("09:00"))
        assertEquals("12:00", DoseWidgetState.next(items(listOf(log)))!!.scheduledTime)
        assertEquals("12:00", DoseWidgetState.next(items(listOf(log.copy(status = "skipped"))))!!.scheduledTime)
    }
    @Test fun `snooze and daily changes determine displayed order and refresh`() {
        val key = DoseLogic.slotKey("2026-10-16", "id", "08:00")
        val moved = items(overrides = mapOf(key to "13:00"))
        assertEquals("12:00", DoseWidgetState.next(moved)!!.scheduledTime)
        assertEquals(at("12:00"), DoseWidgetState.nextRefresh(moved, now))
        val snoozed = items(snoozes = mapOf(key to at("09:10")))
        assertEquals(at("09:10"), DoseWidgetState.next(snoozed)!!.dueMillis)
        assertEquals(at("09:10"), DoseWidgetState.nextRefresh(snoozed, now))
    }
    @Test fun `empty or completed day clears widget and refreshes at local midnight`() {
        val completed = items().map { it.copy(log = DoseLogic.acceptedAt(it, at("08:30"), at("09:00"))) }
        assertNull(DoseWidgetState.next(completed))
        assertNull(DoseWidgetState.next(emptyList()))
        assertEquals(now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli(), DoseWidgetState.nextRefresh(completed, now))
    }
    @Test fun `new day selects only its own schedule`() {
        val tomorrow = now.toLocalDate().plusDays(1)
        val next = DoseWidgetState.next(DoseLogic.todayItems(listOf(med), emptyList(), emptyMap(), tomorrow.atStartOfDay(now.zone).toInstant().toEpochMilli(), tomorrow))!!
        assertEquals(tomorrow.toString(), next.date)
    }
    @Test fun `widget shows all pending medicines at the next shared instant`() {
        val second = med.copy(id = "second", name = "Second", color = "#FFFFFF")
        val current = DoseLogic.todayItems(listOf(med, second), emptyList(), emptyMap(), now.toInstant().toEpochMilli(), now.toLocalDate())
        val group = DoseWidgetState.nextGroup(current)
        assertEquals(setOf("id", "second"), group.map { it.medication.id }.toSet())
        assertEquals(setOf("08:00"), group.map { it.time }.toSet())
        assertEquals(setOf("violet", "#FFFFFF"), group.map { it.medication.color }.toSet())
        val logs = DoseGroups.logs(group, "skipped", now.toInstant().toEpochMilli())
        val remaining = DoseLogic.todayItems(listOf(med, second), logs, emptyMap(), now.toInstant().toEpochMilli(), now.toLocalDate())
        assertEquals(setOf("12:00"), DoseWidgetState.nextGroup(remaining).map { it.time }.toSet())
        assertTrue(DoseWidgetState.nextGroup(emptyList()).isEmpty())
    }

    @Test fun `resized widget reserves header and footer space and grows the visible list`() {
        assertEquals(0, DoseWidgetState.visibleRows(219f, 8))
        assertEquals(1, DoseWidgetState.visibleRows(220f, 8))
        assertEquals(2, DoseWidgetState.visibleRows(332f, 8))
        assertEquals(3, DoseWidgetState.visibleRows(444f, 8))
        assertEquals(2, DoseWidgetState.visibleRows(900f, 2))
        assertEquals(0, DoseWidgetState.visibleRows(900f, 0))
        for (height in 220..1000) assertTrue(DoseWidgetState.visibleRows(height.toFloat(), 20) * DoseWidgetState.ROW_HEIGHT <= height - 96)
    }

    @Test fun `widget list excludes taken and skipped while preserving pending snoozed and moved doses`() {
        val current = items()
        val morning = current.first()
        val taken = DoseGroups.logs(listOf(morning), "taken", now.toInstant().toEpochMilli()).single()
        val skipped = taken.copy(status = "skipped")
        for (log in listOf(taken, skipped)) {
            val pending = DoseWidgetState.pending(items(logs = listOf(log)))
            assertEquals(listOf("12:00"), pending.map { it.time })
            assertTrue(pending.all { it.log == null })
        }
        val deferred = DoseWidgetState.pending(items(snoozes = mapOf(morning.id to at("13:00"))))
        assertEquals(listOf("12:00", "08:00"), deferred.map { it.time })
        assertEquals("snoozed", deferred.last().status)
        assertTrue(DoseWidgetState.pending(items(logs = DoseGroups.logs(current, "taken", now.toInstant().toEpochMilli()))).isEmpty())
    }

    @Test fun `all taken message requires every scheduled dose to be taken`() {
        val current = items()
        val logs = DoseGroups.logs(current, "taken", now.toInstant().toEpochMilli())
        assertTrue(DoseWidgetState.allTaken(items(logs = logs)))
        assertFalse(DoseWidgetState.allTaken(emptyList()))
        assertFalse(DoseWidgetState.allTaken(current))
        assertFalse(DoseWidgetState.allTaken(items(logs = logs.take(1))))
        assertFalse(DoseWidgetState.allTaken(items(logs = logs.map { it.copy(status = "skipped") })))
        assertFalse(DoseWidgetState.allTaken(items(logs = listOf(logs[0], logs[1].copy(status = "skipped")))))
        // Undoing a taken record restores the pending state rather than keeping the celebration.
        assertFalse(DoseWidgetState.allTaken(items(logs = logs.drop(1))))
    }

}
