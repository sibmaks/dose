package io.github.sibmaks.app.dose.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DoseLogJsonTest {
    @Test fun capturedDeadlineSurvivesPersistenceAndLaterRewrites() {
        val log = DoseLog("2026-10-15", "a", "Test", "1 tablet", "08:00", "taken", 1_792_046_000_000, 1_792_045_800_000)
        assertEquals(log, DoseLogJson.decode(JSONObject(DoseLogJson.encode(log).toString())))
    }
    @Test fun oldLogsRemainReadableWithoutInventingADeadline() {
        val json = JSONObject("""{"date":"2026-10-15","medicationId":"a","medicationName":"Test","dose":"500 mg","time":"08:00","status":"taken","recordedAt":1792046000000}""")
        val log = DoseLogJson.decode(json)!!
        assertNull(log.scheduledAt)
        assertEquals("taken", log.status)
        assertEquals(log, DoseLogJson.decode(DoseLogJson.encode(log)))
    }
    @Test fun skippedLogsAlsoPreserveCapturedDeadline() {
        val log = DoseLog("2026-10-15", "a", "Test", "1 ml", "08:00", "skipped", 1_792_046_000_000, 1_792_045_800_000)
        assertEquals(log, DoseLogJson.decode(DoseLogJson.encode(log)))
    }
    @Test fun malformedLogIsRejected() {
        assertNull(DoseLogJson.decode(JSONObject("""{"date":"2026-10-15","medicationId":"a","time":"bad"}""")))
    }
}
