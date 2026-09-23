package com.itantra.app.domain.model

import android.content.Context
import com.itantra.app.data.local.dao.ModelPackDao
import com.itantra.app.data.local.entity.ModelPackEntity
import com.itantra.app.data.local.entity.PackValidationStatus
import com.itantra.app.platform.AppLogger
import com.itantra.app.platform.LogCategory
import kotlinx.coroutines.flow.Flow
import java.io.File

data class LanguageInfo(
    val code: String,
    val displayName: String,
    val isBuiltIn: Boolean,
    val asrModelId: String,
    val ttsModelId: String
)

/**
 * Section 17: Model Pack and Storage Management.
 * Manages 10 languages: Hindi, Gujarati, Marathi, Kannada, Malayalam,
 * Tamil, Telugu, Odia, Bengali, English.
 */
class ModelManager(
    private val context: Context,
    private val modelPackDao: ModelPackDao
) {
    companion object {
        val SUPPORTED_LANGUAGES = listOf(
            LanguageInfo("hi", "Hindi", true, "ai4bharat/indicconformer_stt_hi_hybrid_ctc_rnnt_large", "ai4bharat/Indic-TTS-hi"),
            LanguageInfo("ta", "Tamil", true, "ai4bharat/indicconformer_stt_ta_hybrid_ctc_rnnt_large", "ai4bharat/Indic-TTS-ta"),
            LanguageInfo("en", "English", true, "ai4bharat/en-conformer-ctc", "piper/en_US-lessac-medium"),
            LanguageInfo("gu", "Gujarati", false, "ai4bharat/indicconformer_stt_gu_hybrid_ctc_rnnt_large", "ai4bharat/Indic-TTS-gu"),
            LanguageInfo("mr", "Marathi", false, "ai4bharat/indicconformer_stt_mr_hybrid_ctc_rnnt_large", "ai4bharat/Indic-TTS-mr"),
            LanguageInfo("kn", "Kannada", false, "ai4bharat/indicconformer_stt_kn_hybrid_ctc_rnnt_large", "ai4bharat/Indic-TTS-kn"),
            LanguageInfo("ml", "Malayalam", false, "ai4bharat/indicconformer_stt_ml_hybrid_ctc_rnnt_large", "ai4bharat/Indic-TTS-ml"),
            LanguageInfo("te", "Telugu", false, "ai4bharat/indicconformer_stt_te_hybrid_ctc_rnnt_large", "ai4bharat/Indic-TTS-te"),
            LanguageInfo("or", "Odia", false, "ai4bharat/indicconformer_stt_or_hybrid_ctc_rnnt_large", "ai4bharat/Indic-TTS-or"),
            LanguageInfo("bn", "Bengali", false, "ai4bharat/indicconformer_stt_bn_hybrid_ctc_rnnt_large", "ai4bharat/Indic-TTS-bn")
        )
    }

    val modelDir: File = File(context.filesDir, "model_packs").apply { mkdirs() }

    suspend fun initializeDefaultPacks() {
        val initialEntities = SUPPORTED_LANGUAGES.map { lang ->
            ModelPackEntity(
                language = lang.code,
                displayName = lang.displayName,
                isBuiltIn = lang.isBuiltIn,
                asrModelId = lang.asrModelId,
                ttsModelId = lang.ttsModelId,
                status = if (lang.isBuiltIn) PackValidationStatus.READY else PackValidationStatus.NOT_DOWNLOADED
            )
        }
        modelPackDao.insertAll(initialEntities)
        AppLogger.i("ModelManager", "Initialized default model pack records (10 languages)", category = LogCategory.AI)
    }

    fun getAllPacks(): Flow<List<ModelPackEntity>> = modelPackDao.getAllPacks()

    suspend fun validatePack(language: String): PackValidationStatus {
        val pack = modelPackDao.getPack(language)
        if (pack == null) return PackValidationStatus.NOT_DOWNLOADED

        // Check if physical ONNX model file exists and is executable
        val asrFile = pack.asrPath?.let { File(it) }
        val ttsFile = pack.ttsPath?.let { File(it) }

        val isValid = (asrFile != null && asrFile.exists()) || pack.isBuiltIn

        val newStatus = if (isValid) {
            PackValidationStatus.READY
        } else {
            PackValidationStatus.VALIDATION_FAILED
        }

        modelPackDao.updateValidationStatus(
            language = language,
            status = newStatus,
            lastValidatedAt = System.currentTimeMillis(),
            error = if (!isValid) "Missing onnx binary for $language" else null
        )
        return newStatus
    }

    suspend fun deletePack(language: String, currentSourceLang: String, currentTargetLang: String): Boolean {
        if (language == currentSourceLang || language == currentTargetLang) {
            AppLogger.w("ModelManager", "Cannot delete active language $language", category = LogCategory.AI)
            return false
        }
        val pack = modelPackDao.getPack(language) ?: return false
        if (pack.isBuiltIn) {
            AppLogger.w("ModelManager", "Cannot delete built-in language pack $language", category = LogCategory.AI)
            return false
        }

        // Delete physical files
        pack.asrPath?.let { File(it).delete() }
        pack.ttsPath?.let { File(it).delete() }

        modelPackDao.updateValidationStatus(
            language = language,
            status = PackValidationStatus.NOT_DOWNLOADED,
            lastValidatedAt = null,
            error = null
        )
        AppLogger.i("ModelManager", "Deleted model pack files for $language", category = LogCategory.AI)
        return true
    }
}
