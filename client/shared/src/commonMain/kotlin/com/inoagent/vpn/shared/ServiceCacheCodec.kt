package com.inoagent.vpn.shared

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class CachedAccount(val user:SiteUser,val servers:List<SiteServer>)
class ServiceCacheCodec {
    private val json=Json { ignoreUnknownKeys=true }
    fun encode(user:SiteUser,servers:List<SiteServer>)=json.encodeToString(CachedAccount.serializer(),CachedAccount(user,servers))
    @Throws(Exception::class) fun decode(value:String)=json.decodeFromString<CachedAccount>(value)
}
