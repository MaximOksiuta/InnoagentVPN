package com.inoagent.vpn

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inoagent.vpn.clientui.ClientApp
import com.inoagent.vpn.clientui.TunnelUi
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Root() }
    }

    @Composable
    private fun Root() {
        val profiles by viewModel.profiles.collectAsStateWithLifecycle()
        val home by viewModel.home.collectAsStateWithLifecycle()
        val account by viewModel.account.collectAsStateWithLifecycle()
        var serviceConnection by rememberSaveable { mutableStateOf(false) }

        val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            runCatching {
                val name = contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "profile.conf"
                val bytes = contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().also { require(it.size <= 512 * 1024) { "Файл конфигурации слишком большой" } }
                } ?: error("Не удалось открыть файл")
                name to bytes.toString(Charsets.UTF_8)
            }.onSuccess { (name, contents) ->
                viewModel.importConfig(name, contents)
            }.onFailure { error ->
                viewModel.showMessage(error.message ?: "Не удалось прочитать файл")
            }
        }

        val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                if(serviceConnection) viewModel.connectServerAfterPermission() else viewModel.connectAfterPermission()
            }
            else viewModel.showMessage("Разрешение на VPN не предоставлено")
        }

        fun toggle(service:Boolean) {
            when(home.connection) {
                ConnectionState.Connected->viewModel.disconnect()
                ConnectionState.Disconnected->{
                    serviceConnection=service
                    val permission=VpnService.prepare(this)
                    if(permission!=null) vpnPermission.launch(permission)
                    else if(service) viewModel.connectServerAfterPermission() else viewModel.connectAfterPermission()
                }
                else->Unit
            }
        }
        val busy=home.connection in listOf(ConnectionState.Connecting,ConnectionState.Disconnecting)
        ClientApp(
                account=account,
                profiles = profiles.filter { it.accountId==null },
                selectedProfileId=home.selectedProfileId,
                tunnel=TunnelUi(home.connection==ConnectionState.Connected,busy,when(home.connection) {
                    ConnectionState.Connected->"Подключено"
                    ConnectionState.Connecting->"Подключаемся"
                    ConnectionState.Disconnecting->"Отключаемся"
                    else->"Не подключено"
                },home.message ?: "",home.traffic.receivedBytes,home.traffic.sentBytes),
                artworkUrl={viewModel.accountController.api.artworkUrl(it.id,it.name)},
                onLogin=viewModel.accountController::login,
                onRegister=viewModel.accountController::register,
                onLogout={viewModel.logout()},onRefresh=viewModel.accountController::refresh,
                onSelectServer=viewModel.accountController::select,
                onConnect={toggle(true)},
                onImport = { filePicker.launch(arrayOf("application/octet-stream", "text/plain")) },
                onSelectProfile = viewModel::select,
                onDeleteProfile = {viewModel.delete(it)},
                onLocalConnect = {toggle(false)},
                onOpenWebsite={ url->startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(url))) },
                onReadNotification={viewModel.accountController.readNotification(it)},
                onDismissMessage = viewModel.accountController::clearMessage,
            )
    }
}

