package com.itantra.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MessageDirection {
    OUTGOING, INCOMING
}

enum class MessageStatus {
    DRAFT,
    PROCESSING,
    SENT,
    RECEIVING,
    READY,
    PLAYING,
    COMPLETED,
    INCOMPLETE_TIMEOUT,
    ERROR,
    RETRYING,
    TranslationFailed
}

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: Long, // Numeric 64-bit unsigned/signed ID matching wire MESSAGE_ID
    val conversationId: String,
    val direction: MessageDirection,
    val sourceLanguage: String,
    val targetLanguage: String,
    val text: String,
    val translatedText: String?,
    val alertFlag: Boolean,
    val confidenceFlagsJson: String = "[]", // e.g. "[0,0,1,0]"
    val status: MessageStatus,
    val createdAt: Long = System.currentTimeMillis(),
    val sentAt: Long? = null,
    val receivedAt: Long? = null,
    val audioPath: String? = null,
    val errorCode: String? = null
)
