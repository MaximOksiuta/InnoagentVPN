package com.inoagent.vpn.tunnel

import android.content.Context
import com.inoagent.vpn.shared.VpnProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.backend.TunnelActionHandler
import org.amnezia.awg.config.Config
import java.io.ByteArrayInputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TunnelTraffic(val receivedBytes: Long = 0, val sentBytes: Long = 0)

class AmneziaTunnelEngine(context: Context) {
    private val applicationContext=context.applicationContext
    private val backend = GoBackend(context.applicationContext, NoOpTunnelActionHandler)
    @Volatile private var active: NamedTunnel? = null
    val activeProfileId:Long? get()=active?.profileId
    @Volatile var connectedAtMillis:Long?=null
        private set
    private val mutableState=MutableStateFlow(false)
    val connected=mutableState.asStateFlow()
    private val mutableStop=MutableStateFlow(0)
    val stopRequests=mutableStop.asStateFlow()
    private val mutex=Mutex()

    suspend fun validate(rawConfig: String) = withContext(Dispatchers.IO) {
        Config.parse(ByteArrayInputStream(rawConfig.toByteArray()))
        Unit
    }

    suspend fun connect(profile: VpnProfile, rawConfig: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
        active?.let { backend.setState(it, Tunnel.State.DOWN, null) }
        val tunnel = NamedTunnel("inoagent-"+profile.id.toString(36).takeLast(6),profile.id)
        val config = Config.parse(ByteArrayInputStream(rawConfig.toByteArray()))
        applicationContext.startForegroundService(android.content.Intent(applicationContext,VpnKeepAliveService::class.java))
        try { backend.setState(tunnel, Tunnel.State.UP, config) }
        catch(e:Exception) { applicationContext.stopService(android.content.Intent(applicationContext,VpnKeepAliveService::class.java));throw e }
        active = tunnel
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        mutableStop.value+=1
        mutex.withLock {
        active?.let { backend.setState(it, Tunnel.State.DOWN, null) }
        active = null
        applicationContext.stopService(android.content.Intent(applicationContext,VpnKeepAliveService::class.java))
        }
    }

    suspend fun traffic(): TunnelTraffic = withContext(Dispatchers.IO) {
        val tunnel = active ?: return@withContext TunnelTraffic()
        val statistics = backend.getStatistics(tunnel)
        TunnelTraffic(statistics.totalRx(), statistics.totalTx())
    }

    private inner class NamedTunnel(private val value: String,val profileId:Long) : Tunnel {
        override fun getName(): String = value
        override fun onStateChange(newState: Tunnel.State) {
            connectedAtMillis=if(newState==Tunnel.State.UP) connectedAtMillis ?: System.currentTimeMillis() else null
            mutableState.value=newState==Tunnel.State.UP
            if(newState==Tunnel.State.DOWN) applicationContext.stopService(android.content.Intent(applicationContext,VpnKeepAliveService::class.java))
        }
        override fun isIpv4ResolutionPreferred(): Boolean = false
        override fun isMetered(): Boolean = false
    }

    private object NoOpTunnelActionHandler : TunnelActionHandler {
        override fun runPreUp(commands: Collection<String>) = Unit
        override fun runPostUp(commands: Collection<String>) = Unit
        override fun runPreDown(commands: Collection<String>) = Unit
        override fun runPostDown(commands: Collection<String>) = Unit
    }
}
