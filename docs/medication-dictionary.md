# Offline medication-name suggestions

The editor loads `app/src/main/assets/medication-dictionary.json`, a small starter catalog of 14 generic names, with English and Russian search/display names. This is not a complete national medication registry. Free input remains available; saved medications are searched first. Each name appears once, with an editable suggested form on the next step. Search matches whole words or word prefixes, never arbitrary fragments inside a different medication name. Saved names take priority. The dictionary stores no dose amounts, strengths, schedules, or treatment recommendations.

The catalog covers paracetamol, ibuprofen, amoxicillin, cetirizine, omeprazole, hydrocortisone, vitamin D3, calcium, alfacalcidol, loratadine, metformin, levothyroxine, insulin, and salbutamol. An inhaler uses Other with an editable custom label. Listed variants are examples of available forms rather than an exhaustive inventory of products in each country.

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

Names typed in this editor are searched only on the device. No online service receives the query. The catalog is intentionally small. Extend the JSON asset and rebuild the app; Kotlin changes are not needed to add names, translations, forms, or aliases. Suggestions must show distinct medications and must not infer doses.

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

- `localizations`: language tag → nonempty list of names. The first name is used for display; every name is searchable. English (`en`) is required as the fallback. Current app tags are `en`, `ru`, `be`, `hy`, `az`, `kk`, `ky`, `ro`, `tg`, `tk`, `uz`, `uk`, and `ka`. Missing translations fall back to English. The shipping catalog preserves the existing English/Russian names rather than inventing translations for other languages.
- `forms`: a nonempty, ordered list of `PILL`, `CAPSULE`, `LIQUID`, `SPRAY`, `DROPS`, `INJECTION`, `CREAM`, or `OTHER`. The first form is preselected; the next editor step still allows changing it. These describe catalog form categories, not a prescribed form for any user.
- `aliases`: alternate search terms in any language. Use `[]` when there are none; omitted aliases are also read as an empty list. Localized alternative names can stay in their respective `localizations` arrays.
- `customForm` (optional): custom text for the `OTHER` form, preserving the existing inhaler entry.

Search matches all localizations and aliases, regardless of the chosen app language. Display follows the selected app language, including when a query uses a different script. Regional tags fall back to the base language and then English. Stable codes deduplicate localized names and aliases; saved names/forms take priority, including across translated queries. Saved medication records are not rewritten or migrated.

The JSON is read from APK assets and cached on the first editor opening. It is parsed and validated by the same loader used in unit tests. Duplicate root codes, invalid codes/forms, missing English fallback, blank names, and non-text aliases are rejected. The tests exercise the shipping asset, all app language tags, aliases, localization fallback, and cross-language deduplication.
