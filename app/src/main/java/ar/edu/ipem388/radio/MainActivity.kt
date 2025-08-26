package ar.edu.ipem388.radio

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {

    private val reqNotifPerm =
        if (Build.VERSION.SDK_INT >= 33)
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
        else null

    // Estado "fuente de la verdad" que viene del Servicio
    private var uiPlaying by mutableStateOf(false)

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == RadioService.ACTION_STATE) {
                val isPlaying = intent.getBooleanExtra(RadioService.EXTRA_PLAYING, false)
                uiPlaying = isPlaying
            }
        }
    }

    private fun send(action: String) {
        val i = Intent(this, RadioService::class.java).apply { this.action = action }
        ContextCompat.startForegroundService(this, i)
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

        // Leer versión mostrable (sin BuildConfig)
        val appVersion: String = try {
            if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName ?: "—"
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionName ?: "—"
            }
        } catch (_: Exception) { "—" }

        // Estado inicial desde prefs (por si volvés a la app ya pausada)
        uiPlaying = getSharedPreferences("radio", MODE_PRIVATE).getBoolean("playing", false)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFCCBEBE)
                ) {
                    RadioScreen(
                        playing   = uiPlaying,
                        onPlay    = { send(RadioService.ACTION_START); send(RadioService.ACTION_PLAY) },
                        onPause   = { send(RadioService.ACTION_PAUSE) },
                        onExit    = { exitApp() },
                        appVersion = appVersion
                )
                }
            }
        }
    }
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(RadioService.ACTION_STATE)

        // Una sola llamada que sirve en <33 y >=33
        ContextCompat.registerReceiver(
            this,
            stateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        runCatching { unregisterReceiver(stateReceiver) }
    }
}

@Composable
fun RadioScreen(
    playing: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onExit: () -> Unit,
    appVersion: String
) {
    var showAbout by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current   // <- se obtiene aquí (contexto composable)

    Box(Modifier.fillMaxSize()) {

        // Centro: logo redondeado + botón circular (solo icono)
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.logo_colegio),
                contentDescription = null,
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(16.dp))
            )

            Spacer(Modifier.height(24.dp))

            val iconRes = if (playing) R.drawable.ic_pause_24 else R.drawable.ic_play_24
            IconButton(
                onClick = { if (playing) onPause() else onPlay() },
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
            Text(if (playing) "Reproduciendo" else "Detenido")
            Spacer(Modifier.height(56.dp))
        }

        // Footer: crédito + versión (link) + botón “Acerca de / Licencias”
        val sub = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val annotated = buildAnnotatedString {
                append("AtriaDev · ")
                pushStringAnnotation(tag = "url", annotation = "https://atriasur.org")
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                    append("atriasur.org")
                }
                pop()
                append("  —  v$appVersion")
            }
            ClickableText(
                text = annotated,
                style = MaterialTheme.typography.labelSmall.copy(color = sub),
                onClick = { pos ->
                    annotated.getStringAnnotations("url", pos, pos).firstOrNull()?.let {
                        uriHandler.openUri(it.item)
                    }
                }
            )
            TextButton(onClick = { showAbout = true }) {
                Text("Acerca de / Licencias", style = MaterialTheme.typography.labelSmall)
            }
        }

        // Botón OI (power) centrado abajo, un poco más arriba
        IconButton(
            onClick = onExit,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp)
                .size(72.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_power_24),
                contentDescription = "Salir",
                modifier = Modifier.size(56.dp),
                tint = Color.Unspecified
            )
        }
    }

    if (showAbout) {
        AboutDialog(
            onDismiss = { showAbout = false },
            onOpenPrivacy = { uriHandler.openUri("https://atriasur.org") },  // <- usamos el handler capturado
            onOpenLicenses = {
                showAbout = false
                showLicenses = true
            }
        )
    }

    if (showLicenses) {
        LicensesDialog(onDismiss = { showLicenses = false })
    }
}

@Composable
private fun AboutDialog(
    onDismiss: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenLicenses: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Acerca de") },
        text = {
            Column {
                Text("RadioIPEM388 — App de radio escolar en streaming.")
                Spacer(Modifier.height(8.dp))
                Text("Desarrollada por AtriaDev.")
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenPrivacy) { Text("Política de privacidad") }
        },
        dismissButton = {
            TextButton(onClick = onOpenLicenses) { Text("Licencias de terceros") }
        }
    )
}

@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scroll = rememberScrollState()
    val apache = remember {
        // Requiere res/raw/apache_2_0.txt
        runCatching {
            ctx.resources.openRawResource(R.raw.apache_2_0)
                .bufferedReader().use { it.readText() }
        }.getOrElse { "Apache License 2.0" }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Licencias de terceros") },
        text = {
            Column(Modifier.verticalScroll(scroll)) {
                Text("• ExoPlayer — Apache License 2.0\nhttps://github.com/androidx/media\n")
                Text("• AndroidX / Material — Apache License 2.0\nhttps://developer.android.com/jetpack/androidx\n")
                Spacer(Modifier.height(8.dp))
                Text(apache, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
