package com.itantra.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PackValidationStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    INSTALLED_UNVALIDATED,
    READY,
    VALIDATION_FAILED
}

@Entity(tableName = "model_packs")
data class ModelPackEntity(
    @PrimaryKey val language: String, // e.g. "hi", "ta", "gu", "mr", "kn", "ml", "te", "or", "bn", "en"
    val displayName: String,
    val isBuiltIn: Boolean = false,
    val asrModelId: String?,
    val ttsModelId: String?,
    val asrPath: String? = null,
    val ttsPath: String? = null,
    val asrSha256: String? = null,
    val ttsSha256: String? = null,
    val sizeBytes: Long = 0L,
    val status: PackValidationStatus = PackValidationStatus.NOT_DOWNLOADED,
    val lastValidatedAt: Long? = null,
    val validationError: String? = null
)
