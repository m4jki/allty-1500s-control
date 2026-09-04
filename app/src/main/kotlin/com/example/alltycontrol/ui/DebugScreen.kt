package com.example.alltycontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.alltycontrol.ble.AlltyProtocol
import com.example.alltycontrol.ble.BleConnectionState
import com.example.alltycontrol.ble.label
import com.example.alltycontrol.ble.parseHex

@Composable
fun DebugScreen(
    connectionState: BleConnectionState,
    deviceName: String?,
    deviceAddress: String?,
    services: List<String>,
    characteristics: List<String>,
    currentWriteCharacteristic: String?,
    lastTx: String?,
    lastRx: String?,
    batteryPercent: Int?,
    temperatureCelsius: Int?,
    log: List<String>,
    onSendRaw: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var rawHex by remember { mutableStateOf("") }
    val parsed = parseHex(rawHex)
    val validity = when {
        rawHex.isBlank() -> "Enter a frame to inspect it"
        parsed.isFailure -> parsed.exceptionOrNull()?.message ?: "Invalid hex"
        AlltyProtocol.isValidFrame(parsed.getOrThrow()) -> "Framing, length and checksum are valid"
        else -> "Hex is valid, but protocol framing, length or checksum is invalid"
    }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("BLE debug", style = MaterialTheme.typography.headlineSmall)
        DebugCard(
            listOf(
                "State: ${connectionState.label()}",
                "Device: ${deviceName ?: "—"}",
                "Address: ${deviceAddress ?: "—"}",
                "Write characteristic: ${currentWriteCharacteristic ?: "—"}",
                "Last TX: ${lastTx ?: "—"}",
                "Last RX: ${lastRx ?: "—"}",
                "Battery: ${batteryPercent?.let { "$it%" } ?: "—"}",
                "Temperature: ${temperatureCelsius?.let { "$it °C" } ?: "—"}",
            ),
        )
        DebugCard(listOf("Services:") + services.ifEmpty { listOf("—") })
        DebugCard(listOf("Characteristics:") + characteristics.ifEmpty { listOf("—") })
        Text("Send raw HEX", style = MaterialTheme.typography.titleLarge)
        Text(
            "Warning: Sending arbitrary BLE commands can change device configuration. Use only known protocol frames.",
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedTextField(
            value = rawHex,
            onValueChange = { rawHex = it },
            label = { Text("Hexadecimal bytes") },
            supportingText = { Text(validity) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Button(
            onClick = { onSendRaw(rawHex) },
            enabled = parsed.isSuccess && connectionState is BleConnectionState.Connected,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Send raw command") }
        Text("Protocol log", style = MaterialTheme.typography.titleLarge)
        SelectionContainer {
            Text(log.ifEmpty { listOf("No protocol traffic yet") }.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DebugCard(lines: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(lines.joinToString("\n"), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}
