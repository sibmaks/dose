package io.github.sibmaks.app.dose.data

import io.github.sibmaks.app.dose.localization.AppLanguages
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MedicationDictionaryJsonTest {
    private fun entry() = JSONObject("""{
        "localizations":{"en":["Test medicine"],"ru":["Тест","Другой тест"]},
        "forms":["CAPSULE","LIQUID"],"aliases":["shortcut"]
    }""")
    private fun dictionary(value: JSONObject = entry(), code: String = "test_medicine") =
        MedicationDictionary.fromJson(JSONObject().put(code, value).toString())
    private fun medication(name: String) = Medication("saved", name, "1", "unit", listOf(DoseSchedule()), form = MedicationForm.LIQUID)
    private fun rejected(json: String) {
        try {
            MedicationDictionary.fromJson(json)
            fail("Invalid dictionary must be rejected")
        } catch (_: IllegalArgumentException) { }
    }
    private fun rejected(value: JSONObject, code: String = "test_medicine") = rejected(JSONObject().put(code, value).toString())

    @Test fun `bundled asset preserves starter codes while accepting catalog extensions`() {
        val catalog = MedicationDictionaryTestAsset.load()
        assertTrue(catalog.entries.map { it.code }.toSet().containsAll(setOf("paracetamol", "ibuprofen", "amoxicillin", "cetirizine", "omeprazole", "hydrocortisone", "vitamin_d3", "calcium", "alfacalcidol", "loratadine", "metformin", "levothyroxine", "insulin", "salbutamol")))
        assertTrue(catalog.entries.all { it.localizations.keys.all { language -> AppLanguages.supported.any { it.tag == language } } })
        assertEquals(MedicationForm.PILL, catalog.findByCode("paracetamol")!!.forms.first())
        assertTrue(catalog.findByCode("paracetamol")!!.forms.containsAll(listOf(MedicationForm.PILL, MedicationForm.CAPSULE, MedicationForm.LIQUID)))
        assertEquals(listOf(MedicationForm.INHALER), catalog.entries.single { it.code == "salbutamol" }.forms)
        assertEquals("levothyroxine", catalog.suggestions("Левотероксин", language = "ru").first().code)
    }
    @Test fun `each localization exposes every name variant under the same stable code`() {
        val catalog = dictionary()
        assertEquals(listOf("Тест", "Другой тест"), catalog.suggestions("Тест", language = "ru").map { it.name })
        assertEquals(listOf("Другой тест", "Тест"), catalog.suggestions("Другой тест", language = "ru").map { it.name })
        val aliases = catalog.suggestions("shortcut", language = "ru")
        assertEquals(listOf("Тест", "Другой тест"), aliases.map { it.name })
        assertTrue(aliases.all { it.code == "test_medicine" && it.form == MedicationForm.CAPSULE })
    }
    @Test fun `every app language can select its primary JSON name`() {
        val value = entry()
        AppLanguages.supported.forEach { language ->
            value.getJSONObject("localizations").put(language.tag, JSONArray().put("Display-${language.tag}").put("Alternative-${language.tag}"))
        }
        val catalog = dictionary(value)
        AppLanguages.supported.forEach { language ->
            val results = catalog.suggestions("shortcut", language = language.tag)
            assertEquals(listOf("Display-${language.tag}", "Alternative-${language.tag}"), results.map { it.name })
            assertTrue(results.all { it.code == "test_medicine" })
        }
    }
    @Test fun `display uses app language rather than query script and falls back to English`() {
        val catalog = dictionary()
        assertEquals(listOf("Тест", "Другой тест"), catalog.suggestions("shortcut", language = "ru").map { it.name })
        assertEquals("Test medicine", catalog.suggestions("Другой тест", language = "en").single().name)
        assertEquals("Test medicine", catalog.suggestions("shortcut", language = "hy").single().name)
        assertEquals("Тест", catalog.suggestions("shortcut", language = "ru_RU").first().name)
    }
    @Test fun `a regional translation takes priority over the base language`() {
        val value = entry()
        value.getJSONObject("localizations").put("en-GB", JSONArray().put("Regional medicine"))
        val catalog = dictionary(value)
        assertEquals("Regional medicine", catalog.suggestions("shortcut", language = "en-GB").single().name)
        assertEquals("Test medicine", catalog.suggestions("shortcut", language = "en-AU").single().name)
    }
    @Test fun `saved names and forms win without hiding other spellings across translated queries`() {
        val catalog = dictionary()
        val results = catalog.suggestions("Другой тест", listOf(medication("Test medicine")), language = "ru")
        assertEquals(listOf("Test medicine", "Другой тест", "Тест"), results.map { it.name })
        assertTrue(results.first().saved)
        assertEquals(MedicationForm.LIQUID, results.first().form)
        assertTrue(results.all { it.code == "test_medicine" })
    }
    @Test fun `saved spelling replaces its catalog copy but leaves alternate names available`() {
        val results = dictionary().suggestions("shortcut", listOf(medication("Другой тест")), language = "ru")
        assertEquals(listOf("Другой тест", "Тест"), results.map { it.name })
        assertTrue(results.first().saved)
        assertFalse(results.last().saved)
    }
    @Test fun `default suggestions include every variant beyond the old five-result cap`() {
        val value = entry()
        val names = (1..12).map { "Test variant $it" }
        value.getJSONObject("localizations").put("en", JSONArray(names))
        val catalog = dictionary(value)
        assertEquals(names, catalog.suggestions("Test").map { it.name })
        assertEquals(names.take(3), catalog.suggestions("Test", limit = 3).map { it.name })
        assertEquals("Test variant 12", catalog.suggestions("Test variant 12").first().name)
    }
    @Test fun `different saved variants stay available and replace only their own catalog spelling`() {
        val saved = listOf(medication("Тест"), medication("Другой тест").copy(id = "second"))
        val results = dictionary().suggestions("Тест", saved, language = "ru")
        assertEquals(listOf("Тест", "Другой тест"), results.map { it.name })
        assertTrue(results.all { it.saved })
        assertEquals(listOf("test_medicine", "test_medicine"), results.map { it.code })
    }
    @Test fun `equal visible names for different catalog codes are not merged`() {
        val json = JSONObject().put("first", entry()).put("second", entry())
        val results = MedicationDictionary.fromJson(json.toString()).suggestions("Test", language = "en")
        assertEquals(setOf("first", "second"), results.map { it.code }.toSet())
        assertEquals(2, results.size)
    }
    @Test fun `duplicate top level codes cannot silently overwrite a medicine`() {
        rejected("""{"duplicate":${entry()},"duplicate":${entry()}}""")
    }
    @Test fun `codes must be lower case English identifiers`() {
        rejected(entry(), "лекарство")
        rejected(entry(), "Test medicine")
    }
    @Test fun `a default English name is required`() {
        val value = entry()
        value.getJSONObject("localizations").remove("en")
        rejected(value)
    }
    @Test fun `empty names blank names and non text names are rejected`() {
        for (array in listOf(JSONArray(), JSONArray().put("  "), JSONArray().put(42))) {
            val value = entry()
            value.getJSONObject("localizations").put("en", array)
            rejected(value)
        }
    }
    @Test fun `forms must be known unique nonempty enum codes`() {
        for (array in listOf(JSONArray(), JSONArray().put("UNKNOWN"), JSONArray().put("PILL").put("PILL"))) rejected(entry().put("forms", array))
    }
    @Test fun `aliases may be empty or omitted but must contain text when supplied`() {
        assertTrue(dictionary(entry().put("aliases", JSONArray())).entries.single().aliases.isEmpty())
        val value = entry().apply { remove("aliases") }
        assertTrue(dictionary(value).entries.single().aliases.isEmpty())
        rejected(entry().put("aliases", JSONArray().put(42)))
        rejected(entry().put("aliases", "shortcut"))
    }
    @Test fun `invalid root truncated JSON and trailing content are rejected`() {
        rejected("[]")
        rejected("{}")
        rejected("""{"incomplete": """)
        rejected(JSONObject().put("test", entry()).toString() + "{}")
    }
    @Test fun `custom form labels survive JSON loading and require Other`() {
        val value = entry().put("forms", JSONArray().put("OTHER")).put("customForm", "Patch")
        val result = dictionary(value).suggestions("shortcut").single()
        assertEquals(MedicationForm.OTHER, result.form)
        assertEquals("Patch", result.customForm)
        rejected(entry().put("customForm", "Patch"))
    }
}
