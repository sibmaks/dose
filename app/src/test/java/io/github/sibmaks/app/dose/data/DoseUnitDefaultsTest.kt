package io.github.sibmaks.app.dose.data

import org.junit.Assert.*
import org.junit.Test

class DoseUnitDefaultsTest {
    @Test fun `known forms default to relevant built-in units`() {
        val expected = mapOf(
            MedicationForm.PILL to "tablet", MedicationForm.CAPSULE to "capsule",
            MedicationForm.LIQUID to "ml", MedicationForm.SPRAY to "spray",
            MedicationForm.DROPS to "drop", MedicationForm.INJECTION to "ml", MedicationForm.CREAM to "g",
            MedicationForm.INHALER to "puff", MedicationForm.GEL to "g", MedicationForm.OINTMENT to "g",
            MedicationForm.PATCH to "patch", MedicationForm.POWDER to "g", MedicationForm.SUPPOSITORY to "suppository",
        )
        expected.forEach { (form, unit) ->
            assertEquals(unit, DoseUnits.defaultFor(form))
            assertTrue(unit in DoseUnits.common)
        }
    }
    @Test fun `Other leaves the measurement for the user to choose`() {
        assertEquals("", DoseUnits.defaultFor(MedicationForm.OTHER))
        assertEquals("", DoseUnits.forFormChange(MedicationForm.OTHER, "tablet", automatic = true))
    }
    @Test fun `untouched defaults follow repeated form and suggestion changes`() {
        var unit = DoseUnits.defaultFor(MedicationForm.PILL)
        unit = DoseUnits.forFormChange(MedicationForm.LIQUID, unit, automatic = true)
        assertEquals("ml", unit)
        unit = DoseUnits.forFormChange(MedicationForm.DROPS, unit, automatic = true)
        assertEquals("drop", unit)
        unit = DoseUnits.forFormChange(MedicationForm.CAPSULE, unit, automatic = true)
        assertEquals("capsule", unit)
    }
    @Test fun `saved and explicitly selected built-in units are preserved for all forms`() {
        for (form in MedicationForm.entries) for (unit in DoseUnits.common) {
            assertEquals(unit, DoseUnits.forFormChange(form, unit, automatic = false))
        }
    }
    @Test fun `custom and deliberately cleared units are never overwritten`() {
        for (form in MedicationForm.entries) for (unit in listOf("vials", "штука", "")) {
            assertEquals(unit, DoseUnits.forFormChange(form, unit, automatic = false))
        }
    }
}
