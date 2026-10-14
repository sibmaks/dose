package com.example.dose.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DoseLogicTest {
    @Test fun nextOccurrenceTodayBeforeDose() {
        val now = ZonedDateTime.of(LocalDate.of(2026, 10, 14), LocalTime.of(7, 0), ZoneId.of("Europe/Helsinki"))
        val expected = ZonedDateTime.of(LocalDate.of(2026, 10, 14), LocalTime.of(8, 0), now.zone)
        assertEquals(expected.toInstant().toEpochMilli(), DoseLogic.nextOccurrence("08:00", now, false))
    }

    @Test fun nextOccurrenceTomorrowAfterDoseOrRecorded() {
        val now = ZonedDateTime.of(LocalDate.of(2026, 10, 14), LocalTime.of(9, 0), ZoneId.of("Europe/Helsinki"))
        val expected = ZonedDateTime.of(LocalDate.of(2026, 10, 15), LocalTime.of(8, 0), now.zone)
        assertEquals(expected.toInstant().toEpochMilli(), DoseLogic.nextOccurrence("08:00", now, false))
        assertEquals(expected.toInstant().toEpochMilli(), DoseLogic.nextOccurrence("08:00", now, true))
    }

    @Test fun recordedDoseBeatsUpcomingTime() {
        val date = LocalDate.of(2026, 10, 14)
        val medication = Medication("med-1", "Test medication", "1 tablet", listOf("08:00"))
        val log = DoseLog(date.toString(), medication.id, medication.name, medication.dose, "08:00", "taken", 123L)
        val now = DoseLogic.scheduledMillis(date, "07:30", ZoneId.systemDefault())
        val result = DoseLogic.todayItems(listOf(medication), listOf(log), emptyMap(), now, date)
        assertEquals("taken", result.single().status)
    }

    @Test fun snoozedDoseReportsSnoozedUntilNewDueTime() {
        val date = LocalDate.of(2026, 10, 14)
        val medication = Medication("med-2", "Test medication", "2 tablets", listOf("08:00"))
        val at = DoseLogic.scheduledMillis(date, "09:00")
        val snooze = at + 600_000L
        val key = DoseLogic.slotKey(date.toString(), medication.id, "08:00")
        val before = DoseLogic.todayItems(listOf(medication), emptyList(), mapOf(key to snooze), at, date)
        val after = DoseLogic.todayItems(listOf(medication), emptyList(), mapOf(key to snooze), snooze, date)
        assertEquals("snoozed", before.single().status)
        assertEquals("due", after.single().status)
    }
}
