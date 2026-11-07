package com.inoagent.vpn.desktop

import com.inoagent.vpn.shared.*
import java.util.Properties
import java.io.StringReader
import java.io.StringWriter

class DesktopProfiles(private val vault:ClientVault) {
    fun list():List<VpnProfile> {
        val properties=Properties();vault.read("profiles.index")?.let { properties.load(StringReader(it)) }
        return properties.getProperty("ids","").split(',').mapNotNull { it.toLongOrNull() }.mapNotNull { id->
            val name=properties.getProperty("$id.name") ?: return@mapNotNull null
            VpnProfile(id,name,properties.getProperty("$id.endpoint",""),properties.getProperty("$id.created","0").toLong())
        }
    }
    fun import(name:String,raw:String):VpnProfile {
        val metadata=AmneziaConfigMetadataParser.parse(name,raw)
        val profile=VpnProfile(System.nanoTime(),metadata.suggestedName,metadata.endpoint,System.currentTimeMillis())
        vault.write("profile.${profile.id}",raw);saveIndex(list()+profile);return profile
    }
    fun config(id:Long)=vault.read("profile.$id") ?: error("Конфигурация не найдена")
    fun delete(id:Long) { saveIndex(list().filterNot { it.id==id });vault.remove("profile.$id") }
    private fun saveIndex(profiles:List<VpnProfile>) {
        val properties=Properties();properties.setProperty("ids",profiles.joinToString(",") { it.id.toString() })
        profiles.forEach { properties.setProperty("${it.id}.name",it.name);properties.setProperty("${it.id}.endpoint",it.endpoint);properties.setProperty("${it.id}.created",it.createdAtEpochMillis.toString()) }
        val writer=StringWriter();properties.store(writer,null);vault.write("profiles.index",writer.toString())
    }
}
