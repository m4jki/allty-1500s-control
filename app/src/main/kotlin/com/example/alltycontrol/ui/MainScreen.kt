package com.example.alltycontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.alltycontrol.ble.BleConnectionState
import com.example.alltycontrol.ble.label
import com.example.alltycontrol.domain.LightMode
import com.example.alltycontrol.domain.ModeType

@Composable
fun MainScreen(
    connectionState: BleConnectionState,
    lastDeviceAddress: String?,
    modes: List<LightMode>,
    lightSensorEnabled: Boolean,
    motionSensorEnabled: Boolean,
    batteryPercent: Int?,
    temperatureCelsius: Int?,
    busy: Boolean,
    onChooseDevice: () -> Unit,
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefreshTelemetry: () -> Unit,
    onAddMode: (ModeType, Int) -> Unit,
    onSyncModes: () -> Unit,
    onDeleteMode: (LightMode) -> Unit,
    onLightSensorChanged: (Boolean) -> Unit,
    onMotionSensorChanged: (Boolean) -> Unit,
    onRestoreFactoryModes: () -> Unit,
    onAdvanced: () -> Unit,
    onDebug: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = connectionState is BleConnectionState.Connected
    var showAddDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ConnectionCard(
            connectionState = connectionState,
            lastDeviceAddress = lastDeviceAddress,
            batteryPercent = batteryPercent,
            temperatureCelsius = temperatureCelsius,
            onChooseDevice = onChooseDevice,
            onReconnect = onReconnect,
            onDisconnect = onDisconnect,
            onRefreshTelemetry = onRefreshTelemetry,
        )

        Text("Custom modes", style = MaterialTheme.typography.headlineSmall)
        Text(
            "This is the app's locally managed configuration; the lamp does not provide a confirmed mode-list readback.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (modes.isEmpty()) {
            Text("No locally managed custom modes")
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    modes.forEachIndexed { index, mode ->
                        ModeRow(mode, enabled = connected && !busy, onDelete = { onDeleteMode(mode) })
                        if (index != modes.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
        Button(
            onClick = { showAddDialog = true },
            enabled = connected && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("+ Add mode")
        }
        OutlinedButton(
            onClick = onSyncModes,
            enabled = connected && modes.isNotEmpty() && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Synchronize local modes with light")
        }

        Text("Sensors", style = MaterialTheme.typography.headlineSmall)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SettingSwitch(
                    title = "Light sensor",
                    checked = lightSensorEnabled,
                    enabled = connected && !busy,
                    onCheckedChange = onLightSensorChanged,
                )
                HorizontalDivider()
                SettingSwitch(
                    title = "Motion detection",
                    checked = motionSensorEnabled,
                    enabled = connected && !busy,
                    onCheckedChange = onMotionSensorChanged,
                )
            }
        }

        OutlinedButton(
            onClick = { showRestoreDialog = true },
            enabled = connected && modes.isNotEmpty() && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Restore factory modes")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onAdvanced, modifier = Modifier.weight(1f)) { Text("Advanced") }
            OutlinedButton(onClick = onDebug, modifier = Modifier.weight(1f)) { Text("Debug") }
        }
        Spacer(Modifier.height(12.dp))
    }

    if (showAddDialog) {
        AddModeDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { type, brightness ->
                onAddMode(type, brightness)
                showAddDialog = false
            },
        )
    }
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore factory modes?") },
            text = {
                Text("This will delete all custom modes managed by this app. When the last custom mode is deleted, the ALLTY 1500S restores its factory modes.")
            },
            confirmButton = {
                Button(onClick = {
                    showRestoreDialog = false
                    onRestoreFactoryModes()
                }) { Text("Delete modes") }
            },
            dismissButton = { TextButton(onClick = { showRestoreDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ConnectionCard(
    connectionState: BleConnectionState,
    lastDeviceAddress: String?,
    batteryPercent: Int?,
    temperatureCelsius: Int?,
    onChooseDevice: () -> Unit,
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefreshTelemetry: () -> Unit,
) {
    val connectedState = connectionState as? BleConnectionState.Connected
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Magicshine ALLTY 1500S", style = MaterialTheme.typography.titleLarge)
            Text("Device: ${connectedState?.deviceName ?: "M1-B3"}")
            Text("Status: ${connectionState.label()}")
            Text("Address: ${connectedState?.address ?: lastDeviceAddress ?: "Not selected"}")
            Text(
                "Battery: " + when {
                    connectedState == null -> "—"
                    batteryPercent == null -> "Waiting for response…"
                    else -> "$batteryPercent%"
                },
            )
            Text(
                "Temperature: " + when {
                    connectedState == null -> "—"
                    temperatureCelsius == null -> "Waiting for response…"
                    else -> "$temperatureCelsius °C"
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onChooseDevice, enabled = connectedState == null) { Text("Connect") }
                if (lastDeviceAddress != null && connectedState == null) {
                    OutlinedButton(onClick = onReconnect) { Text("Reconnect") }
                }
                if (connectedState != null) {
                    OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
                }
            }
            OutlinedButton(
                onClick = onRefreshTelemetry,
                enabled = connectedState != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Refresh battery and temperature") }
        }
    }
}

@Composable
private fun ModeRow(mode: LightMode, enabled: Boolean, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(mode.type.displayName(), style = MaterialTheme.typography.titleMedium)
            Text("Mode ${mode.slot}", style = MaterialTheme.typography.bodySmall)
        }
        Text("${mode.brightness}%", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onDelete, enabled = enabled) { Text("Delete") }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddModeDialog(onDismiss: () -> Unit, onAdd: (ModeType, Int) -> Unit) {
    var type by remember { mutableStateOf(ModeType.CONSTANT) }
    var brightness by remember { mutableFloatStateOf(50f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add custom mode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Mode type")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModeType.entries.forEach { candidate ->
                        FilterChip(
                            selected = type == candidate,
                            onClick = { type = candidate },
                            label = { Text(candidate.displayName()) },
                        )
                    }
                }
                Text("Brightness: ${brightness.toInt()}%", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    valueRange = 1f..100f,
                    steps = 98,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(type, brightness.toInt()) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun ModeType.displayName(): String = when (this) {
    ModeType.CONSTANT -> "Constant"
    ModeType.FLASH -> "Flash"
    ModeType.SOS -> "SOS"
}
