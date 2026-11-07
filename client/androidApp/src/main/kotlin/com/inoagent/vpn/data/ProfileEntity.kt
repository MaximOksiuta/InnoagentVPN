package com.inoagent.vpn.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val endpoint: String,
    val encryptedConfig: String,
    val createdAtEpochMillis: Long,
    val accountId: Long? = null,
    val serverId: Long? = null,
)

