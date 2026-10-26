package io.github.sibmaks.app.dose.data

import java.time.ZonedDateTime

/** A pending dose stays visible when due; tomorrow's medication never appears as today's. */
object DoseWidgetState {
    fun allTaken(items: List<DoseItem>): Boolean = items.isNotEmpty() && items.all { it.log?.status == "taken" }

    fun pending(items: List<DoseItem>): List<DoseItem> = items.filter { it.log == null }
        .sortedBy { it.dueMillis }

    fun next(items: List<DoseItem>): DoseItem? = items.filter { it.log == null }
        .minByOrNull { it.dueMillis }

    fun nextGroup(items: List<DoseItem>): List<DoseItem> = DoseGroups.together(items, next(items))

    const val EXPANDED_HEIGHT = 232
    const val ROW_HEIGHT = 136

    /** Reserve the header, padding and footer; never clip an interactive card. */
    fun visibleRows(
        height: Float,
        count: Int
    ): Int =
        if (height < EXPANDED_HEIGHT || count <= 0) 0 else ((height - 96) / ROW_HEIGHT).toInt()
            .coerceIn(1, count)

    fun nextRefresh(
        items: List<DoseItem>,
        now: ZonedDateTime
    ): Long =
        (items.filter { it.log == null }
            .map { it.dueMillis }
            .filter {
                it > now.toInstant()
                    .toEpochMilli()
            } +
                now.toLocalDate()
                    .plusDays(1)
                    .atStartOfDay(now.zone)
                    .toInstant()
                    .toEpochMilli()).min()
}
