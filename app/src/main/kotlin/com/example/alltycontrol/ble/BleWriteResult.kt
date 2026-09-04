package com.example.alltycontrol.ble

sealed interface BleWriteResult {
    data object Success : BleWriteResult
    data object NotConnected : BleWriteResult
    data object CharacteristicUnavailable : BleWriteResult
    data class GattError(val status: Int) : BleWriteResult
    data object Timeout : BleWriteResult
}

fun BleWriteResult.errorMessage(): String = when (this) {
    BleWriteResult.Success -> "Success"
    BleWriteResult.NotConnected -> "The light is not connected"
    BleWriteResult.CharacteristicUnavailable -> "The ALLTY write characteristic is unavailable"
    is BleWriteResult.GattError -> "Bluetooth write failed (GATT status $status)"
    BleWriteResult.Timeout -> "Bluetooth write timed out"
}
