package ar.edu.ipem388.radio

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp


class MainActivity : ComponentActivity() {

    private val reqNotifPerm =
        if (Build.VERSION.SDK_INT >= 33)
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
        else null

    // Estado que refleja al Service (se actualiza por broadcast)
    private val playingState = mutableStateOf(false)

    private var stateReceiver: BroadcastReceiver? = null

    private fun svc(action: String) =
        Intent(this, RadioService::class.java).apply { this.action = action }

    // Reemplazá COMPLETO este método:
    private fun send(action: String) {
        val i = svc(action)

        // 1) Disparar la acción
        if (action == RadioService.ACTION_PLAY || action == RadioService.ACTION_START) {
            ContextCompat.startForegroundService(this, i)
            // 2) Feedback inmediato en UI
            playingState.value = true
        } else {
            startService(i)
            if (action == RadioService.ACTION_PAUSE || action == RadioService.ACTION_STOP) {
                // 2) Feedback inmediato en UI
                playingState.value = false
            }
        }

        // 3) Sincronización con el estado real del Service
        //    (una ahora y otra con un pequeño delay por si ExoPlayer aún no cambió)
        startService(svc(RadioService.ACTION_QUERY))
        Handler(Looper.getMainLooper()).postDelayed({
            startService(svc(RadioService.ACTION_QUERY))
        }, 800)
    }


    override fun onStart() {
        super.onStart()

        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == RadioService.ACTION_STATE) {
                    val isPlaying = intent.getBooleanExtra(RadioService.EXTRA_PLAYING, false)
                    playingState.value = isPlaying
                }
            }
        }
        stateReceiver = r

        val filter = IntentFilter(RadioService.ACTION_STATE)

        // ✅ Único llamado compatible con todas las API (evita el warning del flag)
        ContextCompat.registerReceiver(
            /* context = */ this,
            /* receiver = */ r,
            /* filter = */ filter,
            /* flags = */ ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Estado inicial (NO foreground)
        startService(svc(RadioService.ACTION_QUERY))
    }

    override fun onStop() {
        super.onStop()
        stateReceiver?.let { unregisterReceiver(it) }
        stateReceiver = null
    }

    private fun exitApp() {
        send(RadioService.ACTION_STOP)
        finishAndRemoveTask()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            reqNotifPerm?.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val appVersion: String = try {
            if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                ).versionName ?: "—"
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionName ?: "—"
            }
        } catch (_: Exception) { "—" }

        setContent {
            MaterialTheme {
                val playing by playingState

                RadioScreen(
                    playing = playing,
                    onPlay  = { send(RadioService.ACTION_PLAY) },
                    onPause = { send(RadioService.ACTION_PAUSE) },
                    onStart = { send(RadioService.ACTION_START) },
                    onStop  = { send(RadioService.ACTION_STOP) },
                    onExit  = { exitApp() },
                    appVersion = appVersion
                )
            }
        }
    }
}

@Composable
fun RadioScreen(
    playing: Boolean,
    onPlay: () -> Unit, onPause: () -> Unit,
    onStart: () -> Unit, onStop: () -> Unit,
    onExit: () -> Unit,
    appVersion: String
) {
    // Fondo + contenido apilado
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.fondobanner),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Layout sin align(): zona central + footer
        Column(Modifier.fillMaxSize()) {

            // Centro (ocupa el espacio disponible)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(250.dp))

                    val iconRes = if (playing) R.drawable.ic_pause_24 else R.drawable.ic_play_24
                    IconButton(
                        onClick = { if (playing) onPause() else { onStart(); onPlay() } },
                        modifier = Modifier.size(88.dp)
                    ) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = if (playing) "Pausar" else "Reproducir",
                            modifier = Modifier.size(72.dp),
                            tint = Color.Unspecified
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(text = if (playing) "Reproduciendo" else "Detenido")
                    Spacer(Modifier.height(56.dp))
                }
            }

            // Footer abajo SIEMPRE (incluye botón Salir a la derecha)
            FooterBar(
                onExit = { onStop(); onExit() },
                appVersion = appVersion
            )

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FooterBar(
    onExit: () -> Unit,
    appVersion: String
) {
    val uriHandler = LocalUriHandler.current
    var showChooser by remember { mutableStateOf(false) }
    var showApache  by remember { mutableStateOf(false) }
    var showMIT     by remember { mutableStateOf(false) }

    // Cargar texto desde /res/raw solo cuando se abre cada diálogo
    val ctx = LocalContext.current
    val apacheText by remember(showApache) {
        mutableStateOf(
            if (showApache)
                ctx.resources.openRawResource(R.raw.license_apache_2_0)
                    .bufferedReader().use { it.readText() }
            else ""
        )
    }
    val mitText by remember(showMIT) {
        mutableStateOf(
            if (showMIT)
                ctx.resources.openRawResource(R.raw.license_mit)
                    .bufferedReader().use { it.readText() }
            else ""
        )
    }

    val lineGap = 0.dp // mismo espacio entre renglones (ajustá a gusto)

    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Gray.copy(alpha = 0.9f))
                .padding(horizontal = 4.dp, vertical = 4.dp) // footer más bajo
                .heightIn(max = 60.dp) // altura total reducida
        ) {
            // Tres renglones centrados
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1) AtriaDev - Atriasur.org
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AtriaDev - ", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 10.sp))
                   // Text(" - ", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 10.sp))
                    TextButton(
                        onClick = { uriHandler.openUri("https://atriasur.org") },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Atriasur.org", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 10.sp))
                    }
                }

                Spacer(Modifier.height(lineGap))

                // 2) Términos - Licencias
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { uriHandler.openUri("https://atriasur.org/terms-and-conditions") },
                        contentPadding = PaddingValues(bottom = 3.dp)
                    ) { Text("Términos - ", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 10.sp)) }

                   // Text(" - ", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 10.sp))

                    TextButton(
                        onClick = { showChooser = true },
                        contentPadding = PaddingValues(bottom = 3.dp)
                    ) { Text("Licencias -  ", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 10.sp)) }
                    Text(
                        "v$appVersion",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 10.sp),
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

            }

            // Power dentro del footer, a la derecha
            IconButton(
                onClick = onExit,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(44.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_power_24),
                    contentDescription = "Salir",
                    modifier = Modifier.size(28.dp),
                    tint = Color.White
                )
            }
        }
    }

    // Chooser de licencias y diálogos
    if (showChooser) {
        AlertDialog(
            onDismissRequest = { showChooser = false },
            title = { Text("Licencias de código abierto") },
            text = {
                Column {
                    TextButton(onClick = { showChooser = false; showApache = true }, contentPadding = PaddingValues(0.dp)) {
                        Text("Apache 2.0")
                    }
                    TextButton(onClick = { showChooser = false; showMIT = true }, contentPadding = PaddingValues(0.dp)) {
                        Text("MIT")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChooser = false }) { Text("Cerrar") } }
        )
    }
    if (showApache) LicenseDialog("Apache License 2.0", apacheText) { showApache = false }
    if (showMIT)    LicenseDialog("MIT License",        mitText)    { showMIT = false }
}


@Composable
private fun LicenseDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(Modifier.heightIn(min = 120.dp, max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(text)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
