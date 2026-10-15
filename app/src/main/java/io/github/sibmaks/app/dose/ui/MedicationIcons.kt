package io.github.sibmaks.app.dose.ui

import io.github.sibmaks.app.dose.R
import io.github.sibmaks.app.dose.data.MedicationForm

/** Native vector drawables use the same paths as the SVG sources in design-reference/medication-forms. */
fun MedicationForm.iconResource(): Int = when (this) {
    MedicationForm.PILL -> R.drawable.ic_form_pill
    MedicationForm.CAPSULE -> R.drawable.ic_form_capsule
    MedicationForm.LIQUID -> R.drawable.ic_form_liquid
    MedicationForm.SPRAY -> R.drawable.ic_form_spray
    MedicationForm.DROPS -> R.drawable.ic_form_drops
    MedicationForm.INJECTION -> R.drawable.ic_form_injection
    MedicationForm.CREAM -> R.drawable.ic_form_cream
    MedicationForm.OTHER -> R.drawable.ic_form_other
}
