package io.github.sibmaks.app.dose.data

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import kotlin.random.Random

/** Compare indexed retrieval with the original scan, preserving ranking and candidate boundaries. */
class MedicationDictionarySearchTest {
    private val catalog = MedicationDictionaryTestAsset.load()
    private fun normalize(value: String) = value.trim().lowercase(Locale.ROOT).replace('ё', 'е')
    private val pattern = Regex("[\\p{L}\\p{N}]+")
    private fun words(value: String) = pattern.findAll(normalize(value)).map { it.value }.toList()
    private fun matches(name: String, query: List<String>) = query.all { part -> words(name).any { it.startsWith(part) } }

    private fun scan(query: String, saved: List<Medication>, limit: Int, language: String): List<MedicationSuggestion> {
        val normalized = normalize(query)
        val parts = words(query)
        if (normalized.length < 2 || parts.isEmpty() || limit <= 0) return emptyList()
        val names = catalog.entries.associateWith { it.localizations.values.flatten() }
        val codesByName = catalog.entries.flatMap { entry -> (names.getValue(entry) + entry.aliases).map { normalize(it) to entry.code } }
            .groupBy({ it.first }, { it.second }).mapValues { (_, codes) -> codes.distinct().singleOrNull() }
        val matching = catalog.entries.filter { entry -> (names.getValue(entry) + entry.aliases + entry.code).any { matches(it, parts) } }
        val codes = matching.map { it.code }.toSet()
        val own = saved.filter { matches(it.name, parts) || codesByName[normalize(it.name)] in codes }
            .sortedBy { if (normalize(it.name) == normalized) 0 else 1 }
            .map { MedicationSuggestion(it.name, it.form, it.customForm, saved = true, code = codesByName[normalize(it.name)]) }
        val candidates = matching.sortedBy { entry ->
            when {
                entry.names(language).any { normalize(it) == normalized } -> 0
                entry.names(language).any { matches(it, parts) } -> 1
                names.getValue(entry).any { normalize(it) == normalized } -> 2
                names.getValue(entry).any { matches(it, parts) } -> 3
                else -> 4
            }
        }.map { MedicationSuggestion(it.names(language).first(), it.forms.first(), it.customForm, code = it.code) }
        return (own + candidates).distinctBy { it.code?.let { code -> "code:$code" } ?: "name:${normalize(it.name)}" }.take(limit)
    }

    @Test fun `index matches original retrieval across scripts prefixes aliases and saved variants`() {
        val saved = listOf(
            Medication("saved", "Paracetamol", "1", "ml", listOf(DoseSchedule()), form = MedicationForm.LIQUID),
            Medication("custom", "My custom medicine", "1", "mg", listOf(DoseSchedule())),
        )
        val samples = catalog.entries.flatMap { it.localizations.values.flatten() + it.aliases + it.code }
            .flatMap { listOf(it, words(it).joinToString(" ") { word -> word.take(3) }) }
            .distinct().shuffled(Random(16)).take(60)
        val queries = samples + listOf("Кальций", "Тероксин", "витамин d", "ins gl", "acetaminophen", "my cust", "  ПАРА  ", "missing term", "a", "!!!")
        for (language in listOf("en", "ru", "be", "hy", "az", "kk", "ky", "ro", "tg", "tk", "uz", "uk", "ka", "ru_RU")) {
            for (query in queries) for (limit in listOf(1, 5)) {
                assertEquals("$language / $query / $limit", scan(query, saved, limit, language), catalog.suggestions(query, saved, limit, language))
            }
        }
    }

    @Test fun `multiword query cannot join words from unrelated names of the same entry`() {
        val fixture = MedicationDictionary.fromJson("""{
            "first":{"localizations":{"en":["Alpha beta"],"ru":["Gamma delta"]},"forms":["PILL"],"aliases":["Other words"]},
            "second":{"localizations":{"en":["Alpha delta"]},"forms":["LIQUID"]}
        }""")
        assertEquals(listOf("second"), fixture.suggestions("alph delt").map { it.code })
        assertEquals(listOf("first"), fixture.suggestions("be al").map { it.code })
        assertTrue(fixture.suggestions("alpha words").isEmpty())
        assertTrue(fixture.suggestions("lpha").isEmpty())
    }

    @Test fun `stable codes retrieve complete entries without scanning or changing saved names`() {
        catalog.entries.forEach { assertEquals(it, catalog.findByCode(it.code)) }
        assertNull(catalog.findByCode("unknown_code"))
        assertNull(catalog.findByCode("PARACETAMOL"))
    }
}
