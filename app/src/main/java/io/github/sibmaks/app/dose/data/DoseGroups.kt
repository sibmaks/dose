package io.github.sibmaks.app.dose.data

/** A group contains only pending doses sharing the displayed reminder instant. */
object DoseGroups {
    data class Member(val key: String, val scheduledAt: Long, val dueMillis: Long)

    fun together(items: List<DoseItem>, anchor: DoseItem?): List<DoseItem> =
        if (anchor == null || anchor.log != null) emptyList() else items.filter {
            it.log == null && it.date == anchor.date && it.dueMillis == anchor.dueMillis
        }.distinctBy { it.id }

    fun snapshot(items: List<DoseItem>): List<Member> = items.distinctBy { it.id }.map {
        Member(it.id, it.scheduledAt, it.dueMillis)
    }

    /** Revalidate the displayed snapshot; never overwrite a resolved or rescheduled dose. */
    fun eligible(current: List<DoseItem>, members: List<Member>): List<DoseItem> {
        val expected = members.associateBy { it.key }
        return current.filter { item ->
            val member = expected[item.id]
            item.log == null && member != null && member.scheduledAt == item.scheduledAt && member.dueMillis == item.dueMillis
        }.distinctBy { it.id }
    }

    fun logs(items: List<DoseItem>, status: String, now: Long): List<DoseLog> = items.map {
        DoseLogic.recordedLog(it.medication, it.date, it.time, status, it.scheduledAt, now)
    }
}
