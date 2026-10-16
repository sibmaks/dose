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
}
