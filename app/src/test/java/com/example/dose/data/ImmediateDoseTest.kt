package com.example.dose.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ImmediateDoseTest {
    private val now = ZonedDateTime.of(2026, 10, 15, 16, 20, 45, 123_000_000, ZoneId.of("Europe/Moscow"))
    private val instant = now.toInstant().toEpochMilli()
    private fun medication(rule: DoseSchedule = DoseSchedule.immediately(now)) = Medication("id", "Test", "1", "tablet", listOf(rule))

    @Test fun nowCapturesTheSaveInstantInsteadOfRoundingToMinuteOrNextDay() {
        val rule = DoseSchedule.immediately(now)
        assertEquals(instant, rule.immediateAt)
        assertEquals(listOf("16:20"), rule.times)
        assertEquals(listOf("2026-10-15"), rule.dates)
        assertNull(rule.validationError())
    }
    @Test fun singleDateIsCorrectAtMidnightAndNeverRepeatsTomorrow() {
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        val rule = DoseSchedule.immediately(midnight)
        assertEquals(listOf("00:00"), rule.times)
        assertTrue(rule.includes(LocalDate.of(2026, 10, 16)))
        assertFalse(rule.includes(LocalDate.of(2026, 10, 17)))
    }
    @Test fun todayCardUsesTheExactSaveTimeRatherThanStartOfMinute() {
        val med = medication()
        assertEquals(instant, med.scheduledMillis(now.toLocalDate(), "16:20"))
        assertEquals("upcoming", DoseLogic.todayItems(listOf(med), emptyList(), emptyMap(), instant - 1, now.toLocalDate()).single().status)
        assertEquals("due", DoseLogic.todayItems(listOf(med), emptyList(), emptyMap(), instant, now.toLocalDate()).single().status)
    }
    @Test fun savingANewNowRequestDeliversButEditingInstructionsDoesNotRedeliver() {
        val med = medication()
        assertEquals(med.schedules, DoseLogic.newImmediateSchedules(med, null))
        assertTrue(DoseLogic.newImmediateSchedules(med.copy(note = "Updated instructions"), med).isEmpty())
        assertEquals(1, DoseLogic.newImmediateSchedules(medication(DoseSchedule.immediately(now.plusMinutes(1))), med).size)
    }
    @Test fun pausedOrMalformedImmediateRequestsAreNotDispatched() {
        assertTrue(DoseLogic.newImmediateSchedules(medication().copy(active = false), null).isEmpty())
        val bad = DoseSchedule.immediately(now).copy(times = listOf("16:20", "17:20"))
        assertNotNull(bad.validationError())
        assertTrue(DoseLogic.newImmediateSchedules(medication(bad), null).isEmpty())
        assertNotNull(DoseSchedule.immediately(now).copy(kind = ScheduleKind.DAILY).validationError())
    }
    @Test fun restartingOrReschedulingPastNowDoesNotCreateAnotherAlarm() {
        val med = medication()
        assertEquals(instant, DoseLogic.nextOccurrence(med, "16:20", now.minusSeconds(1), emptyList()))
        assertNull(DoseLogic.nextOccurrence(med, "16:20", now.plusSeconds(1), emptyList()))
        assertNull(DoseLogic.nextOccurrence(med, "16:20", now.plusDays(1), emptyList()))
    }
    @Test fun exactTimestampAndCustomFormSurvivePersistenceTogether() {
        val med = medication().copy(form = MedicationForm.OTHER, customForm = "Patch")
        val restored = MedicationJson.decode(JSONObject(MedicationJson.encode(med).toString()))!!
        assertEquals(med, restored)
        assertEquals(instant, restored.scheduledMillis(now.toLocalDate(), "16:20", ZoneId.of("America/New_York")))
    }
    @Test fun oldSpecificDateRulesRemainMinuteBased() {
        val json = MedicationJson.encode(medication(DoseSchedule(kind = ScheduleKind.SPECIFIC_DATES, times = listOf("16:20"), dates = listOf("2026-10-15"))))
        json.getJSONArray("schedules").getJSONObject(0).remove("immediateAt")
        val med = MedicationJson.decode(json)!!
        assertNull(med.schedules.single().immediateAt)
        assertEquals(DoseLogic.scheduledMillis(now.toLocalDate(), "16:20", now.zone), med.scheduledMillis(now.toLocalDate(), "16:20", now.zone))
    }
    @Test fun overlappingImmediateRequestsProduceOnlyOneDeliveryAndTodayItem() {
        val med = medication().let { it.copy(schedules = it.schedules + it.schedules) }
        assertEquals(1, DoseLogic.newImmediateSchedules(med, null).size)
        assertEquals(1, DoseLogic.todayItems(listOf(med), emptyList(), emptyMap(), instant, now.toLocalDate()).size)
    }
    @Test fun delayReportIncludesOnlyTimeAfterTheImmediateRequest() {
        val scheduled = medication().scheduledMillis(now.toLocalDate(), "16:20")
        val log = DoseLog("2026-10-15", "id", "Test", "1 tablet", "16:20", "taken", instant + 15_000, scheduled)
        assertEquals(15_000L, DoseReports.timing(log)!!.delayMillis)
    }
    @Test fun aNewMedicationIsIndependentEvenWithAnIdenticalRequestTime() {
        val previous = medication().copy(id = "another-id")
        assertEquals(1, DoseLogic.newImmediateSchedules(medication(), previous).size)
    }
}
