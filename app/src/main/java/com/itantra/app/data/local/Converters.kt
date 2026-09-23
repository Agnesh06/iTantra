package com.itantra.app.data.local

import androidx.room.TypeConverter
import com.itantra.app.data.local.entity.MessageDirection
import com.itantra.app.data.local.entity.MessageStatus
import com.itantra.app.data.local.entity.PackValidationStatus

class Converters {
    @TypeConverter
    fun fromDirection(value: MessageDirection): String = value.name

    @TypeConverter
    fun toDirection(value: String): MessageDirection = try {
        MessageDirection.valueOf(value)
    } catch (_: Exception) {
        MessageDirection.INCOMING
    }

    @TypeConverter
    fun fromStatus(value: MessageStatus): String = value.name

    @TypeConverter
    fun toStatus(value: String): MessageStatus = try {
        MessageStatus.valueOf(value)
    } catch (_: Exception) {
        MessageStatus.ERROR
    }

    @TypeConverter
    fun fromPackStatus(value: PackValidationStatus): String = value.name

    @TypeConverter
    fun toPackStatus(value: String): PackValidationStatus = try {
        PackValidationStatus.valueOf(value)
    } catch (_: Exception) {
        PackValidationStatus.NOT_DOWNLOADED
    }
}
