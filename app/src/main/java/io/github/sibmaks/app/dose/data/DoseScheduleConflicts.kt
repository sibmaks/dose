package io.github.sibmaks.app.dose.data

import java.time.LocalDate

/** Compare calendar occurrences, not just matching clock times or a short preview window. */
object DoseScheduleConflicts {
    /**
     * One accordion per collision cluster: schedules joined by real calendar collisions, however
     * many clock times bind them. Schedules that merely share a time without ever meeting stay apart.
     */
    data class Coincidence(
        val members: List<Int>,
        val times: List<String>,
        val date: LocalDate,
        val resolved: Boolean
    ) {
        /** Clusters never share members, so the membership alone identifies the debate. */
        val key: String get() = members.joinToString("-")
    }

    fun unresolved(
        schedules: List<DoseSchedule>,
        defaultAmount: String
    ): List<Coincidence> = coincidences(schedules, defaultAmount)
        .filter { !it.resolved }

    /** Coinciding clusters; the ranking decides which member supplies the dose on overlap. */
    fun coincidences(
        schedules: List<DoseSchedule>,
        defaultAmount: String
    ): List<Coincidence> {
        val collisions = buildList {
            schedules.forEachIndexed { first, a ->
                for (second in first + 1 until schedules.size) {
                    val b = schedules[second]
                    val shared = a.times.intersect(b.times.toSet())
                        .sorted()
                    if (shared.isEmpty()) continue
                    if (sameAmount(
                            a.amount ?: defaultAmount,
                            b.amount ?: defaultAmount
                        )
                    ) continue
                    val date = firstDate(a, b) ?: continue
                    add(Collision(first, second, date, shared))
                }
            }
        }
        // Schedules brushing against a common partner belong to one debate (A–B and A–C once each).
        val parent = IntArray(schedules.size) { it }
        fun root(index: Int): Int {
            var node = index
            while (parent[node] != node) node = parent[node]
            return node
        }

        collisions.forEach { collision ->
            parent[root(collision.first)] = root(collision.second)
        }
        return collisions.groupBy { root(it.first) }
            .values
            .map { edges ->
                val members = edges.flatMap { listOf(it.first, it.second) }
                    .distinct()
                    .sorted()
                Coincidence(
                    members = members,
                    times = edges.flatMap { it.shared }
                        .distinct()
                        .sorted(),
                    date = edges.minOf { it.date },
                    resolved = members.count { schedule ->
                        schedules[schedule].priority == members.maxOf { schedules[it].priority }
                    } == 1
                )
            }
            .sortedBy { it.date }
    }

    private data class Collision(
        val first: Int,
        val second: Int,
        val date: LocalDate,
        val shared: List<String>
    )

    /** Rank one choice above all remaining unranked schedules; previous choices stay higher. */
    fun prefer(
        schedules: List<DoseSchedule>,
        index: Int
    ): List<DoseSchedule> {
        require(schedules[index].priority == 0)
        val rank =
            (schedules.filter { it.priority > 0 }
                .minOfOrNull { it.priority } ?: (schedules.size + 1)) - 1
        require(rank > 0)
        return schedules.mapIndexed { i, rule -> if (i == index) rule.copy(priority = rank) else rule }
    }

    /**
     * Put the chosen schedule above its partner and leave every other schedule in place.
     * Equal unranked pairs use the normal ranking; already ranked pairs just swap positions.
     */
    fun resolve(
        schedules: List<DoseSchedule>,
        winner: Int,
        loser: Int
    ): List<DoseSchedule> {
        val above = schedules[winner].priority
        val below = schedules[loser].priority
        if (above > below) return schedules
        if (above == below) {
            if (above == 0) return prefer(schedules, winner)
            return schedules.mapIndexed { i, rule ->
                when (i) {
                    winner -> rule.copy(priority = below)
                    loser -> rule.copy(priority = (below - 1).coerceAtLeast(1))
                    else -> rule
                }
            }
        }
        return schedules.mapIndexed { i, rule ->
            when (i) {
                winner -> rule.copy(priority = below)
                loser -> rule.copy(priority = above)
                else -> rule
            }
        }
    }

    /** Choosing one member finishes a cluster's debate: the winner outranks every other member. */
    fun resolveGroup(
        schedules: List<DoseSchedule>,
        winner: Int,
        members: List<Int>
    ): List<DoseSchedule> {
        var result = schedules
        members.forEach { member ->
            if (member != winner) result = resolve(result, winner, member)
        }
        return result
    }

    private fun sameAmount(
        a: String,
        b: String
    ): Boolean {
        fun number(value: String) = value.trim()
            .replace(',', '.')
            .toBigDecimalOrNull()

        val first = number(a)
        val second = number(b)
        return if (first != null && second != null) first.compareTo(second) == 0 else a.trim() == b.trim()
    }

    private fun firstDate(
        a: DoseSchedule,
        b: DoseSchedule
    ): LocalDate? {
        if (a.validationIssue() != null || b.validationIssue() != null) return null
        if (a.kind == ScheduleKind.WEEKDAYS && b.kind == ScheduleKind.WEEKDAYS && a.weekDays.intersect(b.weekDays.toSet())
                .isEmpty()
        ) return null
        if (a.kind == ScheduleKind.MONTH_DAYS && b.kind == ScheduleKind.MONTH_DAYS && a.monthDays.intersect(b.monthDays.toSet())
                .isEmpty()
        ) return null
        if (a.kind == ScheduleKind.SPECIFIC_DATES || b.kind == ScheduleKind.SPECIFIC_DATES) {
            val finite = if (a.kind == ScheduleKind.SPECIFIC_DATES) a else b
            return finite.dates.sorted()
                .map(LocalDate::parse)
                .firstOrNull { a.includes(it) && b.includes(it) }
        }
        val start = maxOf(LocalDate.parse(a.from ?: a.createdOn), LocalDate.parse(b.from ?: b.createdOn))
        // Gregorian month/day and weekday combinations repeat exactly every 400 years.
        val cycleEnd = runCatching { start.plusYears(400) }.getOrDefault(LocalDate.MAX)
        val end = listOfNotNull(a.until?.let(LocalDate::parse), b.until?.let(LocalDate::parse), cycleEnd).min()
        var date = start
        while (date <= end) {
            val nextA = a.nextDate(date) ?: return null
            val nextB = b.nextDate(date) ?: return null
            if (nextA > end || nextB > end) return null
            if (nextA == nextB) return nextA
            date = maxOf(nextA, nextB)
        }
        return null
    }
}
