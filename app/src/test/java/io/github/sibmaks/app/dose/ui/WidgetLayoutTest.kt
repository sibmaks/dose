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
        val dimenNodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resources, "values/dimens.xml")).getElementsByTagName("dimen")
        val dimensions = (0 until dimenNodes.length).associate { index ->
            val dimen = dimenNodes.item(index) as Element
            dimen.getAttribute("name") to dimen.textContent.trim()
        }
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resources, "layout/widget_dose_row.xml"))
        val root = document.documentElement
        val nodes = document.getElementsByTagName("*")
        val elements = (0 until nodes.length).map { nodes.item(it) as Element }
        fun element(id: String) = elements.single { it.getAttribute("android:id") == "@+id/$id" }
        fun Element.dp(attribute: String): Int {
            val value = getAttribute("android:$attribute")
            return (if (value.startsWith("@dimen/")) dimensions.getValue(value.substringAfter('/')) else value)
                .removeSuffix("dp").toInt()
        }
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
        val list = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resources, "layout/widget_dose_list.xml")).documentElement
        val listChildren = list.childNodes.let { children ->
            (0 until children.length).map { children.item(it) }.filterIsInstance<Element>()
        }
        val reservedHeight = 2 * list.dp("padding") + listChildren.first().dp("layout_height") +
            listChildren.last().dp("layout_height")
        assertTrue(DoseWidgetState.EXPANDED_HEIGHT >= DoseWidgetState.ROW_HEIGHT + reservedHeight)
        // Android XML and Compose share the same physical interaction and surface sizes.
        mapOf(
            "dose_touch_target" to DoseDimensions.touchTarget,
            "dose_border" to DoseDimensions.border,
            "dose_radius_tile" to DoseRadii.tile,
            "dose_radius_card" to DoseRadii.card,
            "dose_radius_large_panel" to DoseRadii.largePanel,
            "dose_space_extra_small" to DoseSpacing.extraSmall,
            "dose_space_small" to DoseSpacing.small,
            "dose_space_medium" to DoseSpacing.medium,
        ).forEach { (name, expected) ->
            assertEquals(name, expected.value, dimensions.getValue(name).removeSuffix("dp").toFloat())
        }
    }
}
