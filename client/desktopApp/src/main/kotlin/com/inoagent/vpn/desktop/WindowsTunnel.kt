package com.inoagent.vpn.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.*
import java.nio.file.attribute.*
import java.util.concurrent.TimeUnit

/** Talks to the official AmneziaWG Windows tunnel service; each elevation uses normal UAC. */
class WindowsTunnel(private val directory:Path) {
    private val name="inoagent-"+java.security.MessageDigest.getInstance("SHA-256").digest(System.getProperty("user.home").toByteArray()).joinToString("") { "%02x".format(it) }.take(6)
    private val configFile=directory.resolve("$name.conf")
    private val exe=Path.of(System.getenv("ProgramFiles") ?: "C:\\Program Files","AmneziaWG","amneziawg.exe")
    private val service="AmneziaWGTunnel\$$name"
    private fun windows() { check(System.getProperty("os.name").startsWith("Windows")) { "Для VPN на macOS используйте Apple-версию приложения с Network Extension" } }
    suspend fun connect(config:String)=withContext(Dispatchers.IO) {
        windows()
        check(Files.isRegularFile(exe)) { "Нужен системный движок AmneziaWG. Установите его из официального Windows-пакета и повторите" }
        require(!Regex("(?im)^\\s*(PreUp|PostUp|PreDown|PostDown)\\s*=").containsMatchIn(config)) { "Конфигурации с выполняемыми командами не поддерживаются" }
        Files.createDirectories(directory)
        if(isRunning()) elevate("/uninstalltunnelservice",name)
        Files.writeString(configFile,config,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING)
        try {
            val lookup=configFile.fileSystem.userPrincipalLookupService
            val user=Files.getOwner(configFile)
            val system=lookup.lookupPrincipalByName("SYSTEM")
            val permissions=AclEntryPermission.values().toSet()
            val acl=listOf(user,system).map { AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(it).setPermissions(permissions).build() }
            Files.getFileAttributeView(configFile,AclFileAttributeView::class.java).acl=acl
            elevate("/installtunnelservice",configFile.toAbsolutePath().toString())
            check(isRunning()) { "Служба VPN не запущена. Проверьте подтверждение Windows и установку AmneziaWG" }
        } catch(e:Exception) { if(!isRunning()) Files.deleteIfExists(configFile);throw e }
    }
    suspend fun disconnect()=withContext(Dispatchers.IO) {
        windows();if(isRunning()) elevate("/uninstalltunnelservice",name)
        check(!isRunning()) { "Не удалось остановить службу VPN" };Files.deleteIfExists(configFile)
    }
    fun isRunning():Boolean {
        if(!System.getProperty("os.name").startsWith("Windows")) return false
        val process=ProcessBuilder("sc.exe","query",service).redirectErrorStream(true).start()
        if(!process.waitFor(8,TimeUnit.SECONDS)) { process.destroyForcibly();return false }
        val text=process.inputStream.bufferedReader().readText()
        return process.exitValue()==0 && Regex(":\\s*4\\s").containsMatchIn(text)
    }
    private fun quote(value:String)="'"+value.replace("'","''")+"'"
    private fun elevate(action:String,value:String) {
        // Native argument quoting is explicit; no config content or private keys enter arguments.
        val arg="\"$value\""
        val script="\$p=Start-Process -FilePath ${quote(exe.toString())} -ArgumentList @(${quote(action)},${quote(arg)}) -Verb RunAs -Wait -PassThru; exit \$p.ExitCode"
        val process=ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-Command",script).redirectErrorStream(true).start()
        check(process.waitFor(180,TimeUnit.SECONDS)) { process.destroyForcibly();"Истекло время подтверждения Windows" }
        check(process.exitValue()==0) { "Windows не разрешила изменение VPN-службы" }
    }
}
