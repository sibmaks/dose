# Offline medication-name suggestions

The editable catalog is `app/src/main/dictionary/medication-dictionary.json`. It currently contains 170 entries with localized names for all 13 app languages. Gradle generates the compressed APK asset `medication-dictionary.json.gzip`; the raw source is not packaged. This is not a complete national medication registry. Free input remains available; saved medications are searched first. Each name appears once, with an editable suggested form on the next step. Search matches whole words or word prefixes, never arbitrary fragments inside a different medication name. Saved names take priority. The dictionary stores no dose amounts, strengths, schedules, or treatment recommendations.

The original starter catalog covered paracetamol, ibuprofen, amoxicillin, cetirizine, omeprazole, hydrocortisone, vitamin D3, calcium, alfacalcidol, loratadine, metformin, levothyroxine, insulin, and salbutamol. Inhaler is now a built-in form. Additional entries and translations are maintained in the JSON; the source links below document the starter entries rather than every later extension. Listed variants are examples of available forms rather than an exhaustive inventory of products in each country.

Name/form facts were checked on 2026-10-15 against these primary patient-information sources. Only names and form categories are included; no patient-information text or dosage guidance is copied:

- [Paracetamol — NHS](https://www.nhs.uk/medicines/paracetamol-for-adults/)
- [Ibuprofen — NHS](https://www.nhs.uk/medicines/ibuprofen-for-adults/)
- [Amoxicillin — NHS](https://www.nhs.uk/medicines/amoxicillin/about-amoxicillin/)
- [Cetirizine — NHS](https://www.nhs.uk/medicines/cetirizine/about-cetirizine/)
- [Omeprazole — NHS](https://www.nhs.uk/medicines/omeprazole/)
- [Hydrocortisone — NHS](https://www.nhs.uk/medicines/hydrocortisone/)
- [Colecalciferol / vitamin D — NHS](https://www.nhs.uk/medicines/colecalciferol/about-colecalciferol/)
- [Calcium supplements — NHS](https://www.nhs.uk/conditions/vitamins-and-minerals/calcium/)
- [Alfacalcidol capsules — manufacturer SmPC](https://www.medicines.org.uk/emc/product/14384/smpc)
- [Alfacalcidol oral drops — manufacturer SmPC](https://www.medicines.org.uk/emc/product/3843/smpc)
- [Loratadine — NHS](https://www.nhs.uk/medicines/loratadine/about-loratadine/)
- [Metformin — NHS Scotland](https://mydiabetesmyway.scot.nhs.uk/resources/internal/what-is-metformin/)
- [Levothyroxine — NHS](https://www.nhs.uk/medicines/levothyroxine/)
- [Insulin injections — NHS](https://www.nhs.uk/medicines/insulin/intermediate-acting-insulin/about-intermediate-acting-insulin/)
- [Salbutamol inhalers — NHS](https://www.nhs.uk/medicines/salbutamol-inhaler/about-salbutamol-inhalers/)

Names typed in this editor are searched only on the device. No online service receives the query. Extend the editable JSON source and rebuild the app; Kotlin changes are not needed to add names, translations, forms, or aliases. Suggestions must show distinct medications and must not infer doses.

Explicit spelling aliases include «Тероксин» → «Левотироксин» and «Альфокальцидол» → «Альфакальцидол». These return a clearly labelled suggestion for the user to choose; typed names are never silently replaced. No fuzzy matching or clinical equivalence is inferred.

## JSON format

The root object maps a unique, stable English code to each medication. Codes use lowercase ASCII letters, digits, underscores, or hyphens; keep the code unchanged when adding translations or synonyms.

```json
{
  "levothyroxine": {
    "localizations": {
      "en": ["Levothyroxine"],
      "ru": ["Левотироксин", "Левотероксин", "Тироксин", "Л-тироксин", "Тероксин"]
    },
    "forms": ["PILL", "LIQUID"],
    "aliases": ["thyroxine"]
  }
}
```

- `localizations`: language tag → nonempty list of names. The first name is used for display; every name is searchable. English (`en`) is required as the fallback. Current app tags are `en`, `ru`, `be`, `hy`, `az`, `kk`, `ky`, `ro`, `tg`, `tk`, `uz`, `uk`, and `ka`. Missing translations fall back to English. The expanded catalog supplies translations for the supported app languages; individual missing translations still fall back to English.
- `forms`: a nonempty, ordered list of `PILL`, `CAPSULE`, `LIQUID`, `SPRAY`, `DROPS`, `INJECTION`, `CREAM`, `INHALER`, `GEL`, `OINTMENT`, `PATCH`, `POWDER`, `SUPPOSITORY`, or `OTHER`. The first form is preselected; the next editor step still allows changing it. These describe catalog form categories, not a prescribed form for any user.
- `aliases`: alternate search terms in any language. Use `[]` when there are none; omitted aliases are also read as an empty list. Localized alternative names can stay in their respective `localizations` arrays.
- `customForm` (optional): custom text for the `OTHER` form. Saved custom forms remain unchanged when built-in forms are added.

Search matches all localizations and aliases, regardless of the chosen app language. Display follows the selected app language, including when a query uses a different script. Regional tags fall back to the base language and then English. Stable codes deduplicate localized names and aliases; saved names/forms take priority, including across translated queries. Saved medication records are not rewritten or migrated.

## Packaging and retrieval

Edit the JSON source, then build normally. `:app:compressMedicationDictionary` generates a deterministic gzip archive under `app/build/generated/medicationDictionaryAssets/`. The task runs for every build variant when the source changes. Generated assets stay out of Git, and the APK contains only the compressed catalog. The `.gzip` suffix is intentional: Android's asset merger expands `.gz` files automatically, which would undo the storage change. The archive preserves the source byte-for-byte, including all names, aliases, form order, and stable codes. No database engine or additional runtime library is required.

The app reads the gzip asset directly into its cached catalog on a background dispatcher; it does not extract another copy into app storage. `MedicationDictionary.suggestions(query, saved, limit, language)` uses a sorted word-to-entry index to retrieve matching prefixes instead of scanning every entry. It preserves the existing ranking, word boundaries, alias behavior, English fallback and saved-medication priority. All query words must match one name, alias, or code; words from unrelated translations cannot combine into a false match. `findByCode(code)` retrieves a complete entry by its exact stable code.

For the current catalog, the editable source is 138,084 bytes and the generated gzip asset is approximately 16 KB. APKs already compress JSON assets, so this is a comparison with the source file, not an 88% reduction in total APK size. A database is unnecessary at this catalog size. If the catalog grows to require on-demand disk queries, Android's built-in SQLite is a suitable next step without adding an H2 runtime.

Parsing and validation remain shared between source JSON and gzip loading. Duplicate root codes, invalid codes/forms, missing English fallback, blank names, and non-text aliases are rejected. Gradle tracks both the source and generated archive as test inputs. Tests exercise the actual packaged asset, byte-for-byte decompression, malformed archives, all app languages, aliases, localization fallback, cross-language deduplication, and indexed search parity with the original scan. They allow extending the catalog while preserving the original stable codes.

Run validation with:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline
```
