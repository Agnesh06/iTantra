package com.itantra.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val lastSeenAt: Long = System.currentTimeMillis()
)
