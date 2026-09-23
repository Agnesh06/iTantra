package com.itantra.app

import com.itantra.app.domain.ai.IndicTrans2TranslationManager
import com.itantra.app.domain.ai.LocalTtsManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SafeTranslationFallbackTest {

    private val translationManager = IndicTrans2TranslationManager()
    private val ttsManager = LocalTtsManager()

    @Test
    fun testSameLanguageSkipsTranslation() = runBlocking {
        val text = "नमस्ते भारत"
        val result = translationManager.translate(text, "hi", "hi")
        assertTrue(result.isTranslationSkipped)
        assertEquals(text, result.translatedText)
        assertFalse(result.isFailed)
    }

    @Test
    fun testTtsSynthesisBlockedOnTranslationFailure() = runBlocking {
        val tempWav = File.createTempFile("tts_test", ".wav")
        tempWav.deleteOnExit()

        // When translation failed, TTS must be blocked to prevent phoneme mismatch
        val result = ttsManager.synthesize(
            text = "Untranslated text",
            language = "hi",
            outputPath = tempWav,
            isTranslationFailed = true
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Translation failed") == true)
    }

    @Test
    fun testTtsSynthesisSucceedsForMatchingLanguage() = runBlocking {
        val tempWav = File.createTempFile("tts_valid", ".wav")
        tempWav.deleteOnExit()

        val result = ttsManager.synthesize(
            text = "Valid text for synthesis",
            language = "en",
            outputPath = tempWav,
            isTranslationFailed = false
        )

        assertTrue(result.isSuccess)
        val ttsResult = result.getOrThrow()
        assertTrue(ttsResult.audioFile.exists())
        assertTrue(ttsResult.audioFile.length() > 44) // Valid WAV header + PCM
    }
}
