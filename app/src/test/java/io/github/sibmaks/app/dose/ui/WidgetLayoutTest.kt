package io.github.sibmaks.app.dose.ui

import io.github.sibmaks.app.dose.data.DoseWidgetState
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class WidgetLayoutTest {
    @Test fun `widget row fits complete accessible actions and matches row capacity calculation`() {
        val resources = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resources, "layout/widget_dose_row.xml"))
        val root = document.documentElement
        val nodes = document.getElementsByTagName("*")
        val elements = (0 until nodes.length).map { nodes.item(it) as Element }
        fun element(id: String) = elements.single { it.getAttribute("android:id") == "@+id/$id" }
        fun Element.dp(attribute: String) = getAttribute("android:$attribute").removeSuffix("dp").toInt()
        val actions = element("widget_row_actions")
        assertTrue(actions.dp("layout_height") >= 48)
        for (id in listOf("take_now", "skip")) {
            assertTrue(element(id).dp("minWidth") >= 48)
            assertEquals("match_parent", element(id).getAttribute("android:layout_height"))
        }
        val heading = root.childNodes.let { children ->
            (0 until children.length).map { children.item(it) }.filterIsInstance<Element>().first()
        }
        val requiredHeight = 2 * root.dp("padding") + heading.dp("layout_height") +
            actions.dp("layout_marginTop") + actions.dp("layout_height")
        assertTrue(requiredHeight <= root.dp("layout_height"))
        assertEquals(root.dp("layout_height") + root.dp("layout_marginBottom"), DoseWidgetState.ROW_HEIGHT)
        assertTrue(DoseWidgetState.EXPANDED_HEIGHT >= DoseWidgetState.ROW_HEIGHT + 96)
    }
}
