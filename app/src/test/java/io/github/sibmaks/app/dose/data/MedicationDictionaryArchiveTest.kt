package io.github.sibmaks.app.dose.data

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class MedicationDictionaryArchiveTest {
    @Test fun `generated archive preserves every byte of the editable source and all catalog entries`() {
        val raw = MedicationDictionaryTestAsset.source.readBytes()
        val archived = MedicationDictionaryTestAsset.compressed.readBytes()
        assertArrayEquals(raw, GZIPInputStream(archived.inputStream()).use { it.readBytes() })
        assertTrue("Archive should be substantially smaller than the editable source", archived.size < raw.size / 4)
        assertEquals(MedicationDictionary.fromJson(raw.toString(Charsets.UTF_8)).entries, MedicationDictionaryTestAsset.load().entries)
    }

    @Test fun `compressed catalog uses the same strict JSON validation`() {
        val invalid = """{"duplicate":{"localizations":{"en":["One"]},"forms":["PILL"]},"duplicate":{"localizations":{"en":["Two"]},"forms":["PILL"]}}"""
        val bytes = ByteArrayOutputStream().also { output -> GZIPOutputStream(output).use { it.write(invalid.toByteArray()) } }.toByteArray()
        assertTrue(runCatching { MedicationDictionary.fromGzip(bytes.inputStream()) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun `archive stream is closed on success corrupt data and truncated gzip`() {
        class Tracked(bytes: ByteArray) : ByteArrayInputStream(bytes) {
            var closed = false
            override fun close() { closed = true; super.close() }
        }
        val valid = MedicationDictionaryTestAsset.compressed.readBytes()
        val input = Tracked(valid)
        assertTrue(MedicationDictionary.fromGzip(input).entries.isNotEmpty())
        assertTrue(input.closed)
        for (data in listOf(byteArrayOf(1, 2, 3), valid.copyOf(valid.size / 2))) {
            val broken = Tracked(data)
            assertTrue(runCatching { MedicationDictionary.fromGzip(broken) }.isFailure)
            assertTrue(broken.closed)
        }
    }
}
