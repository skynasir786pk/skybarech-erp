package com.skybarech.mobileshoperp.ui.components

import android.content.Intent
import android.Manifest
import android.content.Context
import android.net.Uri
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import java.io.File
import java.util.UUID

@Composable
fun BarcodeScanButton(modifier: Modifier = Modifier, onScan: (String) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    var scannerOpen by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scannerOpen = true
        else onError("Camera permission is needed to scan. You can enter the code manually.")
    }
    OutlineButton("Scan barcode", {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scannerOpen = true
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }, modifier, Icons.Outlined.QrCodeScanner)
    if (scannerOpen) CompactScannerDialog(
        onDismiss = { scannerOpen = false },
        onScan = { value -> onScan(value); scannerOpen = false }
    )
}

/** A compact in-app scanner: it never opens JourneyApps' full-screen capture activity. */
@Composable
private fun CompactScannerDialog(onDismiss: () -> Unit, onScan: (String) -> Unit) {
    var scannerView by remember { mutableStateOf<DecoratedBarcodeView?>(null) }
    var accepted by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { scannerView?.pause() } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SkyBarech ERP", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text("Keep IMEI or barcode inside the scan box", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().height(270.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.scrim
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            DecoratedBarcodeView(viewContext).also { view ->
                                scannerView = view
                                view.setStatusText("")
                                view.decodeSingle(object : BarcodeCallback {
                                    override fun barcodeResult(result: BarcodeResult?) {
                                        val value = result?.text.orEmpty()
                                        if (value.isNotBlank() && !accepted) {
                                            accepted = true
                                            view.post { onScan(value) }
                                        }
                                    }
                                    override fun possibleResultPoints(resultPoints: List<ResultPoint>?) = Unit
                                })
                                view.resume()
                            }
                        }
                    )
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss) { Text("Close scanner") }
            }
        }
    }
}

/** Images remain in this app's private storage, never in public Downloads. */
fun importPrivateImage(context: Context, uri: Uri): String {
    require(context.contentResolver.getType(uri) in listOf("image/png", "image/jpeg")) { "Choose a PNG or JPEG image." }
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= 2 * 1024 * 1024) { "Image must be 2 MB or smaller." }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    } ?: error("The selected image could not be opened.")
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "This file is not a valid image." }
    val directory = File(context.filesDir, "shop-images").apply { mkdirs() }
    val file = File(directory, "${UUID.randomUUID()}.img")
    file.writeBytes(bytes)
    return file.name
}

private fun saveCameraPreview(context: Context, bitmap: android.graphics.Bitmap): String {
    val directory = File(context.filesDir, "shop-images").apply { mkdirs() }
    val file = File(directory, "${UUID.randomUUID()}.jpg")
    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
    require(file.length() <= 2 * 1024 * 1024) { "Captured image is too large. Please retake it." }
    return file.name
}

@Composable
fun ImageUploadButton(label: String, onSaved: (String) -> Unit, onError: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { importPrivateImage(context, uri) }.onSuccess(onSaved).onFailure { onError(it.message ?: "Image upload failed.") }
    }
    OutlineButton(label, { launcher.launch(arrayOf("image/png", "image/jpeg")) }, modifier)
}

/** Captures a purchase document directly from the phone camera into private app storage. */
@Composable
fun CameraImageButton(label: String, onSaved: (String) -> Unit, onError: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) runCatching { saveCameraPreview(context, bitmap) }
            .onSuccess(onSaved)
            .onFailure { onError(it.message ?: "Camera image could not be saved.") }
        else onError("Camera capture cancelled.")
    }
    OutlineButton(label, { launcher.launch(null) }, modifier, Icons.Outlined.PhotoCamera)
}
