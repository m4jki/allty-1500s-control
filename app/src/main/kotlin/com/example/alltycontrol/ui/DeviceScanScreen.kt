package com.example.alltycontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.alltycontrol.ble.DiscoveredDevice

@Composable
fun DeviceScanScreen(
    devices: List<DiscoveredDevice>,
    onRefresh: () -> Unit,
    onConnect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Nearby BLE devices", style = MaterialTheme.typography.headlineSmall)
        Text(
            "M1-B3 devices are shown first. Other devices are available for advanced/debug selection.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Scan again") }
        if (devices.isEmpty()) Text("Scanning…")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(devices, key = { it.address }) { device ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (device.isPreferred) "Magicshine ALLTY 1500S" else (device.name ?: "Unnamed BLE device"),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(device.name ?: "No advertised name")
                            Text(device.address)
                            Text("RSSI ${device.rssi} dBm")
                        }
                        Button(onClick = { onConnect(device.address) }) { Text("Connect") }
                    }
                }
            }
        }
    }
}
