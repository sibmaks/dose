package io.github.sibmaks.app.dose.data

import java.util.Locale

/** An offline starter catalog; variants stay separate so choosing a name never guesses a dose. */
data class MedicationSuggestion(val name: String, val form: MedicationForm, val customForm: String = "", val saved: Boolean = false) {
    val formLabel: String get() = customForm.takeIf { form == MedicationForm.OTHER && it.isNotBlank() } ?: form.label
}

object MedicationDictionary {
    private data class Entry(val english: String, val russian: String, val forms: List<MedicationForm>, val aliases: List<String> = emptyList(), val customForm: String = "")
    // Name/form references, scope, and attribution are recorded in docs/medication-dictionary.md.
    private val entries = listOf(
        Entry("Paracetamol", "Парацетамол", listOf(MedicationForm.PILL, MedicationForm.CAPSULE, MedicationForm.LIQUID), listOf("acetaminophen")),
        Entry("Ibuprofen", "Ибупрофен", listOf(MedicationForm.PILL, MedicationForm.CAPSULE, MedicationForm.LIQUID)),
        Entry("Amoxicillin", "Амоксициллин", listOf(MedicationForm.CAPSULE, MedicationForm.LIQUID)),
        Entry("Cetirizine", "Цетиризин", listOf(MedicationForm.PILL, MedicationForm.LIQUID)),
        Entry("Omeprazole", "Омепразол", listOf(MedicationForm.CAPSULE, MedicationForm.PILL, MedicationForm.LIQUID)),
        Entry("Hydrocortisone", "Гидрокортизон", listOf(MedicationForm.CREAM)),
        Entry("Vitamin D3", "Витамин D3", listOf(MedicationForm.PILL, MedicationForm.CAPSULE, MedicationForm.DROPS, MedicationForm.LIQUID), listOf("colecalciferol", "cholecalciferol", "колекальциферол", "холекальциферол")),
        Entry("Loratadine", "Лоратадин", listOf(MedicationForm.PILL, MedicationForm.LIQUID)),
        Entry("Metformin", "Метформин", listOf(MedicationForm.PILL, MedicationForm.LIQUID)),
        Entry("Levothyroxine", "Левотироксин", listOf(MedicationForm.PILL, MedicationForm.LIQUID)),
        Entry("Insulin", "Инсулин", listOf(MedicationForm.INJECTION)),
        Entry("Salbutamol", "Сальбутамол", listOf(MedicationForm.OTHER), listOf("albuterol"), "Inhaler"),
    )
    private fun normalize(value: String) = value.trim().lowercase(Locale.ROOT).replace('ё', 'е')
    fun suggestions(query: String, saved: List<Medication> = emptyList(), limit: Int = 5): List<MedicationSuggestion> {
        val text = normalize(query)
        if (text.length < 2 || limit <= 0) return emptyList()
        val cyrillic = text.any { it in '\u0400'..'\u04ff' }
        val own = saved.filter { normalize(it.name).contains(text) }.sortedBy { if (normalize(it.name).startsWith(text)) 0 else 1 }
            .map { MedicationSuggestion(it.name, it.form, it.customForm, saved = true) }
        val catalog = entries.filter { entry -> (listOf(entry.english, entry.russian) + entry.aliases).any { normalize(it).contains(text) } }
            .sortedBy { entry -> if ((listOf(entry.english, entry.russian) + entry.aliases).any { normalize(it).startsWith(text) }) 0 else 1 }
            .flatMap { entry -> entry.forms.map { MedicationSuggestion(if (cyrillic) entry.russian else entry.english, it, entry.customForm) } }
        return (own + catalog).distinctBy { Triple(normalize(it.name), it.form, normalize(it.customForm)) }.take(limit)
    }
}

enum class MedicationStep(val title: String) {
    NAME("Name & color"), FORM("Medication form"), OTHER_FORM("Describe the form"),
    DOSAGE("Amount & unit"), SCHEDULE("Due dates & times"), DETAILS("Final details");
}

object MedicationEditorFlow {
    fun steps(form: MedicationForm): List<MedicationStep> = MedicationStep.entries.filter { it != MedicationStep.OTHER_FORM || form == MedicationForm.OTHER }
    fun next(step: MedicationStep, form: MedicationForm): MedicationStep = steps(form).let { it[(it.indexOf(step) + 1).coerceAtMost(it.lastIndex)] }
    fun previous(step: MedicationStep, form: MedicationForm): MedicationStep = steps(form).let { it[(it.indexOf(step) - 1).coerceAtLeast(0)] }
}
