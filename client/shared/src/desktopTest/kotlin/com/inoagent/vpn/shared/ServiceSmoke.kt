package com.inoagent.vpn.shared

import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*

/** Explicit opt-in smoke test. The credential file belongs only to an approved disposable QA account. */
fun main()=runBlocking {
    val file=System.getenv("INOAGENT_QA_FILE") ?: error("Provide the disposable QA credential file")
    val credentials=Json.parseToJsonElement(File(file).readText()).jsonObject
    val api=InoagentApi()
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    try {
        val token=api.login(credentials.getValue("phone").jsonPrimitive.content,credentials.getValue("password").jsonPrimitive.content)
        val user=api.me();check(user.id==credentials.getValue("id").jsonPrimitive.long)
        val values=mutableMapOf("access-token" to token)
        val vault=object:ClientVault {
            override fun read(key:String)=values[key]
            override fun write(key:String,value:String) { values[key]=value }
            override fun remove(key:String) { values.remove(key) }
        }
        val controller=AccountController(scope,vault,"App QA smoke",api)
        withTimeout(45_000) { controller.state.first { it.user!=null && !it.busy } }
        val servers=controller.state.value.servers;check(servers.any { it.id==5L })
        controller.select(5)
        val first=controller.prepare() ?: error(controller.state.value.message ?: "Provisioning failed")
        check(first.content.contains("[Interface]") && first.content.contains("[Peer]") && first.content.contains("PrivateKey ="))
        check(AmneziaConfigMetadataParser.parse("qa.conf",first.content).endpoint.startsWith("82.148.252.240:"))
        val second=controller.prepare() ?: error("Repeat failed")
        check(first.deviceId==second.deviceId && first.content==second.content)
        check(api.device(first.deviceId).configs.count { it.serverId==5L }==1)
        println("Service smoke passed: login, server list, device binding, queued configuration, repeated config reuse; device=${first.deviceId}, servers=${servers.size}")
    } finally { scope.cancel();api.close() }
}
