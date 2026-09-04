package com.example.alltycontrol.ble

import com.example.alltycontrol.domain.ModeType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AlltyProtocolTest {
    @Test
    fun `constant 37 add matches confirmed frame`() {
        assertHexEquals(
            "DE14A20101000000010125000000010125BB0DED",
            AlltyProtocol.buildAddModeFrame(ModeType.CONSTANT, 37),
        )
    }

    @Test
    fun `constant 37 delete matches confirmed frame`() {
        assertHexEquals(
            "DE14A20101000000010125000000010125AA1CED",
            AlltyProtocol.buildDeleteModeFrame(ModeType.CONSTANT, 37),
        )
    }

    @Test
    fun `constant 69 add matches confirmed frame`() {
        assertHexEquals(
            "DE14A20101000000010145000000010145BB0DED",
            AlltyProtocol.buildAddModeFrame(ModeType.CONSTANT, 69),
        )
    }

    @Test
    fun `constant 69 delete matches confirmed frame`() {
        assertHexEquals(
            "DE14A20101000000010145000000010145AA1CED",
            AlltyProtocol.buildDeleteModeFrame(ModeType.CONSTANT, 69),
        )
    }

    @Test
    fun `flash add frames match confirmed examples`() {
        assertHexEquals(
            "DE14A2010100000001032F00000001032FBB0DED",
            AlltyProtocol.buildAddModeFrame(ModeType.FLASH, 47),
        )
        assertHexEquals(
            "DE14A2010100000001034B00000001034BBB0DED",
            AlltyProtocol.buildAddModeFrame(ModeType.FLASH, 75),
        )
    }

    @Test
    fun `sos 51 add matches confirmed frame`() {
        assertHexEquals(
            "DE14A20101000000010233000000010233BB0DED",
            AlltyProtocol.buildAddModeFrame(ModeType.SOS, 51),
        )
    }

    @Test
    fun `light sensor frames match confirmed examples`() {
        assertHexEquals("DE07AF01FF56ED", AlltyProtocol.buildLightSensorFrame(true))
        assertHexEquals("DE07AF0100A9ED", AlltyProtocol.buildLightSensorFrame(false))
    }

    @Test
    fun `motion sensor frames match confirmed examples`() {
        assertHexEquals("DE07AE01FF57ED", AlltyProtocol.buildMotionSensorFrame(true))
        assertHexEquals("DE07AE0100A8ED", AlltyProtocol.buildMotionSensorFrame(false))
    }

    @Test
    fun `telemetry queries match Magicshine protocol frames`() {
        assertHexEquals("DE06A100A7ED", AlltyProtocol.buildTemperatureQueryFrame())
        assertHexEquals("DE06A400A2ED", AlltyProtocol.buildBatteryQueryFrame())
    }

    @Test
    fun `checksum XORs length command and data`() {
        assertEquals(
            0x56.toByte(),
            AlltyProtocol.calculateChecksum(hexToBytes("07AF01FF")),
        )
    }

    @Test
    fun `generic frame builder matches confirmed frame`() {
        assertHexEquals(
            "DE07AF01FF56ED",
            AlltyProtocol.buildFrame(0xAF.toByte(), byteArrayOf(0x01, 0xFF.toByte())),
        )
    }

    @Test
    fun `slot 2 add changes gear number and recalculates checksum`() {
        assertHexEquals(
            "DE14A20102000000010125000000010125BB0EED",
            AlltyProtocol.buildAddModeFrame(ModeType.CONSTANT, 37, slot = 2),
        )
    }

    @Test
    fun `slot 20 delete changes gear number and recalculates checksum`() {
        assertHexEquals(
            "DE14A2011400000001034B00000001034BAA09ED",
            AlltyProtocol.buildDeleteModeFrame(ModeType.FLASH, 75, slot = 20),
        )
    }

    @Test
    fun `brightness outside confirmed range is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AlltyProtocol.buildAddModeFrame(ModeType.CONSTANT, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AlltyProtocol.buildDeleteModeFrame(ModeType.FLASH, 101)
        }
    }

    @Test
    fun `slot outside supported range is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AlltyProtocol.buildAddModeFrame(ModeType.CONSTANT, 50, slot = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AlltyProtocol.buildDeleteModeFrame(ModeType.FLASH, 50, slot = 21)
        }
    }

    @Test
    fun `frame verifier checks delimiters length and checksum`() {
        val valid = hexToBytes("DE07AE01FF57ED")
        assertTrue(AlltyProtocol.isValidFrame(valid))

        val badStart = valid.copyOf().also { it[0] = 0x00 }
        val badLength = valid.copyOf().also { it[1] = 0x06 }
        val badChecksum = valid.copyOf().also { it[it.lastIndex - 1] = 0x00 }
        val badEnd = valid.copyOf().also { it[it.lastIndex] = 0x00 }
        assertFalse(AlltyProtocol.isValidFrame(badStart))
        assertFalse(AlltyProtocol.isValidFrame(badLength))
        assertFalse(AlltyProtocol.isValidFrame(badChecksum))
        assertFalse(AlltyProtocol.isValidFrame(badEnd))
    }

    private fun assertHexEquals(expected: String, actual: ByteArray) {
        assertArrayEquals(hexToBytes(expected), actual)
    }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0)
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
