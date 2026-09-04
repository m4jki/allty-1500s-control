package com.example.alltycontrol

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.alltycontrol.ble.BleConnectionState
import com.example.alltycontrol.ui.AdvancedScreen
import com.example.alltycontrol.ui.DebugScreen
import com.example.alltycontrol.ui.DeviceScanScreen
import com.example.alltycontrol.ui.MainScreen
import com.example.alltycontrol.ui.theme.AlltyTheme
import com.example.alltycontrol.viewmodel.MainViewModel
import kotlinx.coroutines.launch

private enum class AppScreen(val title: String) {
    MAIN("ALLTY 1500S Control"),
    SCAN("Select light"),
    ADVANCED("Advanced"),
    DEBUG("Debug"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AlltyTheme {
                val viewModel: MainViewModel = viewModel()
                AlltyApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun AlltyApp(viewModel: MainViewModel) {
    val connectionState by viewModel.bleManager.connectionState.collectAsStateWithLifecycle()
    val devices by viewModel.bleManager.scanner.results.collectAsStateWithLifecycle()
    val modes by viewModel.modes.collectAsStateWithLifecycle()
    val lastDeviceAddress by viewModel.lastDeviceAddress.collectAsStateWithLifecycle()
    val lightSensorEnabled by viewModel.lightSensorEnabled.collectAsStateWithLifecycle()
    val motionSensorEnabled by viewModel.motionSensorEnabled.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val deviceName by viewModel.bleManager.deviceName.collectAsStateWithLifecycle()
    val deviceAddress by viewModel.bleManager.deviceAddress.collectAsStateWithLifecycle()
    val services by viewModel.bleManager.services.collectAsStateWithLifecycle()
    val characteristics by viewModel.bleManager.characteristics.collectAsStateWithLifecycle()
    val writeCharacteristic by viewModel.bleManager.currentWriteCharacteristic.collectAsStateWithLifecycle()
    val lastTx by viewModel.bleManager.lastTx.collectAsStateWithLifecycle()
    val lastRx by viewModel.bleManager.lastRx.collectAsStateWithLifecycle()
    val protocolLog by viewModel.bleManager.protocolLog.collectAsStateWithLifecycle()
    val batteryPercent by viewModel.bleManager.batteryPercent.collectAsStateWithLifecycle()
    val temperatureCelsius by viewModel.bleManager.temperatureCelsius.collectAsStateWithLifecycle()

    var screen by remember { mutableStateOf(AppScreen.MAIN) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var scanAfterPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (scanAfterPermission && grants.values.all { it }) {
            viewModel.startScan()
        } else if (grants.values.any { !it }) {
            scope.launch { snackbarHostState.showSnackbar("Bluetooth scan permission was not granted") }
        }
        scanAfterPermission = false
    }

    fun beginScan() {
        val permissions = requiredBlePermissions()
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(viewModel.getApplication(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            viewModel.startScan()
        } else {
            scanAfterPermission = true
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    DisposableEffect(screen) {
        onDispose {
            if (screen == AppScreen.SCAN) viewModel.stopScan()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(screen.title) },
                navigationIcon = {
                    if (screen != AppScreen.MAIN) {
                        IconButton(onClick = {
                            if (screen == AppScreen.SCAN) viewModel.stopScan()
                            screen = AppScreen.MAIN
                        }) { Text("‹") }
                    }
                },
            )
        },
    ) { padding ->
        when (screen) {
            AppScreen.MAIN -> MainScreen(
                connectionState = connectionState,
                lastDeviceAddress = lastDeviceAddress,
                modes = modes,
                lightSensorEnabled = lightSensorEnabled,
                motionSensorEnabled = motionSensorEnabled,
                batteryPercent = batteryPercent,
                temperatureCelsius = temperatureCelsius,
                busy = progress != null,
                onChooseDevice = {
                    screen = AppScreen.SCAN
                    beginScan()
                },
                onReconnect = viewModel::reconnect,
                onDisconnect = viewModel::disconnect,
                onRefreshTelemetry = viewModel::refreshTelemetry,
                onAddMode = viewModel::addMode,
                onSyncModes = viewModel::syncModes,
                onDeleteMode = viewModel::deleteMode,
                onLightSensorChanged = viewModel::setLightSensor,
                onMotionSensorChanged = viewModel::setMotionSensor,
                onRestoreFactoryModes = viewModel::restoreFactoryModes,
                onAdvanced = { screen = AppScreen.ADVANCED },
                onDebug = { screen = AppScreen.DEBUG },
                modifier = Modifier.padding(padding),
            )
            AppScreen.SCAN -> DeviceScanScreen(
                devices = devices,
                onRefresh = ::beginScan,
                onConnect = { address ->
                    viewModel.connect(address)
                    screen = AppScreen.MAIN
                },
                modifier = Modifier.padding(padding),
            )
            AppScreen.ADVANCED -> AdvancedScreen(
                connected = connectionState is BleConnectionState.Connected,
                progress = progress,
                onForceClear = { viewModel.forceClearModes() },
                onCancel = viewModel::cancelOperation,
                modifier = Modifier.padding(padding),
            )
            AppScreen.DEBUG -> DebugScreen(
                connectionState = connectionState,
                deviceName = deviceName,
                deviceAddress = deviceAddress,
                services = services,
                characteristics = characteristics,
                currentWriteCharacteristic = writeCharacteristic,
                lastTx = lastTx,
                lastRx = lastRx,
                batteryPercent = batteryPercent,
                temperatureCelsius = temperatureCelsius,
                log = protocolLog,
                onSendRaw = viewModel::sendRawHex,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (progress != null && screen == AppScreen.MAIN) {
        val current = checkNotNull(progress)
        AlertDialog(
            onDismissRequest = {},
            title = { Text(current.title) },
            text = {
                androidx.compose.foundation.layout.Column {
                    LinearProgressIndicator(
                        progress = { current.current.toFloat() / current.total.coerceAtLeast(1) },
                    )
                    Text("${current.current} / ${current.total}")
                }
            },
            confirmButton = {},
        )
    }
}

private fun requiredBlePermissions(): List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
} else {
    listOf(Manifest.permission.ACCESS_FINE_LOCATION)
}
