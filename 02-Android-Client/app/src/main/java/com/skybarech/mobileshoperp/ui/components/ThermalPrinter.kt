package com.skybarech.mobileshoperp.ui.components

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.skybarech.mobileshoperp.ui.i18n.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"

private data class ThermalDevice(val name: String, val address: String)

/** Prints plain-text receipts to a paired ESC/POS Bluetooth thermal printer (58 mm or 80 mm). */
@Composable
fun BluetoothThermalPrintButton(
    receipt: String,
    enabled: Boolean,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPicker by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<ThermalDevice>>(emptyList()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            devices = pairedThermalDevices()
            showPicker = true
        } else onMessage("Bluetooth permission allow karein, phir paired printer select karein.")
    }
    fun choosePrinter() {
        if (!enabled) { onMessage("Save the invoice before printing."); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            devices = pairedThermalDevices()
            showPicker = true
        }
    }
    OutlineButton("Bluetooth Print", ::choosePrinter, modifier)
    if (showPicker) AlertDialog(
        onDismissRequest = { showPicker = false },
        title = { Text("Select paired thermal printer") },
        text = {
            if (devices.isEmpty()) UiText("Phone Settings > Bluetooth mein printer pair karein, phir yahan dobara open karein.")
            else androidx.compose.foundation.layout.Column {
                devices.forEach { device ->
                    TextButton(onClick = {
                        showPicker = false
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { printToThermal(device.address, receipt) }
                            onMessage(result)
                        }
                    }) { Text(device.name) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showPicker = false }) { Text("Close") } }
    )
}

@Suppress("DEPRECATION")
private fun pairedThermalDevices(): List<ThermalDevice> = runCatching {
    BluetoothAdapter.getDefaultAdapter()?.bondedDevices.orEmpty()
        .map { ThermalDevice(it.name ?: "Bluetooth printer", it.address) }
        .sortedBy { it.name.lowercase() }
}.getOrDefault(emptyList())

@Suppress("DEPRECATION")
private fun printToThermal(address: String, receipt: String): String = runCatching {
    val device = BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address) ?: error("Bluetooth is unavailable on this phone.")
    val socket = device.createRfcommSocketToServiceRecord(UUID.fromString(SPP_UUID))
    try {
        socket.connect()
        socket.outputStream.use { output ->
            output.write(byteArrayOf(0x1B, 0x40)) // ESC @ reset
            output.write(byteArrayOf(0x1B, 0x61, 0x01)) // centered heading
            output.write((receipt.lineSequence().firstOrNull().orEmpty() + "\n").toByteArray(Charsets.UTF_8))
            output.write(byteArrayOf(0x1B, 0x61, 0x00)) // left-aligned body
            output.write((receipt.lineSequence().drop(1).joinToString("\n") + "\n\n\n").toByteArray(Charsets.UTF_8))
            output.write(byteArrayOf(0x1D, 0x56, 0x00)) // full cut where supported
            output.flush()
        }
    } finally { socket.close() }
    "Receipt sent to Bluetooth thermal printer."
}.getOrElse { "Bluetooth print failed: ${it.message ?: "check pairing and printer power"}" }
