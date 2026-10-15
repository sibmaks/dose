package io.github.sibmaks.app.dose.localization

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Validate the shipping XML, including every format string used by background reminders. */
class LanguageResourcesTest {
    private val resources = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
    private fun elements(file: File, tag: String): List<Element> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }
    private fun strings(file: File) = elements(file, "string").associateBy { it.getAttribute("name") }
    private val base get() = strings(File(resources, "values/strings.xml"))
    private fun translated(tag: String) = strings(File(resources, "values-$tag/strings.xml"))
    private fun formats(value: String) = Regex("%([0-9]+)\\$([ds])").findAll(value)
        .map { it.groupValues[1] to it.groupValues[2] }.sortedBy { it.first }.toList()

    @Test fun `menu and Android settings advertise the same thirteen languages`() {
        val expected = setOf("en", "ru", "be", "hy", "az", "kk", "ky", "ro", "tg", "tk", "uz", "uk", "ka")
        val menu = AppLanguages.supported.map { it.tag }
        val config = elements(File(resources, "xml/locales_config.xml"), "locale").map { it.getAttribute("android:name") }
        assertEquals(expected, menu.toSet())
        assertEquals(expected.size, menu.size)
        assertEquals(expected, config.toSet())
        assertEquals(expected.size, config.size)
        assertTrue(AppLanguages.supported.all { it.nativeName.isNotBlank() })
    }

    @Test fun `each language translates the complete interface without missing or extra keys`() {
        val required = base.filterValues { it.getAttribute("translatable") != "false" }.keys
        AppLanguages.supported.filter { it.tag != "en" }.forEach { language ->
            val actual = translated(language.tag)
            assertEquals(language.tag, required, actual.keys)
            actual.forEach { (key, element) -> assertTrue("${language.tag}: $key", element.textContent.trim('"').isNotBlank()) }
        }
    }

    @Test fun `all translations preserve format argument numbers and types`() {
        AppLanguages.supported.filter { it.tag != "en" }.forEach { language ->
            translated(language.tag).forEach { (key, element) ->
                assertEquals("${language.tag}: $key", formats(base.getValue(key).textContent), formats(element.textContent))
            }
        }
    }
}
