package com.example.dose.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MedicationJsonTest {
    private val today = LocalDate.of(2026, 10, 14)
    @Test fun oldDailyMedicationMigratesWithoutLosingValues() {
        val old = JSONObject("""{"id":"old","name":"Vitamin","dose":"2 tablets","times":["20:00","08:00"],"note":"With food","color":"mint","active":false}""")
        val med = MedicationJson.decode(old, today)!!
        assertEquals("2", med.amount); assertEquals("tablets", med.quantity)
        assertEquals("2 tablets", med.dose); assertEquals(MedicationForm.PILL, med.form)
        assertEquals(listOf("08:00", "20:00"), med.timesOn(today))
        assertEquals("With food", med.note); assertEquals("mint", med.color); assertFalse(med.active)
        assertEquals(today.toString(), med.schedules.single().createdOn)
    }
    @Test fun structuredRoundTripKeepsAllRulesAndCustomValues() {
        val med = Medication("id", "Test", "1.5", "custom unit", listOf(
            DoseSchedule(ScheduleKind.SPECIFIC_DATES, listOf("08:00", "20:00"), dates = listOf("2026-12-01", "2026-12-03"), createdOn = today.toString()),
            DoseSchedule(ScheduleKind.WEEKDAYS, weekDays = listOf(1, 5), from = "2026-11-01", until = "2027-01-01", createdOn = today.toString()),
            DoseSchedule(ScheduleKind.MONTH_DAYS, monthDays = listOf(1, 31), createdOn = today.toString()),
            DoseSchedule(createdOn = today.toString()),
        ), note = "Note", color = "#123ABC", form = MedicationForm.SPRAY)
        val decoded = MedicationJson.decode(JSONObject(MedicationJson.encode(med).toString()), today.plusDays(8))
        assertEquals(med, decoded)
    }
    @Test fun legacyUnstructuredDoseIsPreservedAcrossMigration() {
        val med = MedicationJson.decode(JSONObject("""{"id":"old","name":"Test","dose":"As directed","times":["08:00"]}"""), today)!!
        assertEquals("As directed", med.dose)
        assertEquals(med, MedicationJson.decode(MedicationJson.encode(med), today.plusDays(1)))
    }
    @Test fun malformedEntryIsRejectedIndependently() {
        assertNull(MedicationJson.decode(JSONObject("""{"id":"old","name":"Test","dose":"1 mg","times":["bad"]}"""), today))
        val med = Medication("id", "Test", "1", "mg", listOf(DoseSchedule(createdOn = today.toString())))
        val bad = MedicationJson.encode(med)
        bad.getJSONArray("schedules").getJSONObject(0).put("kind", "UNKNOWN")
        assertNull(MedicationJson.decode(bad, today))
        assertNotNull(MedicationJson.decode(MedicationJson.encode(med), today))
    }
}
