package io.github.sibmaks.app.dose.data

import org.junit.Assert.*
import org.junit.Test

class MedicationAmountValidationTest {
    @Test fun `empty amount has its own required error`() {
        for (amount in listOf("", "  ")) {
            assertEquals(MedicationAmountIssue.REQUIRED, MedicationEditorFlow.amountIssue(amount))
        }
    }

    @Test fun `zero and negative amounts expose the positive amount error`() {
        for (amount in listOf("0", "0.0", "0,0", "-1", " -0,5 ")) {
            assertEquals(amount, MedicationAmountIssue.NON_POSITIVE, MedicationEditorFlow.amountIssue(amount))
        }
    }

    @Test fun `positive and legacy free form amounts preserve existing support`() {
        for (amount in listOf("1", "0.5", "0,5", "1/2", "as prescribed")) {
            assertNull(amount, MedicationEditorFlow.amountIssue(amount))
        }
    }
}
