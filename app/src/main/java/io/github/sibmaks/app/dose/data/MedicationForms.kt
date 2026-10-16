package io.github.sibmaks.app.dose.data

import java.text.Collator
import java.util.Locale

object MedicationForms {
    /** Sort named forms with the chosen language's collation rules, keeping Other last. */
    fun sorted(locale: Locale, label: (MedicationForm) -> String): List<MedicationForm> {
        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        return MedicationForm.entries.filter { it != MedicationForm.OTHER }.sortedWith { first, second ->
            collator.compare(label(first), label(second)).takeIf { it != 0 } ?: first.name.compareTo(second.name)
        } + MedicationForm.OTHER
    }
}
