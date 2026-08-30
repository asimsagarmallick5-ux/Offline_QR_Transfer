package com.example.qrcode

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.qrcode.history.AppDatabase
import com.example.qrcode.history.TransferRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val records = remember { AppDatabase.getInstance(context).transferDao().getAll() }
        .collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Transfer History", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        if (records.value.isEmpty()) {
            Text("No transfers yet.")
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(records.value) { record ->
                    HistoryRow(record)
                    HorizontalDivider()
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) { Text("Back") }
    }
}

@Composable
fun HistoryRow(record: TransferRecord) {
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(record.fileName, style = MaterialTheme.typography.bodyLarge)
        Text(
            "${record.direction} · ${record.fileSizeBytes} bytes · ${dateFormat.format(Date(record.timestamp))}",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            if (record.success) "Success" else "Failed",
            color = if (record.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}