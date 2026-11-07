package com.inoagent.vpn.clientui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import com.inoagent.vpn.shared.*

val Graphite=Color(0xff141415)
val Surface=Color(0xff1d1d20)
val Copper=Color(0xffe5aa86)
val Chalk=Color(0xfff0ede7)
val Muted=Color(0xffa9a5a0)
private val Line=Color(0xff353538)

data class TunnelUi(val connected:Boolean=false,val busy:Boolean=false,val status:String="Не подключено",val detail:String="",val received:Long=0,val sent:Long=0)

@Composable fun ClientApp(
    account:AccountState,profiles:List<VpnProfile>,selectedProfileId:Long?,tunnel:TunnelUi,
    artworkUrl:(SiteServer)->String,onLogin:(String,String)->Unit,onRegister:(String,String,String)->Unit,
    onLogout:()->Unit,onRefresh:()->Unit,onSelectServer:(Long)->Unit,onConnect:()->Unit,
    onImport:()->Unit,onSelectProfile:(Long)->Unit,onLocalConnect:()->Unit,onDeleteProfile:(VpnProfile)->Unit,
    onOpenWebsite:(String)->Unit,onReadNotification:(Long)->Unit,onDismissMessage:()->Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    MaterialTheme(colorScheme=darkColorScheme(primary=Copper,onPrimary=Graphite,secondary=Copper,secondaryContainer=Color(0xff3c2f27),onSecondaryContainer=Chalk,primaryContainer=Color(0xff3c2f27),onPrimaryContainer=Chalk,background=Graphite,onBackground=Chalk,surface=Surface,onSurface=Chalk,onSurfaceVariant=Muted,outline=Line)) {
        Surface(Modifier.fillMaxSize(),color=Graphite) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=24.dp).widthIn(max=1180.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Row(verticalAlignment=Alignment.CenterVertically) { RouteMark(Modifier.size(36.dp));Spacer(Modifier.width(10.dp));Text("inoagent",fontSize=24.sp);Text(" vpn",color=Muted,fontSize=12.sp) }
                    TextButton(onClick={onOpenWebsite("https://vpn.oksiuta.ru")}) { Text("Сайт") }
                }
                HorizontalDivider(color=Line)
                Row(Modifier.padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("Соединение","Импорт","Аккаунт").forEachIndexed { index,title->
                        FilterChip(selected=tab==index,onClick={tab=index},label={Text(title)})
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    if(account.message!=null) Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) { Text(account.message.orEmpty(),Modifier.weight(1f));TextButton(onClick=onDismissMessage){Text("Закрыть")} } }
                    when(tab) {
                        0 -> {
                            Text("Ваш маршрут",fontSize=32.sp)
                            Text("Один аккаунт. Все ваши устройства.",color=Muted)
                            Panel {
                                Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) { RouteSignal(account.busy || tunnel.busy,tunnel.connected,Modifier.size(240.dp,150.dp)) }
                                Text(if(account.busy) account.activity else tunnel.status,fontSize=20.sp)
                                if(tunnel.detail.isNotBlank()) Text(tunnel.detail,color=Muted)
                                Button(onClick=onConnect,enabled=!account.busy && !tunnel.busy && (account.user!=null || tunnel.connected),modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                                    Text(if(tunnel.connected) "Отключить" else if(account.busy || tunnel.busy) "Подключаемся…" else "Подключиться")
                                }
                                if(account.user==null) TextButton(onClick={tab=2}) { Text("Войти в аккаунт для подключения") }
                                if(tunnel.connected) Text("Получено ${bytes(tunnel.received)}   Отправлено ${bytes(tunnel.sent)}",color=Muted,fontSize=13.sp)
                            }
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                                Text("Серверы",fontSize=22.sp);TextButton(onClick=onRefresh,enabled=account.user!=null && !account.busy){Text("Обновить")}
                            }
                            if(account.user!=null && account.servers.isEmpty() && !account.busy) Text("Доступных серверов пока нет",color=Muted)
                            account.servers.forEach { server->
                                val status=account.statuses.firstOrNull { it.id==server.id }
                                ServerCard(server,artworkUrl(server),account.selectedServerId==server.id,enabled=!tunnel.connected && !tunnel.busy && !account.busy,onClick={onSelectServer(server.id)}) {
                                    if(status!=null) Text(if(status.hostReachable) "С хоста: ${status.latencyMs ?: 0} мс" else "С хоста: нет связи",fontSize=12.sp,color=if(status.hostReachable) Color(0xff71bdb0) else Color(0xffed9299))
                                }
                            }
                            val unread=account.notifications.filter { !it.read }
                            if(unread.isNotEmpty()) {
                                Text("Новые серверы",fontSize=22.sp)
                                unread.take(5).forEach { notification->
                                    ServerCard(SiteServer(notification.serverId,notification.serverName,notification.location),artworkUrl(SiteServer(notification.serverId,notification.serverName,notification.location)),false,true,{
                                        if(!tunnel.connected) onSelectServer(notification.serverId);onReadNotification(notification.id)
                                    }) { Text("Выбрать сервер",color=Copper,fontSize=12.sp) }
                                }
                            }
                        }
                        1 -> {
                            Text("Свои конфигурации",fontSize=28.sp)
                            Text("Импортированные файлы работают и без входа в аккаунт",color=Muted)
                            OutlinedButton(onClick=onImport,enabled=!tunnel.busy) { Text("Импортировать .conf") }
                            profiles.forEach { profile->
                                Panel {
                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                        RadioButton(selected=selectedProfileId==profile.id,onClick={onSelectProfile(profile.id)},enabled=!tunnel.connected && !tunnel.busy)
                                        Column(Modifier.weight(1f)) { Text(profile.name);Text(profile.endpoint,color=Muted,fontSize=12.sp) }
                                        TextButton(onClick={onDeleteProfile(profile)},enabled=!tunnel.busy && !tunnel.connected) { Text("Удалить") }
                                    }
                                }
                            }
                            if(profiles.isNotEmpty()) Button(onClick=onLocalConnect,enabled=!tunnel.busy && !account.busy) { Text(if(tunnel.connected) "Отключить" else "Подключить выбранный") }
                        }
                        else -> {
                            if(account.user==null) LoginForm(account.busy,onLogin,onRegister,onOpenWebsite)
                            else Panel {
                                Text(account.user?.nickname.orEmpty(),fontSize=28.sp);Text(account.user?.phone.orEmpty(),color=Muted)
                                Text("Устройства и конфигурации синхронизируются с vpn.oksiuta.ru. При первом подключении это устройство появится в вашем кабинете.",color=Muted)
                                OutlinedButton(onClick=onLogout,enabled=!tunnel.busy && !account.busy) { Text("Выйти из аккаунта") }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable private fun LoginForm(busy:Boolean,onLogin:(String,String)->Unit,onRegister:(String,String,String)->Unit,onOpen:(String)->Unit) {
    var phone by remember { mutableStateOf("") };var password by remember { mutableStateOf("") };var nickname by remember { mutableStateOf("") };var register by remember { mutableStateOf(false) }
    Panel {
        Text(if(register) "Создать аккаунт" else "С возвращением",fontSize=28.sp)
        Text("Ваш интернет. Ваш маршрут.",color=Muted)
        OutlinedTextField(phone,{phone=it},label={Text("Номер телефона")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!busy)
        if(register) OutlinedTextField(nickname,{nickname=it},label={Text("Имя")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!busy)
        OutlinedTextField(password,{password=it},label={Text("Пароль")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!busy)
        Button(onClick={if(register) onRegister(phone,password,nickname) else onLogin(phone,password);password=""},enabled=!busy && phone.isNotBlank() && password.isNotBlank() && (!register || nickname.isNotBlank()),modifier=Modifier.fillMaxWidth()) { Text(if(busy) "Подождите…" else if(register) "Зарегистрироваться" else "Войти") }
        if(busy) RouteSignal(true,false,Modifier.size(90.dp,56.dp))
        TextButton(onClick={register=!register},enabled=!busy) { Text(if(register) "Уже есть аккаунт" else "Создать аккаунт") }
        TextButton(onClick={onOpen("https://t.me/inoagentvpnbot?start=reset")}) { Text("Восстановить пароль через Telegram") }
    }
}

@Composable private fun Panel(content:@Composable ColumnScope.()->Unit) {
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,Line),colors=CardDefaults.cardColors(containerColor=Surface)) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp),content=content)
    }
}
@Composable private fun ServerCard(server:SiteServer,url:String,selected:Boolean,enabled:Boolean,onClick:()->Unit,extra:@Composable ()->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp,if(selected) Copper else Line,RoundedCornerShape(12.dp)).clickable(enabled=enabled,onClick=onClick).padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        AsyncImage(ImageRequest.Builder(LocalPlatformContext.current).data(url).decoderFactory(SvgDecoder.Factory()).build(),contentDescription=null,modifier=Modifier.size(64.dp,48.dp).clip(RoundedCornerShape(10.dp)))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) { Text(server.name);Text(server.location,color=Muted,fontSize=13.sp);extra() }
        if(selected) Text("✓",color=Copper,fontSize=22.sp)
    }
}
@Composable fun RouteMark(modifier:Modifier=Modifier) {
    Canvas(modifier) {
        val s=size.width/48
        val p=Path().apply { moveTo(8*s,34*s);lineTo(8*s,18*s);quadraticTo(8*s,8*s,18*s,8*s);lineTo(30*s,8*s);lineTo(30*s,18*s);lineTo(18*s,18*s);lineTo(18*s,34*s);close() }
        drawPath(p,Copper)
        val q=Path().apply { moveTo(40*s,14*s);lineTo(40*s,30*s);quadraticTo(40*s,40*s,30*s,40*s);lineTo(18*s,40*s);lineTo(18*s,30*s);lineTo(30*s,30*s);lineTo(30*s,14*s);close() }
        drawPath(q,Copper);drawCircle(Chalk,2*s,Offset(24*s,24*s))
    }
}
@Composable fun RouteSignal(busy:Boolean,connected:Boolean,modifier:Modifier=Modifier) {
    val transition=rememberInfiniteTransition(label="route")
    val phase=if(busy) transition.animateFloat(0f,1f,infiniteRepeatable(tween(1600,easing=LinearEasing)),label="packet").value else 0.6f
    Canvas(modifier) {
        val sx=size.width/100;val sy=size.height/64
        val p=Path().apply { moveTo(12*sx,32*sy);lineTo(26*sx,32*sy);quadraticTo(36*sx,32*sy,36*sx,22*sy);lineTo(36*sx,16*sy);quadraticTo(36*sx,8*sy,44*sx,8*sy);lineTo(56*sx,8*sy);quadraticTo(64*sx,8*sy,64*sx,16*sy);lineTo(64*sx,48*sy);quadraticTo(64*sx,56*sy,56*sx,56*sy);lineTo(44*sx,56*sy);quadraticTo(36*sx,56*sy,36*sx,48*sy);lineTo(36*sx,42*sy);quadraticTo(36*sx,32*sy,46*sx,32*sy);lineTo(88*sx,32*sy) }
        val color=if(connected) Color(0xff71bdb0) else Copper
        drawPath(p,color.copy(alpha=.18f),style=Stroke(1.3f*sx))
        if(connected && !busy) drawPath(p,color,style=Stroke(1.5f*sx,cap=StrokeCap.Round))
        else { val measure=PathMeasure();measure.setPath(p,false);val packet=Path();measure.getSegment((phase*measure.length-25*sx).coerceAtLeast(0f),phase*measure.length,packet);drawPath(packet,color,style=Stroke(1.8f*sx,cap=StrokeCap.Round)) }
        drawCircle(color,2.5f*sx,Offset(12*sx,32*sy));drawCircle(color,2.5f*sx,Offset(88*sx,32*sy));drawCircle(Chalk,2*sx,Offset(50*sx,32*sy))
    }
}
private fun bytes(value:Long)=when { value>=1_048_576->"${value/1_048_576} МБ";value>=1024->"${value/1024} КБ";else->"$value Б" }
