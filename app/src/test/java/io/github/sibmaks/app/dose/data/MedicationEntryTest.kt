package io.github.sibmaks.app.dose.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MedicationEntryTest {
    private val dictionary = MedicationDictionaryTestAsset.load()
    private fun medication(name: String, form: MedicationForm = MedicationForm.PILL, custom: String = "") =
        Medication("id", name, "1", "unit", listOf(DoseSchedule(createdOn = "2026-10-15")), form = form, customForm = custom)

    @Test fun dictionaryShowsOneNameWithAnEditableDefaultForm() {
        val results = dictionary.suggestions("para")
        assertEquals(1, results.size)
        assertEquals(MedicationForm.PILL, results.single().form)
        assertTrue(results.all { it.name == "Paracetamol" })
    }
    @Test fun calciumDoesNotMatchInsideVitaminDAlias() {
        val results = dictionary.suggestions("Кальций", language = "ru")
        assertEquals(listOf("Кальций"), results.map { it.name })
        assertEquals(listOf("Кальций"), dictionary.suggestions("кальц", language = "ru").map { it.name })
    }
    @Test fun thyroidAndAlfacalcidolVariantsRemainSelectableAndExactMatchesComeFirst() {
        assertEquals("Тероксин", dictionary.suggestions("Тероксин", language = "ru").first().name)
        assertEquals("Тироксин", dictionary.suggestions("Тироксин", language = "ru").first().name)
        assertEquals(listOf("Альфакальцидол", "Альфокальцидол"), dictionary.suggestions("Альф", language = "ru").map { it.name })
        assertEquals("Альфокальцидол", dictionary.suggestions("Альфокальцидол", language = "ru").first().name)
        assertTrue(dictionary.suggestions("Альф", language = "ru").all { it.form == MedicationForm.CAPSULE && it.code == "alfacalcidol" })
    }
    @Test fun selectedAlternateNamePersistsWithItsOwnSpellingAndSuggestedForm() {
        val selected = dictionary.suggestions("Альф", language = "ru").single { it.name == "Альфокальцидол" }
        val med = DoseStore.newMedication(selected.name, "1", DoseUnits.defaultFor(selected.form),
            listOf(DoseSchedule()), "", "violet", selected.form)
        val restored = MedicationJson.decode(MedicationJson.encode(med))!!
        assertEquals("Альфокальцидол", restored.name)
        assertEquals(MedicationForm.CAPSULE, restored.form)
        assertEquals("capsule", restored.quantity)
    }
    @Test fun savedVariantsShareOneNameSuggestionAndKeepTheirForm() {
        val results = dictionary.suggestions("para", listOf(medication("Paracetamol", MedicationForm.LIQUID), medication("Paracetamol")))
        assertEquals(1, results.size)
        assertTrue(results.single().saved)
        assertEquals(MedicationForm.LIQUID, results.single().form)
    }
    @Test fun russianSearchUsesRussianNamesAndIgnoresCase() {
        val results = dictionary.suggestions("  ПАРА  ", language = "ru")
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.name == "Парацетамол" })
    }
    @Test fun alternateNameFindsTheCatalogEntry() {
        assertEquals("Paracetamol", dictionary.suggestions("acetaminophen").first().name)
    }
    @Test fun ownNamesComeFirstAndDuplicateVariantIsRemoved() {
        val results = dictionary.suggestions("para", listOf(medication("Paracetamol")))
        assertTrue(results.first().saved)
        assertEquals(1, results.count { it.name == "Paracetamol" && it.form == MedicationForm.PILL })
    }
    @Test fun customSavedNameAndFormRemainAvailable() {
        val results = dictionary.suggestions("My pat", listOf(medication("My patch", MedicationForm.OTHER, "Patch")))
        assertEquals("My patch", results.single().name)
        assertEquals("Patch", results.single().customForm)
    }
    @Test fun suggestionsAreLimitedAndShortOrUnknownInputIsAllowedWithoutSuggestions() {
        assertTrue(dictionary.suggestions("a").isEmpty())
        assertTrue(dictionary.suggestions("Not in the catalog").isEmpty())
        assertTrue(dictionary.suggestions("in", limit = 2).size <= 2)
        assertTrue(dictionary.suggestions("para", limit = 0).isEmpty())
    }
    @Test fun prefixMatchesComeBeforeNamesContainingTheQuery() {
        assertEquals("Insulin", dictionary.suggestions("ins").first().name)
    }
    @Test fun suggestedInhalerUsesBuiltInForm() {
        val inhaler = dictionary.suggestions("salbutamol").single()
        assertEquals(MedicationForm.INHALER, inhaler.form)
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
