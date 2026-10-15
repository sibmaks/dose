package io.github.sibmaks.app.dose.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MedicationEntryTest {
    private fun medication(name: String, form: MedicationForm = MedicationForm.PILL, custom: String = "") =
        Medication("id", name, "1", "unit", listOf(DoseSchedule(createdOn = "2026-10-15")), form = form, customForm = custom)

    @Test fun dictionaryShowsOneNameWithAnEditableDefaultForm() {
        val results = MedicationDictionary.suggestions("para")
        assertEquals(1, results.size)
        assertEquals(MedicationForm.PILL, results.single().form)
        assertTrue(results.all { it.name == "Paracetamol" })
    }
    @Test fun calciumDoesNotMatchInsideVitaminDAlias() {
        val results = MedicationDictionary.suggestions("Кальций")
        assertEquals(listOf("Кальций"), results.map { it.name })
        assertEquals(listOf("Кальций"), MedicationDictionary.suggestions("кальц").map { it.name })
    }
    @Test fun thyroidAndAlfacalcidolSpellingVariantsShowExplicitCanonicalNames() {
        assertEquals("Левотироксин", MedicationDictionary.suggestions("Тероксин").single().name)
        assertEquals("Левотироксин", MedicationDictionary.suggestions("Тироксин").single().name)
        assertEquals("Альфакальцидол", MedicationDictionary.suggestions("Альфокальцидол").single().name)
        assertEquals(MedicationForm.CAPSULE, MedicationDictionary.suggestions("Альфакальцидол").single().form)
    }
    @Test fun savedVariantsShareOneNameSuggestionAndKeepTheirForm() {
        val results = MedicationDictionary.suggestions("para", listOf(medication("Paracetamol", MedicationForm.LIQUID), medication("Paracetamol")))
        assertEquals(1, results.size)
        assertTrue(results.single().saved)
        assertEquals(MedicationForm.LIQUID, results.single().form)
    }
    @Test fun russianSearchUsesRussianNamesAndIgnoresCase() {
        val results = MedicationDictionary.suggestions("  ПАРА  ")
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.name == "Парацетамол" })
    }
    @Test fun alternateNameFindsTheCatalogEntry() {
        assertEquals("Paracetamol", MedicationDictionary.suggestions("acetaminophen").first().name)
    }
    @Test fun ownNamesComeFirstAndDuplicateVariantIsRemoved() {
        val results = MedicationDictionary.suggestions("para", listOf(medication("Paracetamol")))
        assertTrue(results.first().saved)
        assertEquals(1, results.count { it.name == "Paracetamol" && it.form == MedicationForm.PILL })
    }
    @Test fun customSavedNameAndFormRemainAvailable() {
        val results = MedicationDictionary.suggestions("My pat", listOf(medication("My patch", MedicationForm.OTHER, "Patch")))
        assertEquals("My patch", results.single().name)
        assertEquals("Patch", results.single().customForm)
    }
    @Test fun suggestionsAreLimitedAndShortOrUnknownInputIsAllowedWithoutSuggestions() {
        assertTrue(MedicationDictionary.suggestions("a").isEmpty())
        assertTrue(MedicationDictionary.suggestions("Not in the catalog").isEmpty())
        assertTrue(MedicationDictionary.suggestions("in", limit = 2).size <= 2)
        assertTrue(MedicationDictionary.suggestions("para", limit = 0).isEmpty())
    }
    @Test fun prefixMatchesComeBeforeNamesContainingTheQuery() {
        assertEquals("Insulin", MedicationDictionary.suggestions("ins").first().name)
    }
    @Test fun suggestedInhalerUsesEditableOtherForm() {
        val inhaler = MedicationDictionary.suggestions("salbutamol").single()
        assertEquals(MedicationForm.OTHER, inhaler.form)
        assertEquals("Inhaler", inhaler.formLabel)
    }
    @Test fun normalFlowHasOnlyTheFiveRequestedSteps() {
        assertEquals(listOf(MedicationStep.NAME, MedicationStep.FORM, MedicationStep.DOSAGE, MedicationStep.SCHEDULE, MedicationStep.DETAILS), MedicationEditorFlow.steps(MedicationForm.PILL))
        assertEquals(MedicationStep.DOSAGE, MedicationEditorFlow.next(MedicationStep.FORM, MedicationForm.PILL))
        assertEquals(MedicationStep.FORM, MedicationEditorFlow.previous(MedicationStep.DOSAGE, MedicationForm.PILL))
    }
    @Test fun otherFormAddsOnlyItsOptionalTextStep() {
        assertEquals(MedicationStep.OTHER_FORM, MedicationEditorFlow.next(MedicationStep.FORM, MedicationForm.OTHER))
        assertEquals(MedicationStep.DOSAGE, MedicationEditorFlow.next(MedicationStep.OTHER_FORM, MedicationForm.OTHER))
        assertEquals(MedicationStep.OTHER_FORM, MedicationEditorFlow.previous(MedicationStep.DOSAGE, MedicationForm.OTHER))
        assertEquals(6, MedicationEditorFlow.steps(MedicationForm.OTHER).size)
    }
    @Test fun customFormSurvivesPersistenceAndAppearsInDoseDescription() {
        val med = medication("Test", MedicationForm.OTHER, "Patch")
        val restored = MedicationJson.decode(JSONObject(MedicationJson.encode(med).toString()))!!
        assertEquals(med, restored)
        assertEquals("Patch", restored.formLabel)
        assertEquals("1 unit · Patch", restored.doseDescription)
        assertEquals("1 unit", restored.dose)
    }
    @Test fun emptyCustomFormUsesOtherAndOrdinaryFormsIgnoreStaleCustomText() {
        assertEquals("Other", medication("Test", MedicationForm.OTHER).formLabel)
        assertEquals("Pill", medication("Test", custom = "Patch").formLabel)
        assertEquals("1 unit", medication("Test", custom = "Patch").doseDescription)
    }
    @Test fun oldMedicationsDoNotRequireNewFormField() {
        val json = MedicationJson.encode(medication("Test"))
        json.remove("customForm")
        val restored = MedicationJson.decode(json)!!
        assertEquals("", restored.customForm)
        assertEquals(MedicationForm.PILL, restored.form)
    }
    @Test fun incompleteScheduleDraftCanSurviveRotationBeforeValidation() {
        val draft = DoseSchedule(kind = ScheduleKind.WEEKDAYS, weekDays = emptyList(), times = listOf("08:00", "20:00"), from = "2026-11-01", createdOn = "2026-10-15")
        assertNotNull(draft.validationError())
        assertEquals(draft, MedicationJson.decodeSchedule(JSONObject(MedicationJson.encodeSchedule(draft).toString())))
    }
}
