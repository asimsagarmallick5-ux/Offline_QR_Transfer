package com.example.qrcode.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {
    @Insert
    suspend fun insert(record: TransferRecord)

    @Query("SELECT * FROM transfer_records ORDER BY timestamp DESC")
    fun getAll(): Flow<List<TransferRecord>>
}