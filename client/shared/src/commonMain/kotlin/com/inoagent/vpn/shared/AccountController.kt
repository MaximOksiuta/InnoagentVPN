package com.inoagent.vpn.shared

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlin.random.Random

/** Platform implementations encrypt secrets using Keystore, Keychain or Windows DPAPI. */
interface ClientVault {
    fun read(key:String):String?
    fun write(key:String,value:String)
    fun remove(key:String)
}

data class AccountState(
    val user:SiteUser?=null,val servers:List<SiteServer> =emptyList(),
    val notifications:List<SiteNotification> =emptyList(),val statuses:List<SiteServerStatus> =emptyList(),
    val selectedServerId:Long?=null,val busy:Boolean=false,val message:String?=null,
    val activity:String="",val deviceId:Long?=null
)
data class PreparedConfiguration(val userId:Long,val deviceId:Long,val server:SiteServer,val content:String)

class AccountController(
    private val scope:CoroutineScope,private val vault:ClientVault,private val deviceName:String,
    val api:InoagentApi=InoagentApi()
) {
    private val mutable=MutableStateFlow(AccountState())
    val state=mutable.asStateFlow()
    private val mutex=Mutex()
    private var operation:Job?=null
    init {
        operation=scope.launch {
            val token=runCatching { vault.read("access-token") }.getOrNull()
            if(!token.isNullOrBlank()) {
                api.setToken(token)
                runOperation("Восстанавливаем аккаунт") { refreshData() }
            }
        }
        scope.launch {
            while(isActive) {
                delay(30_000)
                if(state.value.user!=null && !state.value.busy) runCatching {
                    val rows=api.notifications();val statuses=api.statuses()
                    mutable.value=state.value.copy(notifications=rows,statuses=statuses)
                }
            }
        }
    }
    fun login(phone:String,password:String) {
        if(state.value.busy) return
        operation=scope.launch { runOperation("Входим в аккаунт") {
            val token=api.login(phone,password)
            val user=api.me()
            vault.write("access-token",token)
            mutable.value=state.value.copy(user=user)
            refreshData()
        } }
    }
    fun register(phone:String,password:String,nickname:String) {
        if(state.value.busy) return
        operation=scope.launch { runOperation("Создаём аккаунт") {
            api.register(phone,password,nickname)
            mutable.value=state.value.copy(message="Аккаунт создан. После подтверждения администратором можно войти")
        } }
    }
    fun refresh() {
        if(state.value.busy) return
        operation=scope.launch { runOperation("Обновляем серверы") { refreshData() } }
    }
    private suspend fun refreshData() {
        val user=api.me();val servers=api.servers()
        val notifications=api.notifications()
        val statuses=runCatching { api.statuses() }.getOrDefault(emptyList())
        mutable.value=state.value.copy(user=user,servers=servers,notifications=notifications,statuses=statuses,
            selectedServerId=(state.value.selectedServerId ?: vault.read("server-${user.id}")?.toLongOrNull())?.takeIf { id->servers.any { it.id==id } } ?: servers.firstOrNull()?.id,
            deviceId=vault.read("device-${user.id}")?.toLongOrNull())
    }
    fun select(serverId:Long) { if(!state.value.busy) { mutable.value=state.value.copy(selectedServerId=serverId);state.value.user?.let { vault.write("server-${it.id}",serverId.toString()) } } }
    fun clearMessage() { mutable.value=state.value.copy(message=null) }
    fun readNotification(id:Long) =scope.launch {
        runCatching { api.readNotification(id);mutable.value=state.value.copy(notifications=state.value.notifications.map { if(it.id==id) it.copy(read=true) else it }) }
    }
    fun logout() {
        operation?.cancel();api.setToken("");vault.remove("access-token");mutable.value=AccountState()
    }
    suspend fun prepare():PreparedConfiguration? {
        var prepared:PreparedConfiguration?=null
        runOperation("Готовим конфигурацию для этого устройства") {
            val user=state.value.user ?: error("Войдите в аккаунт")
            val server=state.value.servers.firstOrNull { it.id==state.value.selectedServerId } ?: error("Выберите сервер")
            val devices=api.devices()
            val installation=vault.read("installation") ?: randomKey().also { vault.write("installation",it) }
            val name="${deviceName.take(70)} · ${installation.take(6)}"
            val bound=vault.read("device-${user.id}")?.toLongOrNull()
            val device=devices.firstOrNull { it.id==bound } ?: devices.firstOrNull { it.name==name } ?: api.createDevice(name)
            vault.write("device-${user.id}",device.id.toString())
            mutable.value=state.value.copy(deviceId=device.id,activity="Выпускаем или загружаем конфигурацию")
            val config=api.configuration(device.id,server.id,"app-${randomKey()}")
            AmneziaConfigMetadataParser.parse("${server.name}.conf",config.config)
            prepared=PreparedConfiguration(user.id,device.id,server,config.config)
        }
        return prepared
    }
    private suspend fun runOperation(label:String,block:suspend ()->Unit) {
        if(!mutex.tryLock()) return
        mutable.value=state.value.copy(busy=true,activity=label,message=null)
        try { block() }
        catch(e:CancellationException) { throw e }
        catch(e:Exception) {
            if(e is ServiceException && e.status==401) { api.setToken("");vault.remove("access-token");mutable.value=AccountState(message=e.message) }
            else mutable.value=state.value.copy(message=if(e is ServiceException || e is IllegalArgumentException) e.message else "Не удалось связаться с сервисом. Проверьте интернет и повторите")
        }
        finally { mutable.value=state.value.copy(busy=false,activity="");mutex.unlock() }
    }
    private fun randomKey()=Random.nextBytes(16).joinToString("") { (it.toInt() and 255).toString(16).padStart(2,'0') }
}
