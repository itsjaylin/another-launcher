package com.anotherlauncher

import androidx.compose.ui.graphics.toArgb
import com.anotherlauncher.ui.ColorPickerDefaults
import org.junit.Assert.*
import org.junit.Test

class ColorPickerTest {

    @Test
    fun parseHexValidInputs() {
        assertEquals(0xFFFFFFFF.toInt(), ColorPickerDefaults.parseHex("#FFFFFF"))
        assertEquals(0xFFFFFFFF.toInt(), ColorPickerDefaults.parseHex("FFFFFF"))
        assertEquals(0xFF000000.toInt(), ColorPickerDefaults.parseHex("#000000"))
        assertEquals(0xFF38BDF8.toInt(), ColorPickerDefaults.parseHex("#38BDF8"))
        assertEquals(0x80123456.toInt(), ColorPickerDefaults.parseHex("#80123456"))
    }

    @Test
    fun parseHexInvalidInputsReturnNull() {
        assertNull(ColorPickerDefaults.parseHex(""))
        assertNull(ColorPickerDefaults.parseHex("#"))
        assertNull(ColorPickerDefaults.parseHex("123"))
        assertNull(ColorPickerDefaults.parseHex("#12345"))
        assertNull(ColorPickerDefaults.parseHex("#1234567"))
        assertNull(ColorPickerDefaults.parseHex("#GGGGGG"))
        assertNull(ColorPickerDefaults.parseHex("NOT_A_COLOR"))
    }

    @Test
    fun formatHexOutputsProperFormat() {
        assertEquals("", ColorPickerDefaults.formatHex(null))
        assertEquals("#FFFFFF", ColorPickerDefaults.formatHex(0xFFFFFFFF.toInt()))
        assertEquals("#000000", ColorPickerDefaults.formatHex(0xFF000000.toInt()))
        assertEquals("#38BDF8", ColorPickerDefaults.formatHex(0xFF38BDF8.toInt()))
        assertEquals("#80123456", ColorPickerDefaults.formatHex(0x80123456.toInt()))
    }

    @Test
    fun presetsContainAutoAndValidColors() {
        val presets = ColorPickerDefaults.presets
        assertTrue(presets.isNotEmpty())
        assertEquals("Auto", presets.first().name)
        assertNull(presets.first().colorInt)

        presets.drop(1).forEach { preset ->
            assertNotNull(preset.colorInt)
            // Color has 100% alpha (0xFF000000)
            assertEquals(0xFF000000.toInt(), preset.colorInt!! and 0xFF000000.toInt())
        }
    }

    @Test
    fun hsvConversionRoundtrip() {
        val testColors = listOf(
            0xFFFF0000.toInt(), // Red
            0xFF00FF00.toInt(), // Green
            0xFF0000FF.toInt(), // Blue
            0xFFFFFFFF.toInt(), // White
            0xFF000000.toInt(), // Black
            0xFF38BDF8.toInt(), // Cyan
        )
        for (color in testColors) {
            val hsv = ColorPickerDefaults.colorToHsv(color)
            val reconstructed = ColorPickerDefaults.hsvToColor(hsv[0], hsv[1], hsv[2]).toArgb()
            assertEquals(color, reconstructed)
        }
    }
}
