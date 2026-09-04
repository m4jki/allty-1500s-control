package com.example.alltycontrol.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class AlltyBleManager(context: Context) : AlltyGattEvents {
    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        private const val WRITE_TIMEOUT_MILLIS = 5_000L
        private const val TELEMETRY_REFRESH_MILLIS = 60_000L
        private const val TELEMETRY_QUERY_DELAY_MILLIS = 250L
        private const val BATTERY_RESPONSE_WAIT_MILLIS = 750L
        private const val MAX_LOG_LINES = 200
    }

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val callback = AlltyGattCallback(this)

    val scanner = BleScanner(appContext, adapter)

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()
    private val _deviceAddress = MutableStateFlow<String?>(null)
    val deviceAddress: StateFlow<String?> = _deviceAddress.asStateFlow()
    private val _services = MutableStateFlow<List<String>>(emptyList())
    val services: StateFlow<List<String>> = _services.asStateFlow()
    private val _characteristics = MutableStateFlow<List<String>>(emptyList())
    val characteristics: StateFlow<List<String>> = _characteristics.asStateFlow()
    private val _currentWriteCharacteristic = MutableStateFlow<String?>(null)
    val currentWriteCharacteristic: StateFlow<String?> = _currentWriteCharacteristic.asStateFlow()
    private val _lastTx = MutableStateFlow<String?>(null)
    val lastTx: StateFlow<String?> = _lastTx.asStateFlow()
    private val _lastRx = MutableStateFlow<String?>(null)
    val lastRx: StateFlow<String?> = _lastRx.asStateFlow()
    private val _protocolLog = MutableStateFlow<List<String>>(emptyList())
    val protocolLog: StateFlow<List<String>> = _protocolLog.asStateFlow()
    private val _batteryPercent = MutableStateFlow<Int?>(null)
    val batteryPercent: StateFlow<Int?> = _batteryPercent.asStateFlow()
    private val _temperatureCelsius = MutableStateFlow<Int?>(null)
    val temperatureCelsius: StateFlow<Int?> = _temperatureCelsius.asStateFlow()

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var writeCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var pendingWrite: CompletableDeferred<Int>? = null
    @Volatile private var pendingNotificationSetup = false
    @Volatile private var telemetryJob: Job? = null
    private val batteryResponseCount = AtomicLong(0)

    private val writeQueue = BleWriteQueue(scope, ::performWrite)

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED &&
                intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) != BluetoothAdapter.STATE_ON
            ) {
                scanner.stop()
                closeGatt()
                _connectionState.value = BleConnectionState.Error("Bluetooth is turned off")
            }
        }
    }

    init {
        appContext.registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    }

    fun startScan(): Result<Unit> {
        _connectionState.value = BleConnectionState.Scanning
        return scanner.start().onFailure {
            _connectionState.value = BleConnectionState.Error(it.message ?: "Unable to scan")
        }
    }

    fun stopScan() {
        scanner.stop()
        if (_connectionState.value == BleConnectionState.Scanning) {
            _connectionState.value = BleConnectionState.Disconnected
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        scanner.stop()
        closeGatt()
        _batteryPercent.value = null
        _temperatureCelsius.value = null
        _connectionState.value = BleConnectionState.Connecting
        runCatching {
            val bluetoothAdapter = checkNotNull(adapter) { "Bluetooth is unavailable" }
            check(bluetoothAdapter.isEnabled) { "Bluetooth is turned off" }
            val device = bluetoothAdapter.getRemoteDevice(address)
            _deviceAddress.value = device.address
            _deviceName.value = runCatching { device.name }.getOrNull()
            gatt = device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
            checkNotNull(gatt) { "Unable to start the GATT connection" }
        }.onFailure {
            closeGatt()
            _connectionState.value = BleConnectionState.Error(it.message ?: "Connection failed")
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        scanner.stop()
        runCatching { gatt?.disconnect() }
        closeGatt()
        _connectionState.value = BleConnectionState.Disconnected
    }

    suspend fun write(frame: ByteArray, postWriteDelayMillis: Long = 0): BleWriteResult =
        writeQueue.enqueue(frame, postWriteDelayMillis)

    suspend fun refreshTelemetry(): BleWriteResult {
        val batteryResponsesBeforeQuery = batteryResponseCount.get()
        val temperatureResult = write(
            AlltyProtocol.buildTemperatureQueryFrame(),
            TELEMETRY_QUERY_DELAY_MILLIS,
        )
        if (temperatureResult != BleWriteResult.Success) return temperatureResult
        val batteryResult = write(AlltyProtocol.buildBatteryQueryFrame())
        if (batteryResult != BleWriteResult.Success) return batteryResult

        delay(BATTERY_RESPONSE_WAIT_MILLIS)
        if (
            batteryResponseCount.get() == batteryResponsesBeforeQuery &&
            _connectionState.value is BleConnectionState.Connected
        ) {
            log("No B4 response; retrying battery query")
            return write(AlltyProtocol.buildBatteryQueryFrame())
        }
        return BleWriteResult.Success
    }

    @SuppressLint("MissingPermission")
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            log("GATT connection error $status")
            closeGatt(gatt)
            _connectionState.value = BleConnectionState.Error("GATT connection error $status")
            return
        }
        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                this.gatt = gatt
                _deviceAddress.value = gatt.device.address
                _deviceName.value = runCatching { gatt.device.name }.getOrNull()
                _connectionState.value = BleConnectionState.DiscoveringServices
                if (!gatt.discoverServices()) {
                    _connectionState.value = BleConnectionState.Error("Service discovery could not start")
                }
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                closeGatt(gatt)
                _connectionState.value = BleConnectionState.Disconnected
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            _connectionState.value = BleConnectionState.Error("Service discovery failed ($status)")
            return
        }
        _services.value = gatt.services.map { it.uuid.toString().uppercase() }
        _characteristics.value = gatt.services.flatMap { service ->
            service.characteristics.map { "${service.uuid.toString().uppercase()} / ${it.uuid.toString().uppercase()}" }
        }

        val characteristic = gatt.getService(SERVICE_UUID)?.getCharacteristic(CHARACTERISTIC_UUID)
        if (characteristic == null) {
            writeCharacteristic = null
            _currentWriteCharacteristic.value = null
            _connectionState.value = BleConnectionState.Error("Required FFE1 / FFE0 characteristic not found")
            return
        }
        writeCharacteristic = characteristic
        _currentWriteCharacteristic.value = characteristic.uuid.toString().uppercase()
        if (!enableNotifications(gatt, characteristic)) markConnected(gatt)
    }

    private fun markConnected(gatt: BluetoothGatt) {
        val name = _deviceName.value ?: BleScanner.PREFERRED_DEVICE_NAME
        val address = _deviceAddress.value ?: gatt.device.address
        _connectionState.value = BleConnectionState.Connected(name, address)
        log("Connected to $name ($address)")
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            while (_connectionState.value is BleConnectionState.Connected) {
                val result = refreshTelemetry()
                if (result != BleWriteResult.Success) {
                    log("Telemetry query failed: ${result.errorMessage()}")
                }
                delay(TELEMETRY_REFRESH_MILLIS)
            }
        }
    }

    override fun onCharacteristicWrite(characteristic: BluetoothGattCharacteristic, status: Int) {
        if (characteristic.uuid == CHARACTERISTIC_UUID) pendingWrite?.complete(status)
    }

    override fun onNotification(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        val hex = value.toHex(" ")
        _lastRx.value = hex
        log("RX  $hex")
        BleTelemetryParser.parseAlltyNotification(value)?.let { reading ->
            reading.batteryPercent?.let {
                _batteryPercent.value = it
                batteryResponseCount.incrementAndGet()
                log("Battery: $it%")
            }
            reading.temperatureCelsius?.let {
                _temperatureCelsius.value = it
                log("Temperature: $it °C")
            }
        }
    }

    override fun onDescriptorWrite(descriptor: BluetoothGattDescriptor, status: Int) {
        log(if (status == BluetoothGatt.GATT_SUCCESS) "Notifications enabled" else "Notification setup failed ($status)")
        if (descriptor.uuid == CCCD_UUID && pendingNotificationSetup) {
            pendingNotificationSetup = false
            gatt?.let(::markConnected)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic): Boolean {
        val supportsNotifications =
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        if (!supportsNotifications || !gatt.setCharacteristicNotification(characteristic, true)) {
            log("FFE0 does not expose usable notifications")
            return false
        }
        val descriptor = characteristic.getDescriptor(CCCD_UUID) ?: run {
            log("Notification descriptor is absent")
            return false
        }
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(
                descriptor,
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
        if (!started) {
            log("Notification descriptor write could not start")
            return false
        }
        pendingNotificationSetup = true
        scope.launch {
            delay(WRITE_TIMEOUT_MILLIS)
            if (pendingNotificationSetup) {
                pendingNotificationSetup = false
                log("Notification setup timed out; continuing without confirmation")
                this@AlltyBleManager.gatt?.let(::markConnected)
            }
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private suspend fun performWrite(frame: ByteArray): BleWriteResult {
        if (_connectionState.value !is BleConnectionState.Connected) return BleWriteResult.NotConnected
        val currentGatt = gatt ?: return BleWriteResult.NotConnected
        val characteristic = writeCharacteristic ?: return BleWriteResult.CharacteristicUnavailable
        val completion = CompletableDeferred<Int>()
        pendingWrite = completion
        val hex = frame.toHex(" ")
        _lastTx.value = hex
        log("TX  $hex")

        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            currentGatt.writeCharacteristic(
                characteristic,
                frame,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            characteristic.value = frame
            @Suppress("DEPRECATION")
            currentGatt.writeCharacteristic(characteristic)
        }
        if (!started) {
            if (pendingWrite === completion) pendingWrite = null
            return BleWriteResult.GattError(-1)
        }

        return try {
            val status = withTimeout(WRITE_TIMEOUT_MILLIS) { completion.await() }
            if (status == BluetoothGatt.GATT_SUCCESS) BleWriteResult.Success else BleWriteResult.GattError(status)
        } catch (_: TimeoutCancellationException) {
            BleWriteResult.Timeout
        } finally {
            if (pendingWrite === completion) pendingWrite = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt(target: BluetoothGatt? = gatt) {
        telemetryJob?.cancel()
        telemetryJob = null
        pendingWrite?.complete(Int.MIN_VALUE)
        pendingWrite = null
        pendingNotificationSetup = false
        if (target != null) runCatching { target.close() }
        if (gatt === target) gatt = null
        writeCharacteristic = null
        _currentWriteCharacteristic.value = null
    }

    private fun log(message: String) {
        val timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        _protocolLog.value = (_protocolLog.value + "$timestamp  $message").takeLast(MAX_LOG_LINES)
    }
}
