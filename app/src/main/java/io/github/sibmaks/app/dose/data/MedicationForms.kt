package io.github.sibmaks.app.dose.data

import java.text.Collator
import java.util.Locale

object MedicationForms {
    /** Use the chosen language's collation rules, including its alphabet and accents. */
    fun sorted(locale: Locale, label: (MedicationForm) -> String): List<MedicationForm> {
        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        return MedicationForm.entries.sortedWith { first, second ->
            collator.compare(label(first), label(second)).takeIf { it != 0 } ?: first.name.compareTo(second.name)
        }
    }
}
