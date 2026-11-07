package com.inoagent.vpn.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inoagent.vpn.ConnectionState
import com.inoagent.vpn.HomeState
import com.inoagent.vpn.shared.VpnProfile
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

@Composable
fun InoagentApp(
    profiles: List<VpnProfile>,
    state: HomeState,
    onImport: () -> Unit,
    onSelect: (Long) -> Unit,
    onDelete: (VpnProfile) -> Unit,
    onToggleConnection: () -> Unit,
    onMessageShown: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }

    Box(Modifier.fillMaxSize().background(Night)) {
        AuroraBackground(Modifier.fillMaxSize())
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { BottomTabs(tab = tab, onTab = { tab = it }) },
        ) { padding ->
            AnimatedContent(targetState = tab, label = "screen") { selected ->
                if (selected == 0) {
                    HomeScreen(
                        modifier = Modifier.padding(padding),
                        profiles = profiles,
                        state = state,
                        onImport = onImport,
                        onSelect = onSelect,
                        onToggleConnection = onToggleConnection,
                    )
                } else {
                    ProfilesScreen(
                        modifier = Modifier.padding(padding),
                        profiles = profiles,
                        selectedId = state.selectedProfileId,
                        onImport = onImport,
                        onSelect = onSelect,
                        onDelete = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    profiles: List<VpnProfile>,
    state: HomeState,
    onImport: () -> Unit,
    onSelect: (Long) -> Unit,
    onToggleConnection: () -> Unit,
) {
    val selected = profiles.firstOrNull { it.id == state.selectedProfileId }
    val connected = state.connection == ConnectionState.Connected
    val busy = state.connection == ConnectionState.Connecting || state.connection == ConnectionState.Disconnecting

    Column(
        modifier = modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("INOAGENT", fontSize = 11.sp, letterSpacing = 4.sp, color = TextMuted)
                Text("VPN", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            }
            StatusPill(connected)
        }

        Spacer(Modifier.height(26.dp))
        Text(
            if (connected) "Защищённое соединение" else "Свобода принимает новую форму",
            fontSize = 13.sp,
            color = TextMuted,
        )
        Spacer(Modifier.height(8.dp))
        AnimatedContent(targetState = state.connection, label = "status") { value ->
            Text(
                when (value) {
                    ConnectionState.Disconnected -> "Не подключено"
                    ConnectionState.Connecting -> "Подключаем…"
                    ConnectionState.Connected -> elapsedLabel(state.connectedAtMillis)
                    ConnectionState.Disconnecting -> "Отключаем…"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = if (connected) Mint else TextPrimary,
            )
        }

        Spacer(Modifier.height(24.dp))
        ConnectionOrb(
            connected = connected,
            busy = busy,
            enabled = selected != null && !busy,
            onClick = onToggleConnection,
        )
        Spacer(Modifier.height(26.dp))

        if (selected == null) EmptyProfileCard(onImport)
        else SelectedProfileCard(selected, profiles, state.selectedProfileId, onSelect)

        Spacer(Modifier.height(16.dp))
        AnimatedVisibility(connected, enter = fadeIn(), exit = fadeOut()) { TrafficCard(state) }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ConnectionOrb(connected: Boolean, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "orb")
    val rotation by transition.animateFloat(
        0f, 360f,
        animationSpec = infiniteRepeatable(tween(if (connected) 2_600 else 7_000, easing = FastOutSlowInEasing)),
        label = "rotation",
    )
    val pulse by transition.animateFloat(
        0.96f, 1.04f,
        animationSpec = infiniteRepeatable(tween(1_500), RepeatMode.Reverse),
        label = "pulse",
    )
    val scale by animateFloatAsState(if (connected) pulse else 1f, label = "orbScale")
    val glow by animateColorAsState(if (connected) Mint else Lavender, label = "orbColor")

    Box(
        modifier = Modifier.size(224.dp).scale(scale).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(listOf(glow.copy(alpha = .30f), Color.Transparent)),
                radius = size.minDimension / 2,
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(Color.Transparent, glow, Color.White, glow, Color.Transparent)),
                startAngle = rotation,
                sweepAngle = 285f,
                useCenter = false,
                topLeft = Offset(10.dp.toPx(), 10.dp.toPx()),
                size = Size(size.width - 20.dp.toPx(), size.height - 20.dp.toPx()),
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
            )
            drawCircle(color = Color(0xD9111519), radius = size.minDimension * .405f)
            drawCircle(color = Color.White.copy(alpha = .08f), radius = size.minDimension * .405f, style = Stroke(1.dp.toPx()))
        }
        if (busy) {
            CircularProgressIndicator(color = glow, strokeWidth = 2.dp, modifier = Modifier.size(34.dp))
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (connected) "Отключить" else "Подключиться", fontWeight = FontWeight.SemiBold)
                Text(if (connected) "×" else "→", fontSize = 24.sp, color = glow)
            }
        }
    }
}

@Composable
private fun SelectedProfileCard(
    selected: VpnProfile,
    profiles: List<VpnProfile>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text("КОНФИГУРАЦИЯ", fontSize = 10.sp, letterSpacing = 2.sp, color = TextMuted)
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(if (profiles.size > 1) 116.dp else 74.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { ProfileRow(selected, selected = true, onClick = {}) }
            items(profiles.filter { it.id != selectedId }, key = { it.id }) { profile ->
                ProfileRow(profile, selected = false, onClick = { onSelect(profile.id) })
            }
        }
    }
}

@Composable
private fun EmptyProfileCard(onImport: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onImport),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Panel.copy(alpha = .9f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            OrbIcon("+")
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Добавить конфигурацию", fontWeight = FontWeight.Medium)
                Text("Выберите файл .conf", fontSize = 12.sp, color = TextMuted)
            }
            Text("→", color = Mint, fontSize = 22.sp)
        }
    }
}

@Composable
private fun TrafficCard(state: HomeState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Panel.copy(alpha = .88f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("↓", formatBytes(state.traffic.receivedBytes), "Получено", Mint)
            Box(Modifier.width(1.dp).height(42.dp).background(Color.White.copy(.08f)))
            Metric("↑", formatBytes(state.traffic.sentBytes), "Отправлено", Lavender)
            Box(Modifier.width(1.dp).height(42.dp).background(Color.White.copy(.08f)))
            Metric("●", "AWG", "Протокол", Color.White)
        }
    }
}

@Composable
private fun Metric(icon: String, value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$icon  $value", color = color, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.height(3.dp))
        Text(label, color = TextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun ProfilesScreen(
    modifier: Modifier,
    profiles: List<VpnProfile>,
    selectedId: Long?,
    onImport: () -> Unit,
    onSelect: (Long) -> Unit,
    onDelete: (VpnProfile) -> Unit,
) {
    Column(modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp)) {
        Spacer(Modifier.height(18.dp))
        Text("Конфигурации", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Text("Ваши зашифрованные профили AmneziaWG", color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color(0xFF062116)),
        ) { Text("＋  Импортировать из файла", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(18.dp))

        if (profiles.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Здесь появятся ваши конфигурации", color = TextMuted)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileManagementRow(
                        profile = profile,
                        selected = profile.id == selectedId,
                        onSelect = { onSelect(profile.id) },
                        onDelete = { onDelete(profile) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(profile: VpnProfile, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) PanelSoft else Panel.copy(alpha = .82f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            OrbIcon(if (selected) "⚡" else "○")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.name, fontWeight = FontWeight.Medium)
                Text(profile.endpoint, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (selected) Text("АКТИВНА", fontSize = 9.sp, letterSpacing = 1.sp, color = Mint)
        }
    }
}

@Composable
private fun ProfileManagementRow(
    profile: VpnProfile,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().border(
            width = 1.dp,
            color = if (selected) Mint.copy(alpha = .45f) else Color.White.copy(alpha = .05f),
            shape = RoundedCornerShape(20.dp),
        ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Panel.copy(alpha = .9f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OrbIcon(if (selected) "✓" else "◇")
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).clickable(onClick = onSelect)) {
                Text(profile.name, fontWeight = FontWeight.Medium)
                Text(profile.endpoint, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (selected) Text("Выбран", color = Mint, fontSize = 10.sp)
            }
            Text(
                "Удалить",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onDelete).padding(8.dp),
            )
        }
    }
}

@Composable
private fun OrbIcon(text: String) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(
            Brush.radialGradient(listOf(Mint.copy(.45f), MintDark.copy(.6f))),
        ),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Mint, fontSize = 17.sp) }
}

@Composable
private fun StatusPill(connected: Boolean) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(.055f)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (connected) Mint else TextMuted))
        Spacer(Modifier.width(7.dp))
        Text(if (connected) "Подключено" else "Отключено", fontSize = 11.sp, color = if (connected) Mint else TextMuted)
    }
}

@Composable
private fun BottomTabs(tab: Int, onTab: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp)
            .background(Color(0xF20A0D10)).padding(horizontal = 40.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabItem("⌂", "Главная", tab == 0) { onTab(0) }
        TabItem("≡", "Конфигурации", tab == 1) { onTab(1) }
    }
}

@Composable
private fun TabItem(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxHeight().width(120.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(icon, fontSize = 19.sp, color = if (selected) Mint else TextMuted)
        Text(label, fontSize = 10.sp, color = if (selected) Mint else TextMuted)
    }
}

@Composable
private fun AuroraBackground(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val shift by transition.animateFloat(
        -30f, 55f,
        animationSpec = infiniteRepeatable(tween(9_000), RepeatMode.Reverse),
        label = "shift",
    )
    Canvas(modifier) {
        drawCircle(
            brush = Brush.radialGradient(listOf(Mint.copy(.14f), Color.Transparent)),
            radius = size.width * .72f,
            center = Offset(size.width * .9f, size.height * .22f + shift),
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(Lavender.copy(.12f), Color.Transparent)),
            radius = size.width * .8f,
            center = Offset(size.width * -.08f, size.height * .68f - shift),
        )
    }
}

@Composable
private fun elapsedLabel(start: Long?): String {
    val elapsed by produceState(0L, start) {
        while (true) {
            value = if (start == null) 0 else ((System.currentTimeMillis() - start) / 1_000).coerceAtLeast(0)
            delay(1_000)
        }
    }
    val hours = elapsed / 3_600
    val minutes = (elapsed % 3_600) / 60
    val seconds = elapsed % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "${((bytes / 1_073_741_824.0) * 10).roundToLong() / 10.0} GB"
    bytes >= 1_048_576 -> "${((bytes / 1_048_576.0) * 10).roundToLong() / 10.0} MB"
    bytes >= 1_024 -> "${bytes / 1_024} KB"
    else -> "$bytes B"
}
