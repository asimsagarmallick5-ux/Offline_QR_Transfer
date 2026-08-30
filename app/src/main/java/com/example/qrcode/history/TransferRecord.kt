package com.example.qrcode.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_records")
data class TransferRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val fileSizeBytes: Long,
    val timestamp: Long,
    val direction: String,   // "SENT" or "RECEIVED"
    val success: Boolean,
    val chunksPerSecond: Double?
)