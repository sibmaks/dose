package io.github.sibmaks.app.dose.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DoseScheduleAmountTest {
    private val monday = LocalDate.of(2026, 10, 26)
    private fun daily(amount: String? = null, time: String = "08:00") =
        DoseSchedule(times = listOf(time), createdOn = monday.toString(), amount = amount)
    private fun weekly(days: List<Int>, amount: String? = null) =
        daily(amount).copy(kind = ScheduleKind.WEEKDAYS, weekDays = days)
    private fun medication(vararg schedules: DoseSchedule) = Medication("med", "Test", "1", "tablet", schedules.toList())
    private fun conflicts(vararg schedules: DoseSchedule) = DoseScheduleConflicts.unresolved(schedules.toList(), "1")
    private fun items(med: Medication, date: LocalDate = monday, snoozes: Map<String, Long> = emptyMap(), overrides: Map<String, String> = emptyMap()) =
        DoseLogic.todayItems(listOf(med), emptyList(), snoozes, DoseLogic.scheduledMillis(date, "09:00"), date, overrides)

    @Test fun `each time uses its matching schedule amount without creating duplicate slots`() {
        val med = medication(daily(), daily("2", "20:00"))
        assertEquals(listOf("1", "2"), items(med).map { it.amount })
        assertEquals(listOf("1 tablet", "2 tablet"), items(med).map { it.dose })
        assertTrue(conflicts(*med.schedules.toTypedArray()).isEmpty())
    }

    @Test fun `overlap asks for priority then keeps both nonoverlapping occurrences`() {
        val daily = daily()
        val weekly = daily("2").copy(kind = ScheduleKind.WEEKDAYS, weekDays = listOf(1))
        val group = conflicts(daily, weekly).single()
        assertEquals(listOf("08:00"), group.times)
        assertEquals(monday, group.date)
        assertEquals(listOf(0, 1), group.members)
        val resolved = DoseScheduleConflicts.resolveGroup(listOf(daily, weekly), 1, group.members)
        assertTrue(DoseScheduleConflicts.unresolved(resolved, "1").isEmpty())
        val med = medication(*resolved.toTypedArray())
        assertEquals("2", items(med).single().amount)
        assertEquals("1", items(med, monday.plusDays(1)).single().amount)
        assertEquals(listOf("08:00"), med.timesOn(monday))
    }

    @Test fun `a shared time gathers every differing dose into one group and one choice resolves it`() {
        val rules = listOf(daily(), daily("2"), daily("3"))
        // Three schedules at the same time are one accordion, not a collection of pairs.
        assertEquals(1, DoseScheduleConflicts.coincidences(rules, "1").size)
        val group = DoseScheduleConflicts.coincidences(rules, "1").single()
        assertEquals(listOf("08:00"), group.times)
        assertEquals(listOf(0, 1, 2), group.members)
        val chosen = DoseScheduleConflicts.resolveGroup(rules, 1, group.members)
        assertTrue(DoseScheduleConflicts.unresolved(chosen, "1").isEmpty())
        assertEquals("2", items(medication(*chosen.toTypedArray())).single().amount)
        // The winner outranks the whole group while older rankings outside it stay untouched.
        assertTrue(DoseScheduleConflicts.coincidences(chosen, "1").single().resolved)
    }

    @Test fun `schedules sharing a time without ever meeting the same day stay separate`() {
        // Mon+Tue 3 · Tue+Wed 4 collide on Tuesday; Thu+Fri 5 · Fri+Sat 6 collide on Friday: two debates.
        val groups = DoseScheduleConflicts.coincidences(
            listOf(
                weekly(listOf(1, 2), "3"), weekly(listOf(2, 3), "4"),
                weekly(listOf(4, 5), "5"), weekly(listOf(5, 6), "6")
            ),
            "1"
        )
        assertEquals(2, groups.size)
        assertEquals(listOf(0, 1), groups[0].members)
        assertEquals(monday.plusDays(1), groups[0].date)
        assertEquals(listOf(2, 3), groups[1].members)
        assertEquals(monday.plusDays(4), groups[1].date)
        assertTrue(groups.all { it.times == listOf("08:00") })
        assertTrue(groups.none { it.resolved })
    }

    @Test fun `pairs sharing several clock times stay one accordion`() {
        val rules = listOf(
            daily().copy(times = listOf("08:00", "09:25")),
            daily("2").copy(times = listOf("08:00", "09:25"))
        )
        val group = DoseScheduleConflicts.coincidences(rules, "1").single()
        assertEquals(listOf(0, 1), group.members)
        assertEquals(listOf("08:00", "09:25"), group.times)
        assertEquals(monday, group.date)
    }

    @Test fun `coincidences gather coinciding schedules per time and mark the resolved ones`() {
        val rules = listOf(daily(), daily("2"))
        val group = DoseScheduleConflicts.coincidences(rules, "1").single()
        assertEquals(listOf(0, 1), group.members)
        assertEquals(monday, group.date)
        assertFalse(group.resolved)
        val ranked = DoseScheduleConflicts.prefer(rules, 1)
        val rankedGroup = DoseScheduleConflicts.coincidences(ranked, "1").single()
        assertTrue(rankedGroup.resolved)
        assertTrue(DoseScheduleConflicts.unresolved(ranked, "1").isEmpty())
        // Equal doses are ordinary duplicates, not coincidences demanding a choice.
        assertTrue(DoseScheduleConflicts.coincidences(listOf(daily(), daily()), "1").isEmpty())
    }

    @Test fun `resolveGroup puts the winner above every group member and leaves the rest in place`() {
        val chosen = DoseScheduleConflicts.resolveGroup(
            listOf(daily(), daily("2"), daily("3")), 1, listOf(0, 1, 2)
        )
        assertTrue(chosen[1].priority > chosen[0].priority && chosen[1].priority > chosen[2].priority)
        // A unique top resolves the group even while the lower members still tie.
        assertTrue(DoseScheduleConflicts.coincidences(chosen, "1").single().resolved)
        // Reopening the same group and choosing another member finishes above the rest too.
        val swapped = DoseScheduleConflicts.resolveGroup(chosen, 0, listOf(0, 1, 2))
        assertTrue(DoseScheduleConflicts.coincidences(swapped, "1").single().resolved)
        assertEquals("1", medication(*swapped.toTypedArray()).amountAt(monday, "08:00"))
        // Applying the already-winning choice does not change anything.
        assertEquals(DoseScheduleConflicts.resolveGroup(swapped, 0, listOf(0, 1, 2)), swapped)
    }

    @Test fun `equal amounts and numerically equivalent decimal amounts need no choice`() {
        listOf("1", "1.0", "1,00").forEach { assertTrue(conflicts(daily(), daily(it)).isEmpty()) }
        assertTrue(conflicts(daily("1.50"), daily("1,5")).isEmpty())
    }

    @Test fun `disjoint weekdays month days and date bounds do not overlap`() {
        val weekly = daily().copy(kind = ScheduleKind.WEEKDAYS, weekDays = listOf(1))
        assertTrue(conflicts(weekly, weekly.copy(amount = "2", weekDays = listOf(2))).isEmpty())
        val monthly = daily().copy(kind = ScheduleKind.MONTH_DAYS, monthDays = listOf(1))
        assertTrue(conflicts(monthly, monthly.copy(amount = "2", monthDays = listOf(31))).isEmpty())
        assertTrue(conflicts(daily().copy(until = "2026-10-31"), daily("2").copy(from = "2026-11-01")).isEmpty())
    }

    @Test fun `inclusive bounds overlap on their shared final day`() {
        val overlap = conflicts(daily().copy(until = "2026-10-31"), daily("2").copy(from = "2026-10-31")).single()
        assertEquals(LocalDate.of(2026, 10, 31), overlap.date)
    }

    @Test fun `specific dates years ahead are checked without a short horizon`() {
        val far = monday.plusYears(20)
        val specific = daily("2").copy(kind = ScheduleKind.SPECIFIC_DATES, dates = listOf(far.toString()))
        assertEquals(far, conflicts(daily(), specific).single().date)
        assertTrue(conflicts(daily().copy(until = far.minusDays(1).toString()), specific).isEmpty())
        assertTrue(conflicts(specific, specific.copy(amount = "3", dates = listOf(far.plusDays(1).toString()))).isEmpty())
    }

    @Test fun `monthly and weekly rules find a real calendar intersection`() {
        val monthly = daily().copy(kind = ScheduleKind.MONTH_DAYS, monthDays = listOf(31))
        val weekly = daily("2").copy(kind = ScheduleKind.WEEKDAYS, weekDays = listOf(1))
        val date = conflicts(monthly, weekly).single().date
        assertEquals(31, date.dayOfMonth)
        assertEquals(1, date.dayOfWeek.value)
        assertTrue(conflicts(monthly.copy(until = "2026-10-31"), weekly).isEmpty())
    }

    @Test fun `month ends skip nonexistent dates and leap day still overlaps`() {
        val monthly = daily().copy(kind = ScheduleKind.MONTH_DAYS, monthDays = listOf(29), from = "2028-02-01", until = "2028-02-29")
        val specific = daily("2").copy(kind = ScheduleKind.SPECIFIC_DATES, dates = listOf("2028-02-29"))
        assertEquals(LocalDate.of(2028, 2, 29), conflicts(monthly, specific).single().date)
        assertTrue(conflicts(monthly.copy(monthDays = listOf(31)), specific).isEmpty())
    }

    @Test fun `snoozing and moving a reminder keep the original schedule amount`() {
        val med = medication(daily(), daily("2", "20:00"))
        val key = DoseLogic.slotKey(monday.toString(), med.id, "20:00")
        val moved = items(med, snoozes = mapOf(key to DoseLogic.scheduledMillis(monday, "22:00")), overrides = mapOf(key to "21:00"))
            .single { it.time == "20:00" }
        assertEquals("2", moved.amount)
        assertEquals("21:00", moved.scheduledTime)
        val log = DoseLogic.recordedLog(med, moved.date, moved.time, "taken", moved.scheduledAt, moved.dueMillis)
        assertEquals("2 tablet", log.dose)
        assertEquals("2 tablet", DoseGroups.logs(listOf(moved), "skipped", moved.dueMillis).single().dose)
    }

    @Test fun `saved history keeps the accepted amount after the schedule changes`() {
        val med = medication(daily("2"))
        val at = DoseLogic.scheduledMillis(monday, "08:00")
        val log = DoseLogic.recordedLog(med, monday.toString(), "08:00", "taken", at, at + 1000)
        assertEquals("2 tablet", DoseLogJson.decode(DoseLogJson.encode(log))!!.dose)
        assertEquals("3 tablet", med.copy(schedules = listOf(daily("3"))).doseAt(monday, "08:00"))
        assertEquals("2 tablet", log.dose)
    }

    @Test fun `schedule amounts and priorities survive persistence and draft restoration`() {
        val rules = DoseScheduleConflicts.prefer(listOf(daily(), daily("2")), 1)
        val med = medication(*rules.toTypedArray())
        val restored = MedicationJson.decode(JSONObject(MedicationJson.encode(med).toString()))!!
        assertEquals(med, restored)
        assertEquals("2", items(restored).single().amount)
        val draft = daily("")
        assertEquals(ScheduleIssue.AMOUNT_REQUIRED, draft.validationIssue())
        assertEquals(draft, MedicationJson.decodeSchedule(MedicationJson.encodeSchedule(draft)))
    }

    @Test fun `old structured schedules inherit the default amount and retain legacy overlaps`() {
        val json = MedicationJson.encode(medication(daily(), daily()))
        val rules = json.getJSONArray("schedules")
        repeat(rules.length()) { rules.getJSONObject(it).apply { remove("amount"); remove("priority") } }
        val med = MedicationJson.decode(json)!!
        assertTrue(med.schedules.all { it.amount == null && it.priority == 0 })
        assertEquals("1 tablet", items(med).single().dose)
        assertTrue(DoseScheduleConflicts.unresolved(med.schedules, med.amount).isEmpty())
    }
}
