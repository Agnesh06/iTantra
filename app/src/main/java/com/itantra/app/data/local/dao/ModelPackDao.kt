package com.itantra.app.data.local.dao

import androidx.room.*
import com.itantra.app.data.local.entity.ModelPackEntity
import com.itantra.app.data.local.entity.PackValidationStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelPackDao {
    @Query("SELECT * FROM model_packs ORDER BY displayName ASC")
    fun getAllPacks(): Flow<List<ModelPackEntity>>

    @Query("SELECT * FROM model_packs WHERE language = :language LIMIT 1")
    suspend fun getPack(language: String): ModelPackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(pack: ModelPackEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(packs: List<ModelPackEntity>)

    @Query("UPDATE model_packs SET status = :status, lastValidatedAt = :lastValidatedAt, validationError = :error WHERE language = :language")
    suspend fun updateValidationStatus(
        language: String,
        status: PackValidationStatus,
        lastValidatedAt: Long?,
        error: String?
    )

    @Query("DELETE FROM model_packs WHERE language = :language AND isBuiltIn = 0")
    suspend fun deleteNonBuiltInPack(language: String)
}
