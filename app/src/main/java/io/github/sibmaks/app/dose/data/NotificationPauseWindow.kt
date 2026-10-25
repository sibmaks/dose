package io.github.sibmaks.app.dose.data

import org.json.JSONObject
import java.time.*

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

/** One managed pause interval; the status is derived, so a completed window never needs rewriting. */
data class PauseWindow(
    val id: Long,
    val startAt: Long,
    val endAt: Long,
    val canceled: Boolean = false,
)

/** Pauses overlap freely: notifications stay silenced while at least one window is running. */
object PauseWindows {
    fun active(
        windows: List<PauseWindow>,
        now: Long
    ): List<PauseWindow> =
        windows.filter { !it.canceled && it.startAt <= now && now < it.endAt }
            .sortedByDescending { it.endAt }

    fun scheduled(
        windows: List<PauseWindow>,
        now: Long
    ): List<PauseWindow> =
        windows.filter { !it.canceled && it.startAt > now }
            .sortedBy { it.startAt }

    /** Only scheduled and running windows are kept; completed and canceled ones are dropped. */
    fun live(
        windows: List<PauseWindow>,
        now: Long
    ): List<PauseWindow> =
        windows.filter { !it.canceled && it.endAt > now }

    fun isPaused(
        windows: List<PauseWindow>,
        now: Long
    ): Boolean =
        windows.any { !it.canceled && it.startAt <= now && now < it.endAt }

    /** Overlaps extend the silence: the effective resume is the latest running end. */
    fun resumeAt(
        windows: List<PauseWindow>,
        now: Long
    ): Long? =
        active(windows, now).maxOfOrNull { it.endAt }

    /** The nearest moment that changes delivery: a window's start or its end. */
    fun nextTransition(
        windows: List<PauseWindow>,
        now: Long
    ): Long? =
        windows.filter { !it.canceled }
            .flatMap { listOf(it.startAt, it.endAt) }
            .filter { it > now }
            .minOrNull()

    fun overlaps(
        windows: List<PauseWindow>,
        startAt: Long,
        endAt: Long
    ): Boolean = windows.any { !it.canceled && startAt < it.endAt && it.startAt < endAt }

    /** "Until morning" is the next 07:00 strictly after the start, so it is never an unexplained hour. */
    fun morningAfter(
        startAt: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val start =
            Instant.ofEpochMilli(startAt)
                .atZone(zone)
        var morning = start.toLocalDate()
            .atTime(LocalTime.of(7, 0))
            .atZone(zone)
        if (!morning.toInstant()
                .toEpochMilli()
                .let { it > startAt }
        ) {
            morning = morning.plusDays(1)
        }
        return morning.toInstant()
            .toEpochMilli()
    }
}

object PauseWindowJson {
    fun decode(obj: JSONObject): PauseWindow? = PauseWindow(
        obj.optLong("id"), obj.optLong("start"), obj.optLong("end"),
        obj.optBoolean("canceled", false),
    ).takeIf { it.id != 0L && it.startAt > 0 && it.endAt > it.startAt }

    fun encode(window: PauseWindow): JSONObject = JSONObject().apply {
        put("id", window.id)
        put("start", window.startAt)
        put("end", window.endAt)
        if (window.canceled) put("canceled", true)
    }
}
