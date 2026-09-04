package com.example.alltycontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.alltycontrol.viewmodel.OperationProgress

@Composable
fun AdvancedScreen(
    connected: Boolean,
    progress: OperationProgress?,
    onForceClear: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirm by remember { mutableStateOf(false) }
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Advanced", style = MaterialTheme.typography.headlineSmall)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Force clear custom modes", style = MaterialTheme.typography.titleLarge)
                Text("Sends delete commands for every Constant, Flash and SOS brightness level from 1 to 100. Use this if the local mode list is lost or inconsistent with the lamp.")
                Button(
                    onClick = { confirm = true },
                    enabled = connected && progress == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Force clear all possible custom modes") }
            }
        }
        if (progress != null) {
            Text(progress.title, style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(
                progress = { progress.current.toFloat() / progress.total.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${progress.current} / ${progress.total}")
            if (progress.cancellable) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Send 300 delete commands?") },
            text = { Text("This operation takes time, writes directly to the connected lamp, and should not be interrupted by disconnecting Bluetooth.") },
            confirmButton = {
                Button(onClick = {
                    confirm = false
                    onForceClear()
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
