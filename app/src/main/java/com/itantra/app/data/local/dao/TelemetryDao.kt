package com.itantra.app.data.local.dao

import androidx.room.*
import com.itantra.app.data.local.entity.TelemetryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TelemetryDao {
    @Query("SELECT * FROM telemetry ORDER BY timestamp DESC LIMIT 500")
    fun getRecentTelemetry(): Flow<List<TelemetryEntity>>

    @Insert
    suspend fun insertRaw(telemetry: TelemetryEntity): Long

    @Query("DELETE FROM telemetry WHERE id NOT IN (SELECT id FROM telemetry ORDER BY timestamp DESC LIMIT 500)")
    suspend fun purgeOldRecords()

    @Transaction
    suspend fun insertAndPurge(telemetry: TelemetryEntity) {
        insertRaw(telemetry)
        purgeOldRecords()
    }

    @Query("DELETE FROM telemetry")
    suspend fun clearTelemetry()
}
