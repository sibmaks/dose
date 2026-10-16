package io.github.sibmaks.app.dose.data

/** A single persisted instant applies equally to notification feed and overlay delivery. */
object NotificationPauseWindow {
    fun active(until: Long, now: Long): Boolean = until > now
    fun after(amount: String, days: Boolean, now: Long): Long? {
        val count = amount.toLongOrNull()?.takeIf { it > 0 } ?: return null
        return runCatching { Math.addExact(now, Math.multiplyExact(count, if (days) 86_400_000L else 3_600_000L)) }.getOrNull()
    }
}
