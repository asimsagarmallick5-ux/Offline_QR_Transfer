package com.example.qrcode

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.qrcode.protocol.CryptoUtils
import com.example.qrcode.protocol.QrFrameCodec
import com.example.qrcode.protocol.QrPacket
import com.example.qrcode.transfer.FileChunker
import com.example.qrcode.transfer.QrImageGenerator
import com.example.qrcode.ui.theme.QRCodeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QRCodeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var screen by remember { mutableStateOf("home") }

                    // Security: Toggle screenshot protection based on screen
                    DisposableEffect(screen) {
                        if (screen == "send") {
                            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                        onDispose {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }

                    when (screen) {
                        "home" -> HomeScreen(
                            onSendClick = { screen = "send" },
                            onReceiveClick = { screen = "receive" }
                        )
                        "send" -> SenderScreen(onBack = { screen = "home" })
                        "receive" -> ReceiverScreen(onBack = { screen = "home" })
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(onSendClick: () -> Unit, onReceiveClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Offline QR File Transfer",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Securely transfer files without internet",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onSendClick,
            modifier = Modifier.fillMaxWidth(0.8f).height(64.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text("Send File", style = MaterialTheme.typography.titleMedium)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        FilledTonalButton(
            onClick = onReceiveClick,
            modifier = Modifier.fillMaxWidth(0.8f).height(64.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text("Receive File", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedFileSize by remember { mutableLongStateOf(0L) }
    var packets by remember { mutableStateOf<List<QrPacket>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    var isShowingEstimate by remember { mutableStateOf(false) }
    var isProcessingFile by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            isProcessingFile = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    var name: String? = null
                    var size: Long = 0
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            name = cursor.getString(nameIdx)
                            size = cursor.getLong(sizeIdx)
                        }
                    }
                    
                    val sessionId = UUID.randomUUID().toString().take(8)
                    val fileId = UUID.randomUUID().toString().take(8)
                    val chunkedPackets = FileChunker.chunkFile(context, uri, sessionId, fileId)
                    
                    withContext(Dispatchers.Main) {
                        selectedFileName = name
                        selectedFileSize = size
                        packets = chunkedPackets
                        currentIndex = 0
                        isPaused = false
                        errorMessage = null
                        isShowingEstimate = true
                        isProcessingFile = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Failed to process file: ${e.message}"
                        isProcessingFile = false
                    }
                }
            }
        }
    }

    fun startTransfer(password: String) {
        isProcessingFile = true
        isShowingEstimate = false
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val uri = selectedFileUri ?: return@launch
                val rawBytes = FileChunker.readAllBytes(context, uri)
                
                val finalBytes = if (password.isNotEmpty()) {
                    CryptoUtils.encrypt(rawBytes, password)
                } else {
                    rawBytes
                }
                
                val sessionId = UUID.randomUUID().toString().take(8)
                val fileId = UUID.randomUUID().toString().take(8)
                val version = if (password.isNotEmpty()) 2 else 1
                val encryptedPackets = FileChunker.chunkBytes(finalBytes, sessionId, fileId, version = version)
                
                withContext(Dispatchers.Main) {
                    packets = encryptedPackets
                    currentIndex = 0
                    isProcessingFile = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Preparation failed: ${e.message}"
                    isProcessingFile = false
                }
            }
        }
    }

    fun resetSender() {
        selectedFileUri = null
        packets = emptyList()
        currentIndex = 0
        isPaused = false
        selectedFileName = null
        selectedFileSize = 0L
        isShowingEstimate = false
    }

    BackHandler(onBack = {
        if (packets.isNotEmpty()) resetSender() else onBack()
    })

    if (isShowingEstimate && selectedFileName != null) {
        CalculatorScreen(
            fileName = selectedFileName!!,
            fileSize = selectedFileSize,
            totalChunks = packets.size,
            onStart = { password -> startTransfer(password) },
            onCancel = { resetSender() }
        )
        return
    }

    LaunchedEffect(packets, isPaused) {
        if (packets.isNotEmpty() && !isPaused) {
            while (true) {
                delay(400)
                if (packets.isNotEmpty()) {
                    currentIndex = (currentIndex + 1) % packets.size
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sending File") },
                navigationIcon = {
                    IconButton(onClick = { if (packets.isNotEmpty()) resetSender() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            if (packets.isNotEmpty() && !isShowingEstimate) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { isPaused = !isPaused },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text(if (isPaused) "Resume" else "Pause")
                        }
                        Button(
                            onClick = { resetSender() },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Stop")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isProcessingFile) {
                CircularProgressIndicator()
                Text("Preparing transfer...", modifier = Modifier.padding(top = 16.dp))
            } else if (packets.isEmpty()) {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    "Select a file to begin the transfer",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.height(56.dp)
                ) {
                    Text("Select File")
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("File: $selectedFileName", style = MaterialTheme.typography.titleSmall)
                        val isEncrypted = packets.firstOrNull()?.version == 2
                        if (isEncrypted) {
                            Text(
                                "🛡️ Secure End-to-End Encryption Active",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "Chunk ${currentIndex + 1} of ${packets.size}",
                    style = MaterialTheme.typography.titleMedium
                )
                
                if (isPaused) {
                    Text(
                        "PAUSED",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                QrFrameDisplay(packet = packets.getOrNull(currentIndex))

                Spacer(modifier = Modifier.height(24.dp))

                LinearProgressIndicator(
                    progress = { if (packets.isNotEmpty()) (currentIndex + 1).toFloat() / packets.size else 0f },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                OutlinedButton(
                    onClick = { resetSender() },
                    modifier = Modifier.fillMaxWidth(0.6f)
                ) {
                    Text("Select Different File")
                }
            }

            errorMessage?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun QrFrameDisplay(packet: QrPacket?) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(packet) {
        if (packet != null) {
            bitmap = withContext(Dispatchers.Default) {
                val qrString = QrFrameCodec.packetToQrString(packet)
                QrImageGenerator.generate(qrString)
            }
        }
    }

    Surface(
        modifier = Modifier.size(320.dp),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(contentAlignment = Alignment.Center) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "QR Code Frame",
                    modifier = Modifier.padding(16.dp).fillMaxSize()
                )
            } ?: CircularProgressIndicator()
        }
    }
}
