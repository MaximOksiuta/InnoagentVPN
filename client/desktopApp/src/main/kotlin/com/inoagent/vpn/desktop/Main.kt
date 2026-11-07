package com.inoagent.vpn.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import com.inoagent.vpn.clientui.*
import com.inoagent.vpn.shared.*
import kotlinx.coroutines.*
import java.awt.Desktop
import java.net.URI
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

fun main()=application {
    val scope=rememberCoroutineScope()
    val windows=System.getProperty("os.name").startsWith("Windows")
    val directory=remember { Path.of(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"),"InoagentVPN") }
    val vault=remember { if(windows) WindowsVault(directory.resolve("vault")) else PreviewVault() }
    val controller=remember { AccountController(scope,vault,"Windows ${System.getenv("COMPUTERNAME") ?: "PC"}") }
    val account by controller.state.collectAsState()
    val tunnel=remember { WindowsTunnel(directory.resolve("tunnel")) }
    var tunnelUi by remember { mutableStateOf(TunnelUi()) }
    val profileStore=remember { DesktopProfiles(vault) }
    var profiles by remember { mutableStateOf(profileStore.list()) }
    var selected by remember { mutableStateOf<Long?>(profiles.firstOrNull()?.id) }
    DisposableEffect(Unit) { onDispose { controller.api.close() } }
    fun disconnect() { scope.launch { tunnelUi=tunnelUi.copy(busy=true);try { tunnel.disconnect();tunnelUi=TunnelUi() } catch(e:Exception) { tunnelUi=tunnelUi.copy(busy=false,detail=e.message.orEmpty()) } } }
    fun connect(raw:String) { scope.launch { tunnelUi=tunnelUi.copy(busy=true,status="Подтвердите запуск VPN в Windows");try { tunnel.connect(raw);tunnelUi=TunnelUi(connected=true,status="VPN включён") } catch(e:Exception) { tunnelUi=TunnelUi(detail=e.message.orEmpty()) } } }
    LaunchedEffect(Unit) {
        if(windows) {
            while(isActive) { if(!tunnelUi.busy) { val running=withContext(Dispatchers.IO){tunnel.isRunning()};tunnelUi=tunnelUi.copy(connected=running,status=if(running) "VPN включён" else "Не подключено") };delay(3000) }
        }
    }
    Window(onCloseRequest=::exitApplication,title="Inoagent VPN",state=rememberWindowState(width=560.dp,height=880.dp)) {
        ClientApp(account,profiles,selected,tunnelUi,{controller.api.artworkUrl(it.id,it.name)},controller::login,controller::register,
            onLogout={scope.launch { if(tunnelUi.connected) { try { tunnel.disconnect();tunnelUi=TunnelUi() } catch(e:Exception) { tunnelUi=tunnelUi.copy(detail=e.message.orEmpty());return@launch } };controller.logout()}},
            onRefresh=controller::refresh,onSelectServer=controller::select,
            onConnect={if(tunnelUi.connected) disconnect() else scope.launch { val prepared=controller.prepare();if(prepared!=null) connect(prepared.content) }},
            onImport={
                val chooser=JFileChooser().apply { fileFilter=FileNameExtensionFilter("AmneziaWG config","conf") }
                if(chooser.showOpenDialog(window)==JFileChooser.APPROVE_OPTION) {
                    runCatching {
                        val file=chooser.selectedFile;require(file.length()<=512*1024) { "Файл больше 512 КБ" }
                        val profile=profileStore.import(file.name,file.readText());profiles=profileStore.list();selected=profile.id
                    }.onFailure { tunnelUi=tunnelUi.copy(detail=it.message.orEmpty()) }
                }
            },
            onSelectProfile={selected=it},onLocalConnect={if(tunnelUi.connected) disconnect() else selected?.let { connect(profileStore.config(it)) }},
            onDeleteProfile={profile->profileStore.delete(profile.id);profiles=profileStore.list();if(selected==profile.id) selected=profiles.firstOrNull()?.id},
            onOpenWebsite={Desktop.getDesktop().browse(URI(it))},onReadNotification={controller.readNotification(it)},onDismissMessage=controller::clearMessage
        )
    }
}
