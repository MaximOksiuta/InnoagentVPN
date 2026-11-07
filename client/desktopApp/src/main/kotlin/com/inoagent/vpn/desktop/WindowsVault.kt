package com.inoagent.vpn.desktop

import com.inoagent.vpn.shared.ClientVault
import com.sun.jna.platform.win32.Crypt32Util
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class WindowsVault(private val directory:Path):ClientVault {
    init { Files.createDirectories(directory) }
    private fun file(key:String)=directory.resolve(MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }+".dpapi")
    override fun read(key:String):String? {
        val path=file(key);if(!Files.exists(path)) return null
        return Crypt32Util.cryptUnprotectData(Files.readAllBytes(path)).toString(Charsets.UTF_8)
    }
    override fun write(key:String,value:String) {
        val path=file(key);val tmp=Files.createTempFile(directory,"vault-",".tmp")
        try { Files.write(tmp,Crypt32Util.cryptProtectData(value.toByteArray()));Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING) }
        finally { Files.deleteIfExists(tmp) }
    }
    override fun remove(key:String) { Files.deleteIfExists(file(key)) }
}

/** For UI preview on a Mac without a signed Apple target. Never persists credentials. */
class PreviewVault:ClientVault {
    private val values=mutableMapOf<String,String>()
    override fun read(key:String)=values[key]
    override fun write(key:String,value:String) { values[key]=value }
    override fun remove(key:String) { values.remove(key) }
}
