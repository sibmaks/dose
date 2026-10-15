# Offline medication-name suggestions

The editor includes a small starter catalog of 14 generic names, with English and Russian search/display names. This is not a complete national medication registry. Free input remains available; saved medications are searched first. Each name appears once, with an editable suggested form on the next step. Search matches whole words or word prefixes, never arbitrary fragments inside a different medication name. Saved names take priority. The dictionary stores no dose amounts, strengths, schedules, or treatment recommendations.

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

Names typed in this editor are searched only on the device. No online service receives the query. The catalog is intentionally small and can be extended in `MedicationDictionary.kt`; suggestions must show distinct names and must not infer doses.

Explicit spelling aliases include «Тероксин» → «Левотироксин» and «Альфокальцидол» → «Альфакальцидол». These return a clearly labelled suggestion for the user to choose; typed names are never silently replaced. No fuzzy matching or clinical equivalence is inferred.
