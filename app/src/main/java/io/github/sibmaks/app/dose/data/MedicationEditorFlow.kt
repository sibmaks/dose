package io.github.sibmaks.app.dose.data

enum class MedicationStep(val title: String) {
    NAME("Name & color"), FORM("Medication form"), OTHER_FORM("Describe the form"),
    DOSAGE("Amount & unit"), SCHEDULE("Due dates & times"), DETAILS("Final details");
}

object MedicationEditorFlow {
    fun steps(form: MedicationForm): List<MedicationStep> = MedicationStep.entries.filter { it != MedicationStep.OTHER_FORM || form == MedicationForm.OTHER }
    fun next(step: MedicationStep, form: MedicationForm): MedicationStep = steps(form).let { it[(it.indexOf(step) + 1).coerceAtMost(it.lastIndex)] }
    fun previous(step: MedicationStep, form: MedicationForm): MedicationStep = steps(form).let { it[(it.indexOf(step) - 1).coerceAtLeast(0)] }
}
