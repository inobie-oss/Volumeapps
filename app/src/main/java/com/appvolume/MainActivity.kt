package com.appvolume

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

private val Blue = Color(0xFF007AFF)
private val Green = Color(0xFF34C759)
private val Red = Color(0xFFFF3B30)
private val Gray = Color(0xFF8E8E93)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Screen() }
    }
}

@Composable
fun Screen() {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val apps by VolumeEngine.apps.collectAsState()
    val err by VolumeEngine.error.collectAsState()
    val shizukuOk = remember(tick) {
        try { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
        catch (e: Throwable) { false }
    }
    val overlayOk = remember(tick) { Settings.canDrawOverlays(ctx) }
    val running = remember(tick) { OverlayService.running }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000); tick++
            if (!OverlayService.running && Shizuku.pingBinder()) withContext(Dispatchers.Default) { VolumeEngine.tick() }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFF2F2F7))
            .verticalScroll(rememberScrollState()).padding(16.dp).padding(top = 24.dp)
    ) {
        Text("Volume Aplikasi", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Group("IZIN") {
            ItemRow("Shizuku", if (shizukuOk) "Terhubung" else "Ketuk untuk izinkan", shizukuOk) {
                try { Shizuku.requestPermission(0) } catch (e: Throwable) {}
            }
            HorizontalDivider(color = Color(0xFFE5E5EA))
            ItemRow("Tampil di atas aplikasi", if (overlayOk) "Diizinkan" else "Ketuk untuk izinkan", overlayOk) {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            }
        }
        Group("TOMBOL MELAYANG") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Aktifkan", Modifier.weight(1f), fontSize = 17.sp)
                Switch(
                    checked = running, enabled = shizukuOk && overlayOk,
                    colors = SwitchDefaults.colors(checkedTrackColor = Green, checkedThumbColor = Color.White),
                    onCheckedChange = { on ->
                        val i = Intent(ctx, OverlayService::class.java)
                        if (on) ContextCompat.startForegroundService(ctx, i) else ctx.stopService(i)
                    }
                )
            }
        }
        Group("APLIKASI BERSUARA") {
            if (apps.isEmpty()) Text("Putar suara di TikTok, YouTube, dll.", color = Gray, fontSize = 15.sp)
            apps.forEach { a ->
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(a.label + if (a.playing) "  ●" else "", Modifier.weight(1f), fontSize = 17.sp)
                    Text("${(a.volume * 100).toInt()}%", color = Gray, fontSize = 17.sp)
                }
                Slider(
                    value = a.volume, onValueChange = { VolumeEngine.setVolume(a.pkg, it) },
                    colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue, inactiveTrackColor = Color(0xFFE5E5EA))
                )
            }
        }
        err?.let { Text(it, color = Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp, start = 16.dp)) }
    }
}

@Composable
fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, fontSize = 13.sp, color = Color(0xFF6D6D72), modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 6.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        content = content
    )
}

@Composable
fun ItemRow(title: String, value: String, ok: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 17.sp)
        Text(value, color = if (ok) Green else Red, fontSize = 15.sp)
    }
}
