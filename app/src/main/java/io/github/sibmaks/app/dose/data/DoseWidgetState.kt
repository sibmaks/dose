package io.github.sibmaks.app.dose.data

import java.time.ZonedDateTime

/** A pending dose stays visible when due; tomorrow's medication never appears as today's. */
object DoseWidgetState {
    fun next(items: List<DoseItem>): DoseItem? = items.filter { it.log == null }.minByOrNull { it.dueMillis }
    fun nextGroup(items: List<DoseItem>): List<DoseItem> = DoseGroups.together(items, next(items))
    fun nextRefresh(items: List<DoseItem>, now: ZonedDateTime): Long =
        (items.filter { it.log == null }.map { it.dueMillis }.filter { it > now.toInstant().toEpochMilli() } +
            now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()).min()
}
