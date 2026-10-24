package io.github.sibmaks.app.dose.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** A single persisted instant applies equally to notification feed and overlay delivery. */
object NotificationPauseWindow {
    fun active(
        until: Long,
        now: Long
    ): Boolean = until > now

    fun selectedUntil(
        date: String,
        time: String,
        now: ZonedDateTime
    ): Long? = runCatching {
        require(DoseLogic.isDate(date) && DoseLogic.isTime(time))
        LocalDate.parse(date)
            .atTime(LocalTime.parse(time))
            .atZone(now.zone)
            .toInstant()
            .toEpochMilli()
            .takeIf {
                it > now.toInstant()
                    .toEpochMilli()
            }
    }.getOrNull()
}
