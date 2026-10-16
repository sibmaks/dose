package io.github.sibmaks.app.dose.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class MedicationFormsTest {
    private val resources = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
    private fun labels(language: String): Map<MedicationForm, String> {
        val dir = if (language == "en") "values" else "values-$language"
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(resources, "$dir/strings.xml")).getElementsByTagName("string")
        val strings = (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent.trim('"') }
        return MedicationForm.entries.associateWith { strings.getValue(it.name.lowercase(Locale.ROOT)) }
    }

    @Test fun `form order follows translated names and changes with the app language`() {
        val english = MedicationForms.sorted(Locale.ENGLISH, labels("en")::getValue)
        assertEquals(listOf(MedicationForm.CAPSULE, MedicationForm.CREAM, MedicationForm.DROPS, MedicationForm.GEL,
            MedicationForm.INHALER, MedicationForm.INJECTION, MedicationForm.LIQUID, MedicationForm.OINTMENT,
            MedicationForm.PATCH, MedicationForm.PILL, MedicationForm.POWDER,
            MedicationForm.SPRAY, MedicationForm.SUPPOSITORY, MedicationForm.OTHER), english)
        val russian = MedicationForms.sorted(Locale.forLanguageTag("ru"), labels("ru")::getValue)
        assertEquals(MedicationForm.GEL, russian.first())
        assertNotEquals(english, russian)
        for (language in listOf("en", "ru", "be", "hy", "az", "kk", "ky", "ro", "tg", "tk", "uz", "uk", "ka")) {
            val translated = labels(language)
            assertTrue(translated.values.all { it.isNotBlank() })
            val ordered = MedicationForms.sorted(Locale.forLanguageTag(language), translated::getValue)
            assertEquals(MedicationForm.entries.toSet(), ordered.toSet())
            assertEquals(language, MedicationForm.OTHER, ordered.last())
        }
    }

    @Test fun `all forms round trip by stable code and retain user selected units and colors`() {
        for (form in MedicationForm.entries) {
            val medication = Medication("id", "Test", "2", "custom unit", listOf(DoseSchedule(createdOn = "2026-10-16")), color = "#FFFFFF", form = form)
            val restored = MedicationJson.decode(MedicationJson.encode(medication), LocalDate.of(2026, 10, 16))!!
            assertEquals(medication, restored)
        }
    }

    @Test fun `all forms have native vector paths matching their SVG sources`() {
        val android = "http://schemas.android.com/apk/res/android"
        val design = File(resources, "../../../../design-reference/medication-forms").let { if (it.isDirectory) it else File("design-reference/medication-forms") }
        for (form in MedicationForm.entries) {
            val code = form.name.lowercase(Locale.ROOT)
            val xml = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(File(resources, "drawable/ic_form_$code.xml"))
            val svg = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(design, "$code.svg"))
            val native = xml.getElementsByTagName("path")
            val source = svg.getElementsByTagName("path")
            assertTrue(native.length > 0)
            assertEquals(code, source.length, native.length)
            for (i in 0 until native.length) assertEquals(code,
                (source.item(i) as Element).getAttribute("d"), (native.item(i) as Element).getAttributeNS(android, "pathData"))
        }
    }
}
