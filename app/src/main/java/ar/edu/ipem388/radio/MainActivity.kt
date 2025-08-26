package ar.edu.ipem388.radio

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape

class MainActivity : ComponentActivity() {

    private val reqNotifPerm =
        if (Build.VERSION.SDK_INT >= 33)
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
        else null

    private fun send(action: String) {
        val i = android.content.Intent(this, RadioService::class.java).apply { this.action = action }
        ContextCompat.startForegroundService(this, i)
    }

    private fun exitApp() {
        // Detenemos la radio y cerramos la tarea
        send(RadioService.ACTION_STOP)
        finishAndRemoveTask()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Pido permiso de notificaciones (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) {
            reqNotifPerm?.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Leer versión mostrable (sin BuildConfig)
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
                RadioScreen(
                    onPlay = { send(RadioService.ACTION_PLAY) },
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
    onPlay: () -> Unit, onPause: () -> Unit,
    onStart: () -> Unit, onStop: () -> Unit,
    onExit: () -> Unit,
    appVersion: String
) {
    // arranca en false → icono de PLAY
    var playing by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {

        // Centro: logo redondeado + botón circular (solo icono)
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo redondeado (comentá si no tenés el recurso)
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
                onClick = {
                    if (playing) { onPause(); playing = false }
                    else { onStart(); onPlay(); playing = true }
                },
                modifier = Modifier.size(88.dp) // área táctil
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = if (playing) "Pausar" else "Reproducir",
                    modifier = Modifier.size(72.dp),
                    tint = Color.Unspecified // respeta el violeta del drawable
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(if (playing) "Reproduciendo" else "Detenido")
            Spacer(Modifier.height(56.dp)) // espacio para que no pise el footer/power
        }

        // Botón OI (power) centrado abajo, un poco más arriba
        IconButton(
            onClick = {
                onStop()
                playing = false
                onExit()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp)  // ajustá este valor si querés
                .size(72.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_power_24),
                contentDescription = "Salir",
                modifier = Modifier.size(56.dp),
                tint = Color.Unspecified
            )
        }

        // Footer: crédito + versión
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val sub = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            Text("AtriaDev · Atria.org", style = MaterialTheme.typography.labelSmall, color = sub)
            Text("v$appVersion", style = MaterialTheme.typography.labelSmall, color = sub)
        }
    }
}
