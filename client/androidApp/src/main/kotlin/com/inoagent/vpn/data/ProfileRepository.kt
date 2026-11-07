package com.inoagent.vpn.data

import com.inoagent.vpn.shared.AmneziaConfigMetadataParser
import com.inoagent.vpn.shared.VpnProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProfileRepository(
    private val dao: ProfileDao,
    private val cipher: ConfigCipher,
) {
    val profiles: Flow<List<VpnProfile>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    suspend fun import(fileName: String, contents: String): Long {
        val metadata = AmneziaConfigMetadataParser.parse(fileName, contents)
        return dao.insert(
            ProfileEntity(
                name = metadata.suggestedName,
                endpoint = metadata.endpoint,
                encryptedConfig = cipher.encrypt(contents),
                createdAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun configFor(profileId: Long): String {
        val row = daoRow(profileId)
        return cipher.decrypt(row.encryptedConfig)
    }

    suspend fun saveRemote(accountId:Long,serverId:Long,name:String,contents:String):Long {
        val metadata=AmneziaConfigMetadataParser.parse("$name.conf",contents)
        val existing=dao.findRemote(accountId,serverId)
        return dao.insert(ProfileEntity(existing?.id ?: 0,metadata.suggestedName,metadata.endpoint,cipher.encrypt(contents),existing?.createdAtEpochMillis ?: System.currentTimeMillis(),accountId,serverId))
    }

    suspend fun delete(profile: VpnProfile) {
        dao.delete(daoRow(profile.id))
    }

    private suspend fun daoRow(id: Long): ProfileEntity =
        dao.findById(id) ?: error("Профиль не найден")

    private fun ProfileEntity.toModel() = VpnProfile(id, name, endpoint, createdAtEpochMillis,accountId,serverId)
}
