package com.inoagent.vpn.shared

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val SERVICE_ORIGIN = "https://vpn.oksiuta.ru"

@Serializable data class SiteUser(val id:Long,val phone:String,val nickname:String,val isAdmin:Boolean=false)
@Serializable data class SiteServer(val id:Long,val name:String,val location:String)
@Serializable data class SiteDevice(val id:Long,val name:String)
@Serializable data class SiteConfig(val id:Long,val serverId:Long,val serverName:String,val serverLocation:String,val config:String) {
    override fun toString()="SiteConfig(id=$id, serverId=$serverId, config=[redacted])"
}
@Serializable data class SiteDeviceDetails(val id:Long,val name:String,val configs:List<SiteConfig>)
@Serializable data class SiteNotification(val id:Long,val serverId:Long,val serverName:String,val location:String,val createdAt:Long,val read:Boolean)
@Serializable data class SiteServerStatus(val id:Long,val hostReachable:Boolean,val latencyMs:Long?=null)
@Serializable data class SiteJobItem(val deviceId:Long,val state:String,val configId:Long?=null,val error:String?=null)
@Serializable data class SiteJob(val id:String,val state:String,val items:List<SiteJobItem>)
@Serializable private data class LoginBody(val phone:String,val password:String)
@Serializable private data class RegisterBody(val phone:String,val password:String,val nickname:String)
@Serializable private data class DeviceBody(val name:String)
@Serializable private data class JobBody(val serverId:Long,val deviceIds:List<Long>,val requestKey:String)
@Serializable private data class TokenBody(val accessToken:String)
@Serializable private data class ErrorBody(val message:String="Ошибка сервиса")

class ServiceException(val status:Int, message:String):Exception(message)

/** Shared authenticated contract for Android, Apple and desktop. Never logs tokens/configs. */
class InoagentApi internal constructor(rawClient:HttpClient,private val origin:String) {
    constructor():this(HttpClient(),SERVICE_ORIGIN)
    private val json=Json { ignoreUnknownKeys=true }
    private val client=rawClient.config {
        expectSuccess=false
        followRedirects=false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) { requestTimeoutMillis=30_000;connectTimeoutMillis=15_000;socketTimeoutMillis=30_000 }
    }
    private var token=""
    fun setToken(value:String) { token=value }
    fun artworkUrl(serverId:Long,name:String):String = "$origin/api/server-art/$serverId?v=${name.encodeURLParameter()}"

    @Throws(Exception::class)
    suspend fun login(phone:String,password:String):String {
        val response=client.post("$origin/api/auth/login") { contentType(ContentType.Application.Json);setBody(LoginBody(normalizeServicePhone(phone),password)) }
        checkResponse(response.status,response.bodyAsText())
        val result=json.decodeFromString<TokenBody>(response.bodyAsText()).accessToken
        token=result;return result
    }
    @Throws(Exception::class)
    suspend fun register(phone:String,password:String,nickname:String) {
        val response=client.post("$origin/api/auth/register") { contentType(ContentType.Application.Json);setBody(RegisterBody(normalizeServicePhone(phone),password,nickname.trim())) }
        checkResponse(response.status,response.bodyAsText())
    }
    private fun checkResponse(status:HttpStatusCode,body:String) {
        if(status.isSuccess()) return
        val message=when(status.value) {
            401->"Сессия истекла или неверный номер/пароль. Войдите снова"
            403->"Аккаунт ожидает подтверждения или доступ ограничен"
            else->runCatching { json.decodeFromString<ErrorBody>(body).message.take(300) }.getOrDefault("Сервис временно недоступен (${status.value})")
        }
        throw ServiceException(status.value,message)
    }
    private suspend inline fun <reified T> get(path:String):T {
        require(token.isNotBlank()) { "Войдите в аккаунт" }
        val response=client.get(origin+path) { bearerAuth(token) }
        checkResponse(response.status,response.bodyAsText());return response.body()
    }
    @Throws(Exception::class) suspend fun me():SiteUser=get("/api/auth/me")
    @Throws(Exception::class) suspend fun servers():List<SiteServer> =get("/api/servers")
    @Throws(Exception::class) suspend fun devices():List<SiteDevice> =get("/api/devices")
    @Throws(Exception::class) suspend fun device(id:Long):SiteDeviceDetails=get("/api/devices/$id")
    @Throws(Exception::class) suspend fun notifications():List<SiteNotification> =get("/api/notifications")
    @Throws(Exception::class) suspend fun statuses():List<SiteServerStatus> =get("/api/servers/status")
    @Throws(Exception::class) suspend fun createDevice(name:String):SiteDevice {
        val response=client.post("$origin/api/devices") { bearerAuth(token);contentType(ContentType.Application.Json);setBody(DeviceBody(name.take(100))) }
        checkResponse(response.status,response.bodyAsText());return response.body()
    }
    @Throws(Exception::class) suspend fun readNotification(id:Long) {
        val response=client.post("$origin/api/notifications/$id/read") { bearerAuth(token) }
        checkResponse(response.status,response.bodyAsText())
    }
    @Throws(Exception::class) suspend fun enqueue(deviceId:Long,serverId:Long,requestKey:String):SiteJob {
        val response=client.post("$origin/api/generation/jobs") { bearerAuth(token);contentType(ContentType.Application.Json);setBody(JobBody(serverId,listOf(deviceId),requestKey)) }
        checkResponse(response.status,response.bodyAsText());return response.body()
    }
    @Throws(Exception::class) suspend fun job(id:String):SiteJob =get("/api/generation/jobs/${id.encodeURLPathPart()}")

    /** Reuses the device's saved config; timeouts leave a durable job running for next attempt. */
    @Throws(Exception::class) suspend fun configuration(deviceId:Long,serverId:Long,requestKey:String):SiteConfig {
        device(deviceId).configs.firstOrNull { it.serverId==serverId }?.let { return it }
        val queued=enqueue(deviceId,serverId,requestKey)
        repeat(120) {
            val current=job(queued.id)
            val item=current.items.firstOrNull { it.deviceId==deviceId }
            if(item?.state=="failed") throw ServiceException(422,item.error ?: "Не удалось выпустить конфигурацию")
            if(item?.configId!=null || item?.state=="done" || current.state=="completed") {
                device(deviceId).configs.firstOrNull { it.serverId==serverId }?.let { return it }
            }
            delay(1500)
        }
        throw ServiceException(408,"Конфигурация ещё выпускается. Задание сохранено — попробуйте подключиться позже")
    }
    fun close() { client.close() }
}

fun normalizeServicePhone(value:String):String {
    var digits=value.filter { it in '0'..'9' }
    if(digits.length==10) digits="7$digits"
    if(digits.length==11 && digits.startsWith('8')) digits="7"+digits.drop(1)
    require(digits.length in 7..15) { "Проверьте номер телефона" }
    return "+$digits"
}
