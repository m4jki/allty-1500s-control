package com.example.alltycontrol.ble

import com.example.alltycontrol.domain.ModeType

/** Pure Kotlin encoder and validator for confirmed ALLTY 1500S protocol frames. */
object AlltyProtocol {
    const val MAX_CUSTOM_MODES = 20

    private val START: Byte = 0xDE.toByte()
    private val END: Byte = 0xED.toByte()
    private val CUSTOM_MODE_COMMAND: Byte = 0xA2.toByte()
    private val TEMPERATURE_QUERY_COMMAND: Byte = 0xA1.toByte()
    private val BATTERY_QUERY_COMMAND: Byte = 0xA4.toByte()
    private val LIGHT_SENSOR_COMMAND: Byte = 0xAF.toByte()
    private val MOTION_SENSOR_COMMAND: Byte = 0xAE.toByte()
    private val ADD_OPERATION: Byte = 0xBB.toByte()
    private val DELETE_OPERATION: Byte = 0xAA.toByte()

    /** XORs exactly the supplied bytes. Callers pass LENGTH through the final DATA byte. */
    fun calculateChecksum(data: ByteArray): Byte =
        data.fold(0) { checksum, value -> checksum xor value.toUByte().toInt() }.toByte()

    /**
     * Builds DE | LENGTH | COMMAND | DATA | CHECKSUM | ED.
     * LENGTH is the complete frame size, as shown by the confirmed device frames.
     */
    fun buildFrame(command: Byte, data: ByteArray): ByteArray {
        val frameLength = data.size + 5
        require(frameLength <= UByte.MAX_VALUE.toInt()) {
            "Frame is too long: $frameLength bytes"
        }

        val length = frameLength.toByte()
        val checksumInput = byteArrayOf(length, command) + data
        return byteArrayOf(START) +
            checksumInput +
            byteArrayOf(calculateChecksum(checksumInput), END)
    }

    fun buildAddModeFrame(type: ModeType, brightness: Int, slot: Int = 1): ByteArray =
        buildModeFrame(type, brightness, slot, ADD_OPERATION)

    fun buildDeleteModeFrame(type: ModeType, brightness: Int, slot: Int = 1): ByteArray =
        buildModeFrame(type, brightness, slot, DELETE_OPERATION)

    fun buildLightSensorFrame(enabled: Boolean): ByteArray =
        buildFrame(LIGHT_SENSOR_COMMAND, byteArrayOf(0x01, enabled.protocolFlag()))

    fun buildMotionSensorFrame(enabled: Boolean): ByteArray =
        buildFrame(MOTION_SENSOR_COMMAND, byteArrayOf(0x01, enabled.protocolFlag()))

    /** Query used by the Magicshine app; the light answers with command B1. */
    fun buildTemperatureQueryFrame(): ByteArray =
        buildFrame(TEMPERATURE_QUERY_COMMAND, byteArrayOf(0x00))

    /** Query used by the Magicshine app; the light answers with command B4. */
    fun buildBatteryQueryFrame(): ByteArray =
        buildFrame(BATTERY_QUERY_COMMAND, byteArrayOf(0x00))

    /** Checks framing, declared length, and checksum without interpreting the command. */
    fun isValidFrame(frame: ByteArray): Boolean {
        if (frame.size < 5 || frame.first() != START || frame.last() != END) return false
        if (frame[1].toUByte().toInt() != frame.size) return false

        val expectedChecksum = calculateChecksum(frame.copyOfRange(1, frame.lastIndex - 1))
        return frame[frame.lastIndex - 1] == expectedChecksum
    }

    private fun buildModeFrame(type: ModeType, brightness: Int, slot: Int, operation: Byte): ByteArray {
        require(brightness in 1..100) { "Brightness must be in the range 1..100" }
        require(slot in 1..MAX_CUSTOM_MODES) { "Mode slot must be in the range 1..$MAX_CUSTOM_MODES" }
        val value = brightness.toByte()
        val typeValue = type.protocolValue
        val data = byteArrayOf(
            0x01, slot.toByte(), 0x00, 0x00, 0x00,
            0x01, typeValue, value,
            0x00, 0x00, 0x00,
            0x01, typeValue, value,
            operation,
        )
        return buildFrame(CUSTOM_MODE_COMMAND, data)
    }

    private fun Boolean.protocolFlag(): Byte = if (this) 0xFF.toByte() else 0x00
}
