package com.example.alltycontrol.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiscoveredDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
) {
    val isPreferred: Boolean get() = name == BleScanner.PREFERRED_DEVICE_NAME
}

class BleScanner(
    context: Context,
    private val adapter: BluetoothAdapter?,
) {
    companion object {
        const val PREFERRED_DEVICE_NAME = "M1-B3"
    }

    private val appContext = context.applicationContext
    private val _results = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val results: StateFlow<List<DiscoveredDevice>> = _results.asStateFlow()

    private var scanning = false
    private val callback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val item = DiscoveredDevice(
                name = runCatching { result.device.name }.getOrNull() ?: result.scanRecord?.deviceName,
                address = result.device.address,
                rssi = result.rssi,
            )
            val byAddress = _results.value.associateBy { it.address }.toMutableMap()
            byAddress[item.address] = item
            _results.value = byAddress.values.sortedWith(
                compareByDescending<DiscoveredDevice> { it.isPreferred }.thenByDescending { it.rssi },
            )
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(0, it) }
        }
    }

    @SuppressLint("MissingPermission")
    fun start(): Result<Unit> = runCatching {
        check(adapter?.isEnabled == true) { "Bluetooth is turned off" }
        val scanner = checkNotNull(adapter.bluetoothLeScanner) { "BLE scanner is unavailable" }
        _results.value = emptyList()
        scanner.startScan(callback)
        scanning = true
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!scanning) return
        runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        scanning = false
    }
}
