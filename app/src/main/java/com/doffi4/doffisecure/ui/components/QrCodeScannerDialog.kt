package com.doffi4.doffisecure.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.doffi4.doffisecure.R
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrCodeScannerDialog(
    onDismiss: () -> Unit,
    onCodeScanned: (String) -> Unit,
    onManualInputRequested: () -> Unit,
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            if (hasCameraPermission) {
                CameraPreviewContent(
                    onCodeScanned = onCodeScanned,
                    onDismiss = onDismiss,
                    onManualInputRequested = onManualInputRequested
                )
            } else {
                CameraPermissionDeniedContent(
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onCodeScanned = onCodeScanned,
                    onManualInputRequested = onManualInputRequested,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
private fun CameraPreviewContent(
    onCodeScanned: (String) -> Unit,
    onDismiss: () -> Unit,
    onManualInputRequested: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val noQrMessage = stringResource(R.string.totp_error_no_qr_found)
    var isScanned by remember { mutableStateOf(value = false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scanQrCodeFromUri(
                context = context,
                uri = uri,
                onSuccess = { raw ->
                    if (!isScanned) {
                        isScanned = true
                        onCodeScanned(raw)
                    }
                },
                onNotFound = {
                    Toast.makeText(context, noQrMessage, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView<PreviewView>(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                val cameraExecutor = Executors.newSingleThreadExecutor()

                cameraProviderFuture.addListener(
                    {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val scanner = BarcodeScanning.getClient(
                            BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                .build(),
                        )

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            @androidx.annotation.OptIn(ExperimentalGetImage::class)
                            val mediaImage = imageProxy.image
                            if ((mediaImage != null) && !isScanned) {
                                val inputImage = InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy.imageInfo.rotationDegrees,
                                )
                                scanner.process(inputImage)
                                    .addOnSuccessListener { barcodes ->
                                        if (isScanned) return@addOnSuccessListener
                                        for (barcode in barcodes) {
                                            val raw = barcode.rawValue
                                            if (!raw.isNullOrBlank()) {
                                                isScanned = true
                                                onCodeScanned(raw)
                                                break
                                            }
                                        }
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis,
                            )
                        } catch (_: Exception) {}
                    },
                    ContextCompat.getMainExecutor(ctx),
                )

                previewView
            },
        )

        // Overlay with scanning frame
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.totp_scanner_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = {
                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = stringResource(R.string.totp_btn_pick_gallery),
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = Color.White
                        )
                    }
                }
            }

            // Target reticle
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                )
            }

            // Bottom controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.totp_scanner_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = {
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.totp_btn_pick_gallery))
                }

                OutlinedButton(
                    onClick = onManualInputRequested,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.totp_btn_enter_manually))
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionDeniedContent(
    onRequestPermission: () -> Unit,
    onCodeScanned: (String) -> Unit,
    onManualInputRequested: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val noQrMessage = stringResource(R.string.totp_error_no_qr_found)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scanQrCodeFromUri(
                context = context,
                uri = uri,
                onSuccess = onCodeScanned,
                onNotFound = {
                    Toast.makeText(context, noQrMessage, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.totp_camera_permission_title),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.totp_camera_permission_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.totp_camera_grant_btn))
        }
        Spacer(modifier = Modifier.height(8.dp))
        FilledTonalButton(
            onClick = {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Image, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.totp_btn_pick_gallery))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onManualInputRequested,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text(stringResource(R.string.totp_btn_enter_manually))
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.action_cancel), color = Color.White.copy(alpha = 0.7f))
        }
    }
}
