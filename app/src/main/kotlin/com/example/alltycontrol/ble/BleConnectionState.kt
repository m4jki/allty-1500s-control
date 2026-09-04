package com.example.alltycontrol.ble

sealed interface BleConnectionState {
    data object Disconnected : BleConnectionState
    data object Scanning : BleConnectionState
    data object Connecting : BleConnectionState
    data object DiscoveringServices : BleConnectionState
    data class Connected(val deviceName: String, val address: String) : BleConnectionState
    data class Error(val message: String) : BleConnectionState
}

fun BleConnectionState.label(): String = when (this) {
    BleConnectionState.Disconnected -> "Disconnected"
    BleConnectionState.Scanning -> "Scanning"
    BleConnectionState.Connecting -> "Connecting"
    BleConnectionState.DiscoveringServices -> "Discovering services"
    is BleConnectionState.Connected -> "Connected"
    is BleConnectionState.Error -> "Error: $message"
}
