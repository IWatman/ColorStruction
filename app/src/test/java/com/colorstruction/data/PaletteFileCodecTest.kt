package com.colorstruction.data

import com.colorstruction.color.Rgb
import com.colorstruction.mixing.PaletteType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** JVM tests for [PaletteFileCodec] and [suggestedFileName]. Needs Robolectric for `org.json`. */
@RunWith(RobolectricTestRunner::class)
class PaletteFileCodecTest {

    private val white = Rgb.parseHex("#F5F3EF")!!
    private val blue = Rgb.parseHex("#1234AB")!!

    private fun reasonOf(text: String): PaletteFileException.Reason {
        try {
            PaletteFileCodec.decode(text)
        } catch (e: PaletteFileException) {
            return e.reason
        }
        throw AssertionError("Expected a PaletteFileException")
    }

    private fun json(
        vararg paints: String,
        name: String = "Test",
        type: String = "OPAQUE",
        version: Int = 1,
    ): String =
        """{"format":"colorstruction-palette","version":$version,"name":"$name",""" +
            """"type":"$type","paints":[${paints.joinToString(",")}]}"""

    private fun paint(
        name: String = "Paint",
        color: String = "#FFFFFF",
        strength: String = "1.0",
        note: String = "null",
    ): String = """{"name":"$name","color":"$color","tintingStrength":$strength,"note":$note}"""

    @Test
    fun roundTripKeepsEveryField() {
        val data = PaletteFileData(
            name = "Basic Acrylic",
            type = PaletteType.WATERCOLOR,
            paints = listOf(
                PaintFileData("Titanium White", white, 1.0, null),
                PaintFileData("Ultramarine", blue, 0.75, "cool blue"),
            ),
        )

        val decoded = PaletteFileCodec.decode(PaletteFileCodec.encode(data))

        assertEquals(data, decoded)
        assertNull(decoded.paints.first().note)
    }

    @Test
    fun encodeIsPrettyPrintedAndMarked() {
        val text = PaletteFileCodec.encode(PaletteFileData("P", PaletteType.OPAQUE, emptyList()))

        assertTrue(text, text.contains("colorstruction-palette"))
        assertTrue(text, text.contains("\n  \"format\""))
        assertTrue(text, text.contains("\"version\": 1"))
    }

    @Test
    fun rejectsNotJsonAndOtherJson() {
        assertEquals(PaletteFileException.Reason.NOT_A_PALETTE, reasonOf("not json at all"))
        assertEquals(PaletteFileException.Reason.NOT_A_PALETTE, reasonOf("""{"hello":1}"""))
        assertEquals(PaletteFileException.Reason.NOT_A_PALETTE, reasonOf("""[{"format":"x"}]"""))
    }

    @Test
    fun rejectsNewerVersionAndBadFields() {
        assertEquals(
            PaletteFileException.Reason.UNSUPPORTED_VERSION,
            reasonOf(json(paint(), version = 2)),
        )
        assertEquals(PaletteFileException.Reason.INVALID, reasonOf(json(paint(color = "#12"))))
        assertEquals(PaletteFileException.Reason.INVALID, reasonOf(json(paint(), name = "   ")))
        assertEquals(PaletteFileException.Reason.INVALID, reasonOf(json(paint(name = ""))))
        assertEquals(
            PaletteFileException.Reason.INVALID,
            reasonOf("""{"format":"colorstruction-palette","version":1,"name":"A"}"""),
        )
    }

    @Test
    fun rejectsOversizedFiles() {
        val big = JSONArray()
        repeat(PaletteFileCodec.MAX_PAINTS + 1) { big.put(JSONObject(paint(name = "p$it"))) }
        val manyPaints = JSONObject()
            .put("format", PaletteFileCodec.FORMAT)
            .put("version", 1)
            .put("name", "Big")
            .put("paints", big)
            .toString()

        assertEquals(PaletteFileException.Reason.TOO_LARGE, reasonOf(manyPaints))
        assertEquals(
            PaletteFileException.Reason.TOO_LARGE,
            reasonOf("x".repeat(PaletteFileCodec.MAX_TEXT_LENGTH + 1)),
        )
    }

    @Test
    fun clampsStrengthAndFallsBackToDefaults() {
        val decoded = PaletteFileCodec.decode(
            json(
                paint(name = "high", strength = "9.0"),
                paint(name = "low", strength = "0.01"),
                """{"name":"missing","color":"#FFFFFF"}""",
                paint(name = "broken", strength = "\"abc\""),
            ),
        )

        assertEquals(listOf(2.0, 0.25, 1.0, 1.0), decoded.paints.map { it.tintingStrength })
    }

    @Test
    fun unknownTypeBecomesOpaqueAndZeroPaintsAreAllowed() {
        val decoded = PaletteFileCodec.decode(json(type = "OIL"))

        assertEquals(PaletteType.OPAQUE, decoded.type)
        assertEquals("Test", decoded.name)
        assertTrue(decoded.paints.isEmpty())
    }

    @Test
    fun truncatesLongNamesAndNotes() {
        val decoded = PaletteFileCodec.decode(
            json(paint(name = "n".repeat(200), note = "\"${"m".repeat(700)}\""), name = "P".repeat(200)),
        )

        assertEquals(PaletteFileCodec.MAX_NAME_LENGTH, decoded.name.length)
        assertEquals(PaletteFileCodec.MAX_NAME_LENGTH, decoded.paints.single().name.length)
        assertEquals(PaletteFileCodec.MAX_NOTE_LENGTH, decoded.paints.single().note?.length)
    }

    @Test
    fun suggestedFileNameSanitizes() {
        assertEquals("Моя палитра.colorstruction.json", suggestedFileName("Моя палитра"))
        assertEquals("a_b_c.colorstruction.json", suggestedFileName("a/b:c"))
        assertEquals("my_palette-1.colorstruction.json", suggestedFileName("  my_palette-1  "))
        assertEquals("palette.colorstruction.json", suggestedFileName("   "))
        assertEquals("___.colorstruction.json", suggestedFileName("///"))
        assertEquals(60 + ".colorstruction.json".length, suggestedFileName("x".repeat(200)).length)
    }
}
