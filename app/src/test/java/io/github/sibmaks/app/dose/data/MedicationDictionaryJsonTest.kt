package io.github.sibmaks.app.dose.data

import io.github.sibmaks.app.dose.localization.AppLanguages
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

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

    @Test fun `bundled asset preserves all fourteen codes and their supported forms`() {
        val source = listOf(File("src/main/assets/medication-dictionary.json"), File("app/src/main/assets/medication-dictionary.json")).first { it.isFile }
        val catalog = MedicationDictionary.fromJson(source.readText())
        assertEquals(setOf("paracetamol", "ibuprofen", "amoxicillin", "cetirizine", "omeprazole", "hydrocortisone", "vitamin_d3", "calcium", "alfacalcidol", "loratadine", "metformin", "levothyroxine", "insulin", "salbutamol"), catalog.entries.map { it.code }.toSet())
        assertTrue(catalog.entries.all { it.localizations.keys.all { language -> AppLanguages.supported.any { it.tag == language } } })
        assertEquals(listOf(MedicationForm.PILL, MedicationForm.CAPSULE, MedicationForm.LIQUID), catalog.entries.single { it.code == "paracetamol" }.forms)
        assertEquals(listOf(MedicationForm.INHALER), catalog.entries.single { it.code == "salbutamol" }.forms)
        assertEquals("levothyroxine", catalog.suggestions("Левотероксин", language = "ru").single().code)
    }
    @Test fun `each localization can supply multiple searchable names with one stable result`() {
        val dictionary = dictionary()
        val preferred = dictionary.suggestions("Тест", language = "ru").single()
        val alternate = dictionary.suggestions("Другой тест", language = "ru").single()
        val alias = dictionary.suggestions("shortcut", language = "ru").single()
        assertEquals("Тест", preferred.name)
        assertEquals(preferred, alternate)
        assertEquals(preferred, alias)
        assertEquals("test_medicine", alias.code)
        assertEquals(MedicationForm.CAPSULE, alias.form)
    }
    @Test fun `every app language can select its primary JSON name`() {
        val value = entry()
        AppLanguages.supported.forEach { language ->
            value.getJSONObject("localizations").put(language.tag, JSONArray().put("Display-${language.tag}").put("Alternative-${language.tag}"))
        }
        val catalog = dictionary(value)
        AppLanguages.supported.forEach { language ->
            val result = catalog.suggestions("shortcut", language = language.tag).single()
            assertEquals("Display-${language.tag}", result.name)
            assertEquals("test_medicine", result.code)
        }
    }
    @Test fun `display uses app language rather than query script and falls back to English`() {
        val catalog = dictionary()
        assertEquals("Тест", catalog.suggestions("shortcut", language = "ru").single().name)
        assertEquals("Test medicine", catalog.suggestions("Другой тест", language = "en").single().name)
        assertEquals("Test medicine", catalog.suggestions("shortcut", language = "hy").single().name)
        assertEquals("Тест", catalog.suggestions("shortcut", language = "ru_RU").single().name)
    }
    @Test fun `a regional translation takes priority over the base language`() {
        val value = entry()
        value.getJSONObject("localizations").put("en-GB", JSONArray().put("Regional medicine"))
        val catalog = dictionary(value)
        assertEquals("Regional medicine", catalog.suggestions("shortcut", language = "en-GB").single().name)
        assertEquals("Test medicine", catalog.suggestions("shortcut", language = "en-AU").single().name)
    }
    @Test fun `saved names and forms win across translated queries without duplicates`() {
        val catalog = dictionary()
        val results = catalog.suggestions("Другой тест", listOf(medication("Test medicine")), language = "ru")
        assertEquals(1, results.size)
        assertTrue(results.single().saved)
        assertEquals("Test medicine", results.single().name)
        assertEquals(MedicationForm.LIQUID, results.single().form)
        assertEquals("test_medicine", results.single().code)
    }
    @Test fun `an alias search also preserves the saved spelling`() {
        val result = dictionary().suggestions("shortcut", listOf(medication("Другой тест")), language = "en").single()
        assertEquals("Другой тест", result.name)
        assertTrue(result.saved)
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
