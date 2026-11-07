package com.inoagent.vpn.shared

data class VpnProfile(
    val id: Long,
    val name: String,
    val endpoint: String,
    val createdAtEpochMillis: Long,
    val accountId: Long? = null,
    val serverId: Long? = null,
)

data class ParsedConfigMetadata(
    val suggestedName: String,
    val endpoint: String,
)

