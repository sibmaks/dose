package io.github.sibmaks.app.dose.data

enum class MedicationStep(val title: String) {
    NAME("Name & color"),
    FORM("Medication form"),
    OTHER_FORM("Describe the form"),
    DOSAGE("Amount & unit"),
    SCHEDULE("Due dates & times"),
    OVERLAPS("Schedule overlaps"),
    DETAILS("Final details");
}

enum class MedicationAmountIssue { REQUIRED, NON_POSITIVE }

object MedicationEditorFlow {
    /** Free-form dosage descriptions stay valid; recognizable numeric amounts must be positive. */
    fun amountIssue(amount: String): MedicationAmountIssue? = when {
        amount.isBlank() -> MedicationAmountIssue.REQUIRED
        (amount.trim().replace(',', '.').toDoubleOrNull() ?: 1.0) <= 0.0 -> MedicationAmountIssue.NON_POSITIVE
        else -> null
    }

    /** The overlaps screen exists only when coinciding schedules with different dosages are detected. */
    fun steps(
        form: MedicationForm,
        hasOverlaps: Boolean = false
    ): List<MedicationStep> = MedicationStep.entries.filter {
        (it != MedicationStep.OTHER_FORM || form == MedicationForm.OTHER) &&
                (it != MedicationStep.OVERLAPS || hasOverlaps)
    }

    fun next(
        step: MedicationStep,
        form: MedicationForm,
        hasOverlaps: Boolean = false
    ): MedicationStep =
        steps(form, hasOverlaps).let { it[(it.indexOf(step) + 1).coerceAtMost(it.lastIndex)] }

    fun previous(
        step: MedicationStep,
        form: MedicationForm,
        hasOverlaps: Boolean = false
    ): MedicationStep =
        steps(form, hasOverlaps).let { it[(it.indexOf(step) - 1).coerceAtLeast(0)] }
}
