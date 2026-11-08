package com.inoagent.vpn.shared

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.first
import kotlin.test.*

class AccountCacheTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `saved device config stays usable when service is offline and logout hides account`()=runTest {
        val user=SiteUser(7,"+79991234567","User")
        val server=SiteServer(5,"London","UK")
        val raw="[Interface]\nPrivateKey = secret\nS4 = 10\n[Peer]\nEndpoint = 82.148.252.240:1234"
        val entries=mutableMapOf("access-token" to "token","cached-account" to ServiceCacheCodec().encode(user,listOf(server)),"device-7" to "42","remote-7-5" to raw)
        val vault=object:ClientVault {
            override fun read(key:String)=entries[key]
            override fun write(key:String,value:String) { entries[key]=value }
            override fun remove(key:String) { entries.remove(key) }
        }
        var requests=0
        val api=InoagentApi(HttpClient(MockEngine { requests++;error("offline") }),"https://service.test")
        val controller=AccountController(backgroundScope,vault,"Test device",api)
        runCurrent()
        controller.state.first { it.user!=null && !it.busy }
        assertEquals(user,controller.state.value.user)
        val attempts=requests
        val prepared=controller.prepare()!!
        assertEquals(42L,prepared.deviceId);assertEquals(raw,prepared.content);assertEquals(attempts,requests)
        controller.logout();assertNull(controller.state.value.user);assertNull(entries["access-token"]);assertNull(entries["cached-account"])
        assertNull(controller.prepare());api.close()
    }
}
