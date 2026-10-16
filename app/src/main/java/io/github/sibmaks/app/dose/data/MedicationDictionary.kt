package io.github.sibmaks.app.dose.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Locale

/** A catalog suggestion carries its stable identity without rewriting saved medication names. */
data class MedicationSuggestion(
    val name: String, val form: MedicationForm, val customForm: String = "",
    val saved: Boolean = false, val code: String? = null,
) {
    val formLabel: String get() = customForm.takeIf { form == MedicationForm.OTHER && it.isNotBlank() } ?: form.label
}

data class MedicationDictionaryEntry(
    val code: String,
    val localizations: Map<String, List<String>>,
    val forms: List<MedicationForm>,
    val aliases: List<String>,
    val customForm: String = "",
) {
    fun names(language: String): List<String> {
        val tag = language.replace('_', '-').lowercase(Locale.ROOT)
        return localizations[tag] ?: localizations[tag.substringBefore('-')] ?: localizations.getValue("en")
    }
}

/** The bundled JSON is the only source of medication names, forms, and search aliases. */
class MedicationDictionary private constructor(val entries: List<MedicationDictionaryEntry>) {
    private data class IndexedEntry(val entry: MedicationDictionaryEntry, val names: List<String>, val candidates: List<List<String>>)
    private val index = entries.map { entry ->
        val names = entry.localizations.values.flatten()
        IndexedEntry(entry, names, (names + entry.aliases + entry.code).distinct().map(::words))
    }
    // Ambiguous names must not merge two different catalog medications.
    private val codesByName = index.flatMap { item -> (item.names + item.entry.aliases).map { normalize(it) to item.entry.code } }
        .groupBy({ it.first }, { it.second }).mapValues { (_, codes) -> codes.distinct().singleOrNull() }

    fun suggestions(query: String, saved: List<Medication> = emptyList(), limit: Int = 5, language: String = "en"): List<MedicationSuggestion> {
        val text = normalize(query)
        val queryWords = words(text)
        if (text.length < 2 || queryWords.isEmpty() || limit <= 0) return emptyList()
        val matchingEntries = index.filter { item -> item.candidates.any { matches(it, queryWords) } }
        val matchingCodes = matchingEntries.map { it.entry.code }.toSet()
        val own = saved.filter { matches(words(it.name), queryWords) || codesByName[normalize(it.name)] in matchingCodes }
            .sortedBy { if (normalize(it.name) == text) 0 else 1 }
            .map { MedicationSuggestion(it.name, it.form, it.customForm, saved = true, code = codesByName[normalize(it.name)]) }
        val catalog = matchingEntries
            .sortedBy { item ->
                val preferred = item.entry.names(language)
                when {
                    preferred.any { normalize(it) == text } -> 0
                    preferred.any { matches(words(it), queryWords) } -> 1
                    item.names.any { normalize(it) == text } -> 2
                    item.names.any { matches(words(it), queryWords) } -> 3
                    else -> 4
                }
            }.map { item ->
                val entry = item.entry
                MedicationSuggestion(entry.names(language).first(), entry.forms.first(), entry.customForm, code = entry.code)
            }
        // One suggestion per code across localized names and aliases; saved variants keep priority.
        return (own + catalog).distinctBy { it.code?.let { code -> "code:$code" } ?: "name:${normalize(it.name)}" }.take(limit)
    }

    companion object {
        const val ASSET_FILE = "medication-dictionary.json"
        @Volatile private var cached: MedicationDictionary? = null

        fun load(context: Context): MedicationDictionary = cached ?: synchronized(this) {
            cached ?: context.applicationContext.assets.open(ASSET_FILE).bufferedReader(Charsets.UTF_8)
                .use { fromJson(it.readText()) }.also { cached = it }
        }

        fun fromJson(json: String): MedicationDictionary = try {
            val reader = JSONTokener(json)
            require(reader.nextClean() == '{') { "Medication dictionary must be an object keyed by English codes" }
            val entries = mutableListOf<MedicationDictionaryEntry>()
            val codes = mutableSetOf<String>()
            if (reader.nextClean() != '}') {
                reader.back()
                while (true) {
                    require(reader.nextClean() == '"') { "Medication codes must be quoted JSON keys" }
                    val code = reader.nextString('"')
                    require(Regex("[a-z][a-z0-9]*(?:[_-][a-z0-9]+)*").matches(code)) { "Invalid English medication code: $code" }
                    require(codes.add(code)) { "Duplicate medication code: $code" }
                    require(reader.nextClean() == ':') { "Missing value for $code" }
                    val value = reader.nextValue()
                    require(value is JSONObject) { "$code must contain a medication object" }
                    entries += parseEntry(code, value)
                    when (reader.nextClean()) {
                        '}' -> break
                        ',' -> Unit
                        else -> throw IllegalArgumentException("Expected another medication or end of dictionary")
                    }
                }
            }
            require(entries.isNotEmpty()) { "Medication dictionary must not be empty" }
            require(reader.nextClean() == '\u0000') { "Unexpected content after medication dictionary" }
            MedicationDictionary(entries)
        } catch (failure: JSONException) {
            throw IllegalArgumentException("Invalid medication dictionary JSON", failure)
        }

        private fun parseEntry(code: String, value: JSONObject): MedicationDictionaryEntry {
            val localized = value.getJSONObject("localizations")
            val languageTags = localized.keys().asSequence().toList()
            require(languageTags.map { it.lowercase(Locale.ROOT) }.distinct().size == languageTags.size) { "$code contains duplicate language tags" }
            val names = languageTags.associate { language ->
                require(Regex("[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})*").matches(language)) { "$code has an invalid language tag: $language" }
                language.lowercase(Locale.ROOT) to strings(localized.getJSONArray(language), "$code.localizations.$language", allowEmpty = false)
            }
            require("en" in names) { "$code needs English names for localization fallback" }
            val forms = strings(value.getJSONArray("forms"), "$code.forms", allowEmpty = false).map { form ->
                require(MedicationForm.entries.any { it.name == form }) { "$code has an unknown form: $form" }
                MedicationForm.valueOf(form)
            }
            val aliases = if (value.has("aliases")) strings(value.getJSONArray("aliases"), "$code.aliases", allowEmpty = true) else emptyList()
            val custom = if (value.has("customForm")) {
                val text = value.get("customForm")
                require(text is String) { "$code.customForm must be text" }
                text.trim()
            } else ""
            require(custom.isBlank() || MedicationForm.OTHER in forms) { "$code.customForm requires the OTHER form" }
            return MedicationDictionaryEntry(code, names, forms, aliases, custom)
        }

        private fun strings(array: JSONArray, location: String, allowEmpty: Boolean): List<String> {
            require(allowEmpty || array.length() > 0) { "$location must not be empty" }
            val values = (0 until array.length()).map { index ->
                val text = array.get(index)
                require(text is String && text.isNotBlank()) { "$location[$index] must be non-empty text" }
                text.trim()
            }
            require(values.map(::normalize).distinct().size == values.size) { "$location contains duplicate values" }
            return values
        }

        private fun normalize(value: String) = value.trim().lowercase(Locale.ROOT).replace('ё', 'е')
        private fun words(value: String) = Regex("[\\p{L}\\p{N}]+").findAll(normalize(value)).map { it.value }.toList()
        private fun matches(candidate: List<String>, query: List<String>) = query.all { part -> candidate.any { it.startsWith(part) } }
    }
}
