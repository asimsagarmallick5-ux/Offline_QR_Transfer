package com.example.qrcode

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.example.qrcode.history.AppDatabase
import com.example.qrcode.history.TransferRecord
import com.example.qrcode.transfer.ChunkResult
import com.example.qrcode.transfer.FilePreview
import com.example.qrcode.transfer.FilePreviewDetector
import com.example.qrcode.transfer.QrAnalyzer
import com.example.qrcode.transfer.TransferReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun ReceiverScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val transferReceiver = remember { TransferReceiver(context) }
    var receivedCount by remember { mutableIntStateOf(0) }
    var totalExpected by remember { mutableStateOf<Int?>(null) }
    var isComplete by remember { mutableStateOf(false) }
    var isSecure by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var reassembledBytes by remember { mutableStateOf<ByteArray?>(null) }

    var lastChunkFoundTime by remember { mutableLongStateOf(0L) }
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var errorFlashMessage by remember { mutableStateOf<String?>(null) }
    var errorFlashTime by remember { mutableLongStateOf(0L) }

    var firstChunkTime by remember { mutableStateOf<Long?>(null) }
    var latestChunkTime by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(200)
            nowTick = System.currentTimeMillis()
        }
    }

    val showChunkFound = lastChunkFoundTime != 0L && (nowTick - lastChunkFoundTime) < 800
    val showErrorFlash = errorFlashMessage != null && (nowTick - errorFlashTime) < 2000

    val chunksPerSecond: Double? = run {
        val start = firstChunkTime
        val latest = latestChunkTime
        if (start != null && latest != null && receivedCount > 1) {
            val elapsedSec = (latest - start) / 1000.0
            if (elapsedSec > 0) receivedCount / elapsedSec else null
        } else null
    }

    val etaSeconds: Int? = run {
        if (chunksPerSecond != null && chunksPerSecond > 0 && totalExpected != null) {
            val remainingChunks = max(0, totalExpected!! - receivedCount)
            (remainingChunks / chunksPerSecond).roundToInt()
        } else null
    }

    fun handleReassemble() {
        try {
            val bytes = transferReceiver.reassembleFile(if (isSecure) password else null)
            reassembledBytes = bytes

            val info = FilePreviewDetector.detect(bytes).let {
                when(it) {
                    is FilePreview.ImagePreview -> it.info
                    is FilePreview.GifPreview -> it.info
                    is FilePreview.VideoPreview -> it.info
                    is FilePreview.TextPreview -> it.info
                    is FilePreview.GenericPreview -> it.info
                }
            }

            val timestamp = System.currentTimeMillis()
            val ext = getExtensionFromMime(info.mimeType)
            val fileName = "received_$timestamp$ext"

            // Save to internal app storage (private backup)
            val outFile = File(context.getExternalFilesDir(null), fileName)
            FileOutputStream(outFile).use { it.write(bytes) }

            // NEW: Save to Public Gallery/Downloads automatically
            saveFileToPublicStorage(context, bytes, info.mimeType, fileName)

            val speedAtCompletion = chunksPerSecond
            coroutineScope.launch {
                AppDatabase.getInstance(context).transferDao().insert(
                    TransferRecord(
                        fileName = fileName,
                        fileSizeBytes = bytes.size.toLong(),
                        timestamp = System.currentTimeMillis(),
                        direction = "RECEIVED",
                        success = true,
                        chunksPerSecond = speedAtCompletion
                    )
                )
            }
            errorFlashMessage = null
        } catch (e: Exception) {
            errorFlashMessage = e.message ?: "Failed to reassemble file"
            errorFlashTime = System.currentTimeMillis()
        }
    }

    Scaffold(
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp)
                ) {
                    Text("Back to Home")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Receive File",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(24.dp)
            )

            if (hasCameraPermission) {
                Box(
                    modifier = Modifier
                        .height(400.dp)
                        .fillMaxWidth()
                ) {
                    if (!isComplete) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }
                                    
                                    val resolutionSelector = ResolutionSelector.Builder()
                                        .setResolutionStrategy(
                                            ResolutionStrategy(
                                                Size(1280, 720),
                                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                            )
                                        )
                                        .build()

                                    val analysis = ImageAnalysis.Builder()
                                        .setResolutionSelector(resolutionSelector)
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()

                                    analysis.setAnalyzer(
                                        Executors.newSingleThreadExecutor(),
                                        QrAnalyzer { qrString ->
                                            if (!isComplete) {
                                                when (transferReceiver.receiveQrString(qrString)) {
                                                    is ChunkResult.NewChunk -> {
                                                        val now = System.currentTimeMillis()
                                                        receivedCount = transferReceiver.receivedCount
                                                        totalExpected = transferReceiver.totalChunksExpected()
                                                        isSecure = transferReceiver.isSecure
                                                        lastChunkFoundTime = now
                                                        if (firstChunkTime == null) firstChunkTime = now
                                                        latestChunkTime = now
                                                        errorFlashMessage = null
                                                    }
                                                    is ChunkResult.ChecksumFailed -> {
                                                        errorFlashMessage = "Chunk corrupted, waiting for retry..."
                                                        errorFlashTime = System.currentTimeMillis()
                                                    }
                                                    else -> {}
                                                }

                                                if (transferReceiver.isComplete()) {
                                                    isComplete = true
                                                    isSecure = transferReceiver.isSecure
                                                    if (!isSecure) handleReassemble()
                                                }
                                            }
                                        }
                                    )

                                    try {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            analysis
                                        )
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        ScanAlignmentOverlay(modifier = Modifier.fillMaxSize())
                    } else {
                        // Secure Completion UI
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Data Received!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            
                            if (isSecure && reassembledBytes == null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Encrypted Transfer", fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedTextField(
                                            value = password,
                                            onValueChange = { password = it },
                                            label = { Text("Enter Password") },
                                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                            visualTransformation = PasswordVisualTransformation(),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(onClick = { handleReassemble() }, modifier = Modifier.fillMaxWidth()) {
                                            Text("Decrypt & Reassemble")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (!isComplete) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            ScanStatusIndicator(
                                isScanning = totalExpected == null || !isComplete,
                                showChunkFound = showChunkFound
                            )

                            if (totalExpected != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Progress: $receivedCount / $totalExpected chunks", fontWeight = FontWeight.Medium)
                                if (isSecure) {
                                    Text("🛡️ Secure Mode Active", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                }
                                LinearProgressIndicator(
                                    progress = { receivedCount.toFloat() / (totalExpected ?: 1) },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    strokeCap = StrokeCap.Butt
                                )

                                if (chunksPerSecond != null) {
                                    val etaText = etaSeconds?.let { eta ->
                                        if (eta < 60) "$eta sec" else "${eta / 60}m ${eta % 60}s"
                                    } ?: "calculating..."
                                    Text("ETA: $etaText · Speed: %.1f chunks/s".format(chunksPerSecond), style = MaterialTheme.typography.bodySmall)
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                TextButton(
                                    onClick = {
                                        transferReceiver.cancel()
                                        receivedCount = 0
                                        totalExpected = null
                                        isComplete = false
                                        isSecure = false
                                        reassembledBytes = null
                                        firstChunkTime = null
                                        latestChunkTime = null
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Cancel Transfer")
                                }
                            }
                        }
                    }
                } else if (reassembledBytes != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        FilePreviewView(reassembledBytes!!)
                    }
                }
                
                AnimatedVisibility(visible = showErrorFlash) {
                    Text(
                        errorFlashMessage ?: "", 
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            } else {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("Camera permission required.", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun ScanStatusIndicator(isScanning: Boolean, showChunkFound: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = if (showChunkFound) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showChunkFound) {
                Text("Chunk Found!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            } else if (isScanning) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
                    label = "alpha"
                )
                Text("Scanning...", modifier = Modifier.alpha(alpha), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun FilePreviewView(bytes: ByteArray) {
    val context = LocalContext.current
    val preview = remember(bytes) { FilePreviewDetector.detect(bytes) }
    
    val info = when(preview) {
        is FilePreview.ImagePreview -> preview.info
        is FilePreview.GifPreview -> preview.info
        is FilePreview.VideoPreview -> preview.info
        is FilePreview.TextPreview -> preview.info
        is FilePreview.GenericPreview -> preview.info
    }

    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Transfer Complete", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(4.dp))
        Text("${info.type} · ${formatSize(info.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Spacer(modifier = Modifier.height(20.dp))
        
        when (preview) {
            is FilePreview.ImagePreview -> {
                Image(
                    bitmap = preview.bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Fit
                )
            }
            is FilePreview.GifPreview -> {
                val imageLoader = remember {
                    ImageLoader.Builder(context)
                        .components {
                            if (android.os.Build.VERSION.SDK_INT >= 28) {
                                add(ImageDecoderDecoder.Factory())
                            } else {
                                add(GifDecoder.Factory())
                            }
                        }
                        .build()
                }
                
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(preview.bytes)
                        .build(),
                    contentDescription = null,
                    imageLoader = imageLoader,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Fit
                )
            }
            is FilePreview.VideoPreview -> {
                VideoPlayer(bytes = preview.bytes)
            }
            is FilePreview.TextPreview -> {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
                ) {
                    Text(
                        preview.snippet,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Start
                    )
                }
            }
            is FilePreview.GenericPreview -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Info, 
                        contentDescription = null, 
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Preview not available for this file type.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Saved to Gallery and QRTransfer folder",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun VideoPlayer(bytes: ByteArray) {
    val context = LocalContext.current
    val tempFile = remember(bytes) {
        val file = File(context.cacheDir, "temp_video_${System.currentTimeMillis()}.mp4")
        file.writeBytes(bytes)
        file
    }

    val exoPlayer = remember(tempFile) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.fromFile(tempFile))
            setMediaItem(mediaItem)
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
            if (tempFile.exists()) tempFile.delete()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(MaterialTheme.shapes.medium)
    )
}

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
    return "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

fun getExtensionFromMime(mime: String): String {
    return when (mime) {
        "image/png" -> ".png"
        "image/jpeg" -> ".jpg"
        "image/gif" -> ".gif"
        "video/mp4" -> ".mp4"
        "application/pdf" -> ".pdf"
        "application/zip" -> ".zip"
        "text/plain" -> ".txt"
        else -> ""
    }
}

fun saveFileToPublicStorage(context: Context, bytes: ByteArray, mimeType: String, fileName: String) {
    val resolver = context.contentResolver
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val collection = when {
            mimeType.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            mimeType.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (mimeType.startsWith("image/") || mimeType.startsWith("video/")) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/QRTransfer")
            } else {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/QRTransfer")
            }
        }

        try {
            val uri = resolver.insert(collection, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { output ->
                    output.write(bytes)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    } else {
        // Legacy implementation for API 28
        val directory = if (mimeType.startsWith("image/") || mimeType.startsWith("video/")) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }
        val appDir = File(directory, "QRTransfer")
        if (!appDir.exists()) appDir.mkdirs()
        val file = File(appDir, fileName)
        try {
            FileOutputStream(file).use { it.write(bytes) }
            // Scan the file so it appears in the gallery immediately
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun ScanAlignmentOverlay(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val squareSize = size.minDimension * 0.7f
        val left = (size.width - squareSize) / 2f
        val top = (size.height - squareSize) / 2f
        val right = left + squareSize
        val bottom = top + squareSize
        val cornerLength = 40.dp.toPx()
        val strokeWidth = 4.dp.toPx()

        val overlayColor = Color.Black.copy(alpha = 0.6f)
        drawRect(color = overlayColor, topLeft = Offset(0f, 0f), size = GeometrySize(size.width, top))
        drawRect(color = overlayColor, topLeft = Offset(0f, bottom), size = GeometrySize(size.width, size.height - bottom))
        drawRect(color = overlayColor, topLeft = Offset(0f, top), size = GeometrySize(left, squareSize))
        drawRect(color = overlayColor, topLeft = Offset(right, top), size = GeometrySize(size.width - right, squareSize))

        // Corners
        drawLine(primaryColor, Offset(left, top), Offset(left + cornerLength, top), strokeWidth)
        drawLine(primaryColor, Offset(left, top), Offset(left, top + cornerLength), strokeWidth)
        drawLine(primaryColor, Offset(right, top), Offset(right - cornerLength, top), strokeWidth)
        drawLine(primaryColor, Offset(right, top), Offset(right, top + cornerLength), strokeWidth)
        drawLine(primaryColor, Offset(left, bottom), Offset(left + cornerLength, bottom), strokeWidth)
        drawLine(primaryColor, Offset(left, bottom), Offset(left, bottom - cornerLength), strokeWidth)
        drawLine(primaryColor, Offset(right, bottom), Offset(right - cornerLength, bottom), strokeWidth)
        drawLine(primaryColor, Offset(right, bottom), Offset(right, bottom - cornerLength), strokeWidth)
    }
}
