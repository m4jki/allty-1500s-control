package com.example.alltycontrol.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alltycontrol.AlltyApplication
import com.example.alltycontrol.ble.AlltyProtocol
import com.example.alltycontrol.ble.BleConnectionState
import com.example.alltycontrol.ble.BleWriteResult
import com.example.alltycontrol.ble.errorMessage
import com.example.alltycontrol.ble.parseHex
import com.example.alltycontrol.domain.LightMode
import com.example.alltycontrol.domain.ModeType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class OperationProgress(
    val title: String,
    val current: Int,
    val total: Int,
    val cancellable: Boolean,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AlltyApplication
    val bleManager = app.bleManager
    private val modeRepository = app.modeRepository
    private val settingsRepository = app.settingsRepository

    val modes = modeRepository.modes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val lastDeviceAddress = settingsRepository.lastDeviceAddress.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )
    val lightSensorEnabled = settingsRepository.lightSensorEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false,
    )
    val motionSensorEnabled = settingsRepository.motionSensorEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false,
    )

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()
    private val _progress = MutableStateFlow<OperationProgress?>(null)
    val progress = _progress.asStateFlow()
    private var operationJob: Job? = null

    init {
        viewModelScope.launch {
            bleManager.connectionState.collect { state ->
                if (state is BleConnectionState.Connected) {
                    settingsRepository.setLastDeviceAddress(state.address)
                }
            }
        }
    }

    fun startScan() {
        bleManager.startScan().onFailure { _messages.tryEmit(it.message ?: "Unable to scan") }
    }

    fun stopScan() = bleManager.stopScan()

    fun connect(address: String) = bleManager.connect(address)

    fun reconnect() {
        val address = lastDeviceAddress.value
        if (address == null) _messages.tryEmit("No previously selected light") else connect(address)
    }

    fun disconnect() = bleManager.disconnect()

    fun refreshTelemetry() {
        viewModelScope.launch {
            when (val result = bleManager.refreshTelemetry()) {
                BleWriteResult.Success -> Unit
                else -> _messages.emit(result.errorMessage())
            }
        }
    }

    fun addMode(type: ModeType, brightness: Int) {
        if (operationJob?.isActive == true) return
        operationJob = viewModelScope.launch {
            val current = modeRepository.normalizeSlots()
            if (current.any { it.type == type && it.brightness == brightness }) {
                _messages.emit("Mode already exists")
                return@launch
            }
            if (current.size >= AlltyProtocol.MAX_CUSTOM_MODES) {
                _messages.emit("The light supports up to ${AlltyProtocol.MAX_CUSTOM_MODES} custom modes")
                return@launch
            }
            val slot = (1..AlltyProtocol.MAX_CUSTOM_MODES).first { candidate ->
                current.none { it.slot == candidate }
            }
            val newMode = LightMode(UUID.randomUUID().toString(), type, brightness, slot)
            val desiredModes = current + newMode
            try {
                desiredModes.sortedBy { it.slot }.forEachIndexed { index, mode ->
                    _progress.value = OperationProgress("Saving mode configuration", index, desiredModes.size, false)
                    val result = bleManager.write(
                        AlltyProtocol.buildAddModeFrame(mode.type, mode.brightness, mode.slot),
                        postWriteDelayMillis = 60,
                    )
                    if (result != BleWriteResult.Success) {
                        _messages.emit(result.errorMessage())
                        return@launch
                    }
                    _progress.value = OperationProgress("Saving mode configuration", index + 1, desiredModes.size, false)
                }
                modeRepository.add(newMode)
                _messages.emit("Mode ${newMode.slot} saved to the light and local list")
            } finally {
                _progress.value = null
            }
        }
    }

    fun syncModes() {
        if (operationJob?.isActive == true) return
        operationJob = viewModelScope.launch {
            val current = modeRepository.normalizeSlots()
            if (current.isEmpty()) {
                _messages.emit("There are no local custom modes to synchronize")
                return@launch
            }
            try {
                current.forEachIndexed { index, mode ->
                    _progress.value = OperationProgress("Synchronizing local modes", index, current.size, false)
                    val result = bleManager.write(
                        AlltyProtocol.buildAddModeFrame(mode.type, mode.brightness, mode.slot),
                        postWriteDelayMillis = 60,
                    )
                    if (result != BleWriteResult.Success) {
                        _messages.emit("Synchronization stopped at ${index + 1}: ${result.errorMessage()}")
                        return@launch
                    }
                    _progress.value = OperationProgress("Synchronizing local modes", index + 1, current.size, false)
                }
                _messages.emit("${current.size} local mode(s) synchronized with the light")
            } finally {
                _progress.value = null
            }
        }
    }

    fun deleteMode(mode: LightMode) {
        viewModelScope.launch {
            val normalizedMode = modeRepository.normalizeSlots().firstOrNull { it.id == mode.id } ?: return@launch
            when (val result = bleManager.write(
                AlltyProtocol.buildDeleteModeFrame(
                    normalizedMode.type,
                    normalizedMode.brightness,
                    normalizedMode.slot,
                ),
            )) {
                BleWriteResult.Success -> {
                    val wasLast = modeRepository.modes.first().size == 1
                    modeRepository.remove(mode.id)
                    _messages.emit(
                        if (wasLast) {
                            "Last custom mode removed. ALLTY 1500S should now use its factory modes."
                        } else {
                            "Mode deleted"
                        },
                    )
                }
                else -> _messages.emit(result.errorMessage())
            }
        }
    }

    fun setLightSensor(enabled: Boolean) {
        viewModelScope.launch {
            when (val result = bleManager.write(AlltyProtocol.buildLightSensorFrame(enabled))) {
                BleWriteResult.Success -> settingsRepository.setLightSensorEnabled(enabled)
                else -> _messages.emit(result.errorMessage())
            }
        }
    }

    fun setMotionSensor(enabled: Boolean) {
        viewModelScope.launch {
            when (val result = bleManager.write(AlltyProtocol.buildMotionSensorFrame(enabled))) {
                BleWriteResult.Success -> settingsRepository.setMotionSensorEnabled(enabled)
                else -> _messages.emit(result.errorMessage())
            }
        }
    }

    fun restoreFactoryModes() {
        if (operationJob?.isActive == true) return
        operationJob = viewModelScope.launch {
            val knownModes = modeRepository.normalizeSlots().sortedByDescending { it.slot }
            if (knownModes.isEmpty()) {
                _messages.emit("The app has no locally managed custom modes to delete")
                return@launch
            }
            var failures = 0
            try {
                knownModes.forEachIndexed { index, mode ->
                    _progress.value = OperationProgress("Restoring factory modes", index, knownModes.size, false)
                    val result = bleManager.write(
                        AlltyProtocol.buildDeleteModeFrame(mode.type, mode.brightness, mode.slot),
                        postWriteDelayMillis = 60,
                    )
                    if (result == BleWriteResult.Success) {
                        modeRepository.remove(mode.id)
                    } else {
                        failures++
                    }
                    _progress.value = OperationProgress("Restoring factory modes", index + 1, knownModes.size, false)
                }
                _messages.emit(
                    if (failures == 0) {
                        "All local custom modes deleted. The light should now use its factory modes."
                    } else {
                        "$failures mode(s) could not be deleted and remain in the local list"
                    },
                )
            } finally {
                _progress.value = null
            }
        }
    }

    fun forceClearModes(delayMillis: Long = 60L) {
        if (operationJob?.isActive == true) return
        operationJob = viewModelScope.launch {
            val total = ModeType.entries.size * 100
            var completed = 0
            var failure: BleWriteResult? = null
            try {
                loop@ for (type in ModeType.entries) {
                    for (brightness in 1..100) {
                        _progress.value = OperationProgress("Clearing modes", completed, total, true)
                        val result = bleManager.write(
                            AlltyProtocol.buildDeleteModeFrame(type, brightness),
                            postWriteDelayMillis = delayMillis,
                        )
                        if (result != BleWriteResult.Success) {
                            failure = result
                            break@loop
                        }
                        completed++
                        _progress.value = OperationProgress("Clearing modes", completed, total, true)
                    }
                }
                if (failure == null && completed == total) {
                    modeRepository.clear()
                    _messages.emit("All 300 delete commands completed; local custom modes cleared")
                } else {
                    _messages.emit("Force clear stopped at $completed / $total: ${failure?.errorMessage()}")
                }
            } catch (_: CancellationException) {
                _messages.emit("Force clear cancelled at $completed / $total")
                throw CancellationException()
            } finally {
                _progress.value = null
            }
        }
    }

    fun cancelOperation() {
        operationJob?.cancel()
    }

    fun sendRawHex(input: String) {
        val parsed = parseHex(input)
        if (parsed.isFailure) {
            _messages.tryEmit(parsed.exceptionOrNull()?.message ?: "Invalid hexadecimal input")
            return
        }
        viewModelScope.launch {
            when (val result = bleManager.write(parsed.getOrThrow())) {
                BleWriteResult.Success -> _messages.emit("Raw frame written")
                else -> _messages.emit(result.errorMessage())
            }
        }
    }
}
