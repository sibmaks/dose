package io.github.sibmaks.app.dose.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Locale
import java.util.TreeMap
import java.io.InputStream
import java.util.zip.GZIPInputStream

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

/** A generated gzip asset supplies catalog data; a word index serves prefix lookups offline. */
class MedicationDictionary private constructor(val entries: List<MedicationDictionaryEntry>) {
    private data class IndexedEntry(val entry: MedicationDictionaryEntry, val names: List<String>, val candidates: List<List<String>>)
    private val index = entries.map { entry ->
        val names = entry.localizations.values.flatten()
        IndexedEntry(entry, names, (names + entry.aliases + entry.code).distinct().map(::words).distinct())
    }
    // Sorted word -> entry IDs. Prefix ranges avoid scanning every medicine on each keystroke.
    private val wordIndex = TreeMap<String, IntArray>().apply {
        val postings = mutableMapOf<String, MutableSet<Int>>()
        index.forEachIndexed { id, item ->
            item.candidates.flatten().toSet().forEach { word -> postings.getOrPut(word) { linkedSetOf() }.add(id) }
        }
        postings.forEach { (word, ids) -> put(word, ids.toIntArray()) }
    }
    private val entriesByCode = entries.associateBy { it.code }

    fun findByCode(code: String): MedicationDictionaryEntry? = entriesByCode[code]

    private fun matchingEntries(query: List<String>): List<IndexedEntry> {
        var candidates: Set<Int>? = null
        for (part in query.distinct()) {
            val matches = mutableSetOf<Int>()
            for ((word, ids) in wordIndex.tailMap(part, true)) {
                if (!word.startsWith(part)) break
                ids.forEach { matches.add(it) }
            }
            candidates = candidates?.intersect(matches) ?: matches
            if (candidates.isEmpty()) return emptyList()
        }
        // Keep catalog order for equal-ranked suggestions. Multiword matches must belong
        // to one name or alias, never to separate translations of the same medicine.
        return candidates.orEmpty().sorted().map { index[it] }
            .filter { item -> item.candidates.any { matches(it, query) } }
    }

    // Ambiguous names must not merge two different catalog medications.
    private val codesByName = index.flatMap { item -> (item.names + item.entry.aliases).map { normalize(it) to item.entry.code } }
        .groupBy({ it.first }, { it.second }).mapValues { (_, codes) -> codes.distinct().singleOrNull() }

    fun suggestions(query: String, saved: List<Medication> = emptyList(), limit: Int = Int.MAX_VALUE, language: String = "en"): List<MedicationSuggestion> {
        val text = normalize(query)
        val queryWords = words(text)
        if (text.length < 2 || queryWords.isEmpty() || limit <= 0) return emptyList()
        val matchingEntries = matchingEntries(queryWords)
        val matchingCodes = matchingEntries.map { it.entry.code }.toSet()
        val own = saved.filter { matches(words(it.name), queryWords) || codesByName[normalize(it.name)] in matchingCodes }
            .sortedBy { if (normalize(it.name) == text) 0 else 1 }
            .map { MedicationSuggestion(it.name, it.form, it.customForm, saved = true, code = codesByName[normalize(it.name)]) }
        val catalog = matchingEntries.flatMap { item ->
            val entry = item.entry
            entry.names(language).map { name ->
                val rank = when {
                    normalize(name) == text -> 0
                    matches(words(name), queryWords) -> 1
                    item.names.any { normalize(it) == text } -> 2
                    item.names.any { matches(words(it), queryWords) } -> 3
                    else -> 4
                }
                rank to MedicationSuggestion(name, entry.forms.first(), entry.customForm, code = entry.code)
            }
        }.sortedBy { it.first }.map { it.second }
        // Variants share a catalog code but remain independently selectable. Saved copies
        // replace only the same spelling, preserving other localized names of that medicine.
        return (own + catalog).distinctBy { it.code to normalize(it.name) }.take(limit)
    }

    companion object {
        const val ASSET_FILE = "medication-dictionary.json.gzip"
        @Volatile private var cached: MedicationDictionary? = null

        fun load(context: Context): MedicationDictionary = cached ?: synchronized(this) {
            cached ?: fromGzip(context.applicationContext.assets.open(ASSET_FILE)).also { cached = it }
        }

        /** Reads and closes the compressed asset without extracting a second copy to app storage. */
        fun fromGzip(input: InputStream): MedicationDictionary =
            input.use { source -> GZIPInputStream(source).bufferedReader(Charsets.UTF_8).use { fromJson(it.readText()) } }

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
        private val wordPattern = Regex("[\\p{L}\\p{N}]+")
        private fun words(value: String) = wordPattern.findAll(normalize(value)).map { it.value }.toList()
        private fun matches(candidate: List<String>, query: List<String>) = query.all { part -> candidate.any { it.startsWith(part) } }
    }
}
