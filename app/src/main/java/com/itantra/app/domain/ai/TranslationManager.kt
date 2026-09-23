package com.itantra.app.domain.ai

import com.itantra.app.platform.AppLogger
import com.itantra.app.platform.LogCategory

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val isTranslationSkipped: Boolean = false,
    val isFailed: Boolean = false,
    val errorMessage: String? = null
)

interface TranslationManager {
    suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String
    ): TranslationResult
}

/**
 * Section 9.2 & 11: IndicTrans2 Distilled Translation Manager.
 * Rules:
 * 1. source == target -> skip translation entirely.
 * 2. Never send audio to translation.
 * 3. On failure -> retain original transcript and return isFailed=true (TranslationFailed).
 */
class IndicTrans2TranslationManager : TranslationManager {

    override suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String
    ): TranslationResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return TranslationResult(originalText = text, translatedText = text, isTranslationSkipped = true)
        }

        // Rule 1: Same language bypass
        if (sourceLang.equals(targetLang, ignoreCase = true)) {
            AppLogger.i("Translation", "Source and target languages are identical ($sourceLang). Skipping translation.", category = LogCategory.AI)
            return TranslationResult(originalText = text, translatedText = text, isTranslationSkipped = true)
        }

        AppLogger.i("Translation", "Translating from '$sourceLang' to '$targetLang'", category = LogCategory.AI)

        // IndicTrans2 ONNX execution hook
        // When real ONNX model file is deployed, runs IndicTrans2 tokenizer & beam search.
        return try {
            // Placeholder representation for local test verification
            val simulatedTranslation = "[$targetLang] $trimmed"
            TranslationResult(
                originalText = text,
                translatedText = simulatedTranslation,
                isTranslationSkipped = false,
                isFailed = false
            )
        } catch (e: Exception) {
            AppLogger.e("Translation", "Translation failed: ${e.message}", e, category = LogCategory.AI)
            // Rule 3: Safe failure fallback - retain original transcript, do not discard!
            TranslationResult(
                originalText = text,
                translatedText = text,
                isTranslationSkipped = false,
                isFailed = true,
                errorMessage = e.message
            )
        }
    }
}
