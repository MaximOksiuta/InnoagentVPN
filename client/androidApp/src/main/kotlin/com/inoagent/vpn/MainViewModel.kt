package com.inoagent.vpn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inoagent.vpn.shared.VpnProfile
import com.inoagent.vpn.shared.AccountController
import com.inoagent.vpn.data.AndroidVault
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import com.inoagent.vpn.tunnel.TunnelTraffic
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ConnectionState { Disconnected, Connecting, Connected, Disconnecting }

data class HomeState(
    val selectedProfileId: Long? = null,
    val connection: ConnectionState = ConnectionState.Disconnected,
    val connectedAtMillis: Long? = null,
    val traffic: TunnelTraffic = TunnelTraffic(),
    val message: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as InoagentApplication).container
    private var serviceConnectionJob:kotlinx.coroutines.Job?=null
    val accountController=AccountController(viewModelScope,AndroidVault(application),"Android ${android.os.Build.MODEL}")
    val account=accountController.state
    val profiles = combine(container.profiles.profiles,account) { rows,state -> rows.filter { it.accountId==null || it.accountId==state.user?.id } }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home.asStateFlow()

    init {
        viewModelScope.launch {
            container.tunnel.stopRequests.drop(1).collect {
                serviceConnectionJob?.cancel()
                if(_home.value.connection==ConnectionState.Connecting) _home.value=_home.value.copy(connection=ConnectionState.Disconnected)
            }
        }
        viewModelScope.launch {
            container.tunnel.connected.collect { connected ->
                if(!connected && _home.value.connection==ConnectionState.Connected) _home.value=_home.value.copy(connection=ConnectionState.Disconnected,connectedAtMillis=null,traffic=TunnelTraffic())
            }
        }
        viewModelScope.launch {
            profiles.collect { items ->
                if (items.none { it.id==_home.value.selectedProfileId } && items.isNotEmpty()) {
                    _home.value = _home.value.copy(selectedProfileId = items.first().id)
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                if (_home.value.connection == ConnectionState.Connected) {
                    runCatching { container.tunnel.traffic() }.onSuccess {
                        _home.value = _home.value.copy(traffic = it)
                    }
                }
            }
        }
    }

    fun select(profileId: Long) {
        if (_home.value.connection == ConnectionState.Disconnected) {
            _home.value = _home.value.copy(selectedProfileId = profileId)
        }
    }

    fun importConfig(fileName: String, contents: String) = viewModelScope.launch {
        runCatching {
            container.tunnel.validate(contents)
            container.profiles.import(fileName, contents)
        }
            .onSuccess { id -> _home.value = _home.value.copy(selectedProfileId = id, message = "Конфигурация добавлена") }
            .onFailure { error -> _home.value = _home.value.copy(message = error.message ?: "Не удалось импортировать файл") }
    }

    fun connectAfterPermission() = viewModelScope.launch {
        val profile = profiles.value.firstOrNull { it.id == _home.value.selectedProfileId }
            ?: return@launch showMessage("Сначала добавьте конфигурацию")
        _home.value = _home.value.copy(connection = ConnectionState.Connecting, message = null)
        runCatching {
            container.tunnel.connect(profile, container.profiles.configFor(profile.id))
        }.onSuccess {
            _home.value = _home.value.copy(
                connection = ConnectionState.Connected,
                connectedAtMillis = System.currentTimeMillis(),
            )
        }.onFailure { error ->
            _home.value = _home.value.copy(
                connection = ConnectionState.Disconnected,
                message = error.message ?: "Подключение не удалось",
            )
        }
    }

    fun connectServerAfterPermission() = viewModelScope.launch {
        if(_home.value.connection!=ConnectionState.Disconnected || account.value.busy) return@launch
        _home.value=_home.value.copy(connection=ConnectionState.Connecting,message=null)
        try {
            getApplication<Application>().startForegroundService(android.content.Intent(getApplication(),com.inoagent.vpn.tunnel.VpnKeepAliveService::class.java))
            val prepared=accountController.prepare() ?: return@launch
            container.tunnel.validate(prepared.content)
            val id=container.profiles.saveRemote(prepared.userId,prepared.server.id,prepared.server.name,prepared.content)
            val metadata=com.inoagent.vpn.shared.AmneziaConfigMetadataParser.parse("${prepared.server.name}.conf",prepared.content)
            container.tunnel.connect(VpnProfile(id,metadata.suggestedName,metadata.endpoint,System.currentTimeMillis(),prepared.userId,prepared.server.id),prepared.content)
            _home.value=_home.value.copy(selectedProfileId=id,connection=ConnectionState.Connected,connectedAtMillis=System.currentTimeMillis())
        } catch(e:kotlinx.coroutines.CancellationException) { throw e }
        catch(e:Exception) { showMessage("Не удалось запустить VPN: ${e.message?.take(160) ?: "проверьте конфигурацию"}") }
        finally { if(_home.value.connection==ConnectionState.Connecting) _home.value=_home.value.copy(connection=ConnectionState.Disconnected);if(_home.value.connection!=ConnectionState.Connected) getApplication<Application>().stopService(android.content.Intent(getApplication(),com.inoagent.vpn.tunnel.VpnKeepAliveService::class.java)) }
    }.also { serviceConnectionJob=it }

    fun logout() = viewModelScope.launch {
        container.tunnel.disconnect()
        _home.value=HomeState()
        accountController.logout()
    }

    fun disconnect() = viewModelScope.launch {
        _home.value = _home.value.copy(connection = ConnectionState.Disconnecting)
        runCatching { container.tunnel.disconnect() }
            .onSuccess {
                _home.value = _home.value.copy(
                    connection = ConnectionState.Disconnected,
                    connectedAtMillis = null,
                    traffic = TunnelTraffic(),
                )
            }
            .onFailure { _home.value=_home.value.copy(connection=if(container.tunnel.connected.value) ConnectionState.Connected else ConnectionState.Disconnected);showMessage(it.message ?: "Не удалось отключиться") }
    }

    fun delete(profile: VpnProfile) = viewModelScope.launch {
        if (_home.value.connection != ConnectionState.Disconnected && profile.id == _home.value.selectedProfileId) {
            showMessage("Сначала отключите активный профиль")
            return@launch
        }
        container.profiles.delete(profile)
        if (_home.value.selectedProfileId == profile.id) {
            _home.value = _home.value.copy(
                selectedProfileId = profiles.value.firstOrNull { it.id != profile.id }?.id,
            )
        }
    }

    fun consumeMessage() { _home.value = _home.value.copy(message = null) }
    fun showMessage(value: String) { _home.value = _home.value.copy(message = value) }
}
