package com.example.alltycontrol.ble

data class AlltyTelemetryReading(
    val batteryPercent: Int? = null,
    val temperatureCelsius: Int? = null,
)

object BleTelemetryParser {
    private const val TEMPERATURE_RESPONSE = 0xB1
    private const val BATTERY_RESPONSE = 0xB4

    /** Standard Bluetooth Battery Level characteristic (2A19). */
    fun parseBatteryLevel(value: ByteArray): Int? =
        value.firstOrNull()?.toInt()?.and(0xFF)?.takeIf { it in 0..100 }

    /** Standard Bluetooth Temperature characteristic (2A6E). */
    fun parseTemperatureCelsius(value: ByteArray): Float? {
        if (value.size < 2) return null
        val raw = ((value[1].toInt() shl 8) or (value[0].toInt() and 0xFF)).toShort()
        return raw.toInt() / 100f
    }

    /** Parses the confirmed vendor B1/B4 notification layout used by Magicshine. */
    fun parseAlltyNotification(frame: ByteArray): AlltyTelemetryReading? {
        if (!AlltyProtocol.isValidFrame(frame)) return null
        return when (frame[2].toInt() and 0xFF) {
            BATTERY_RESPONSE -> {
                if (frame.size < 11) return null
                val battery = frame[8].toInt() and 0xFF
                battery.takeIf { it in 0..100 }?.let { AlltyTelemetryReading(batteryPercent = it) }
            }
            TEMPERATURE_RESPONSE -> {
                if (frame.size < 13) return null
                val magnitude = frame[8].toInt() and 0xFF
                val temperature = if (frame[9].toInt() == 0) -magnitude else magnitude
                AlltyTelemetryReading(temperatureCelsius = temperature)
            }
            else -> null
        }
    }
}
