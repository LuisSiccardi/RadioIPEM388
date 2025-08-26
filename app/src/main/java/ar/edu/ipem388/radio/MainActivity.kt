package ar.edu.ipem388.radio

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val reqNotifPerm = if (Build.VERSION.SDK_INT >= 33) {
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    } else null

    private fun send(action: String) {
        val i = android.content.Intent(this, RadioService::class.java).apply { this.action = action }
        ContextCompat.startForegroundService(this, i)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            reqNotifPerm?.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MaterialTheme {
                RadioScreen(
                    onPlay = { send(RadioService.ACTION_PLAY) },
                    onPause = { send(RadioService.ACTION_PAUSE) },
                    onStart = { send(RadioService.ACTION_START) },
                    onStop  = { send(RadioService.ACTION_STOP) }
                )
            }
        }
    }
}

@Composable
fun RadioScreen(
    onPlay: () -> Unit, onPause: () -> Unit,
    onStart: () -> Unit, onStop: () -> Unit
) {
    var playing by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Poné tu logo en res/drawable/logo_colegio.png (o comentá esta línea hasta tenerlo)
            Image(
                painter = painterResource(R.drawable.logo_colegio),
                contentDescription = null,
                modifier = Modifier.size(200.dp)
            )
            Spacer(Modifier.height(24.dp))
            Row {
                Button(
                    onClick = { if (!playing) { onStart(); onPlay(); playing = true } },
                    modifier = Modifier.width(160.dp)
                ) { Text("Reproducir") }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = { if (playing) { onPause(); playing = false } },
                    modifier = Modifier.width(160.dp)
                ) { Text("Pausar") }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onStop) { Text("Detener servicio") }
        }
    }
}
