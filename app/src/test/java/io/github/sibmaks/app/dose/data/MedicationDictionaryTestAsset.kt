package io.github.sibmaks.app.dose.data

import java.io.File

/** Gradle supplies the authoring source and the exact generated asset packaged in the app. */
internal object MedicationDictionaryTestAsset {
    val source: File get() = File(requireNotNull(System.getProperty("dose.dictionary.source")))
    val compressed: File get() = File(requireNotNull(System.getProperty("dose.dictionary.asset")))
    fun load(): MedicationDictionary = compressed.inputStream().use(MedicationDictionary::fromGzip)
}
