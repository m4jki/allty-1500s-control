package com.example.alltycontrol.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BleTelemetryParserTest {
    @Test
    fun `battery level parses unsigned percentage`() {
        assertEquals(0, BleTelemetryParser.parseBatteryLevel(byteArrayOf(0x00)))
        assertEquals(100, BleTelemetryParser.parseBatteryLevel(byteArrayOf(0x64)))
    }

    @Test
    fun `battery level rejects missing and out of range values`() {
        assertNull(BleTelemetryParser.parseBatteryLevel(byteArrayOf()))
        assertNull(BleTelemetryParser.parseBatteryLevel(byteArrayOf(0x65)))
        assertNull(BleTelemetryParser.parseBatteryLevel(byteArrayOf(0xFF.toByte())))
    }

    @Test
    fun `temperature parses signed little endian hundredths of celsius`() {
        assertEquals(25.0f, requireNotNull(BleTelemetryParser.parseTemperatureCelsius(byteArrayOf(0xC4.toByte(), 0x09))), 0.001f)
        assertEquals(-10.0f, requireNotNull(BleTelemetryParser.parseTemperatureCelsius(byteArrayOf(0x18, 0xFC.toByte()))), 0.001f)
    }

    @Test
    fun `temperature rejects incomplete value`() {
        assertNull(BleTelemetryParser.parseTemperatureCelsius(byteArrayOf()))
        assertNull(BleTelemetryParser.parseTemperatureCelsius(byteArrayOf(0x01)))
    }

    @Test
    fun `ALLTY B4 notification exposes battery percentage`() {
        assertEquals(
            AlltyTelemetryReading(batteryPercent = 100),
            BleTelemetryParser.parseAlltyNotification(hexToBytes("DE0BB4000000000064DBED")),
        )
    }

    @Test
    fun `ALLTY B1 notification exposes signed temperature`() {
        assertEquals(
            AlltyTelemetryReading(temperatureCelsius = 37),
            BleTelemetryParser.parseAlltyNotification(hexToBytes("DE0DB100000000002501059DED")),
        )
        assertEquals(
            AlltyTelemetryReading(temperatureCelsius = -10),
            BleTelemetryParser.parseAlltyNotification(hexToBytes("DE0DB100000000000A0005B3ED")),
        )
    }

    @Test
    fun `ALLTY telemetry rejects invalid frame and impossible battery`() {
        assertNull(BleTelemetryParser.parseAlltyNotification(hexToBytes("DE0BB4000000000064DAED")))
        assertNull(BleTelemetryParser.parseAlltyNotification(hexToBytes("DE0BB4000000000065DAED")))
    }

    private fun hexToBytes(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
