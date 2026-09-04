package com.example.alltycontrol.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor

internal interface AlltyGattEvents {
    fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int)
    fun onServicesDiscovered(gatt: BluetoothGatt, status: Int)
    fun onCharacteristicWrite(characteristic: BluetoothGattCharacteristic, status: Int)
    fun onNotification(characteristic: BluetoothGattCharacteristic, value: ByteArray)
    fun onDescriptorWrite(descriptor: BluetoothGattDescriptor, status: Int)
}

internal class AlltyGattCallback(
    private val events: AlltyGattEvents,
) : BluetoothGattCallback() {
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) =
        events.onConnectionStateChange(gatt, status, newState)

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) =
        events.onServicesDiscovered(gatt, status)

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int,
    ) = events.onCharacteristicWrite(characteristic, status)

    @Deprecated("Used on Android 12 and lower")
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
    ) = events.onNotification(characteristic, characteristic.value?.copyOf() ?: byteArrayOf())

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ) = events.onNotification(characteristic, value.copyOf())

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int,
    ) = events.onDescriptorWrite(descriptor, status)
}
