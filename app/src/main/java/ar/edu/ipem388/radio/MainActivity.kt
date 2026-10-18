package ar.edu.ipem388.radio

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val selected = mutableStateOf(AudioSource.RECORDING)
    private val phase = mutableStateOf(PlaybackPhase.STOPPED)
    private val active = mutableStateOf(false)
    private val detail = mutableStateOf("")
    private var receiver: BroadcastReceiver? = null
    private val notificationsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected.value = AudioSource.fromKey(getSharedPreferences(AudioSource.PREFS, MODE_PRIVATE)
            .getString(AudioSource.SELECTED, null))
        setContent {
            MaterialTheme {
                RadioScreen(selected.value, phase.value, active.value, detail.value,
                    onSelect = ::selectSource,
                    onToggle = { if (active.value) pause() else play() },
                    onExit = {
                        sendBroadcast(Intent(RadioService.ACTION_STOP).setPackage(packageName))
                        finishAndRemoveTask()
                    }, appVersion = BuildConfig.VERSION_NAME)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Querying must not create a service, make network requests or start playback.
        phase.value = PlaybackPhase.STOPPED; active.value = false; detail.value = ""
        val stateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.getStringExtra(RadioService.EXTRA_SOURCE) != selected.value.key) return
                phase.value = PlaybackPhase.fromKey(intent.getStringExtra(RadioService.EXTRA_PHASE))
                active.value = intent.getBooleanExtra(RadioService.EXTRA_ACTIVE, false)
                detail.value = intent.getStringExtra(RadioService.EXTRA_MESSAGE).orEmpty()
            }
        }
        receiver = stateReceiver
        ContextCompat.registerReceiver(this, stateReceiver, IntentFilter(RadioService.ACTION_STATE),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        sendBroadcast(Intent(RadioService.ACTION_QUERY).setPackage(packageName))
    }

    override fun onStop() {
        receiver?.let(::unregisterReceiver); receiver = null
        super.onStop()
    }

    private fun selectSource(next: AudioSource) {
        if (next == selected.value) return
        selected.value = next
        getSharedPreferences(AudioSource.PREFS, MODE_PRIVATE).edit()
            .putString(AudioSource.SELECTED, next.key).apply()
        active.value = false; phase.value = PlaybackPhase.STOPPED; detail.value = ""
        sendBroadcast(Intent(RadioService.ACTION_SELECT).setPackage(packageName)
            .putExtra(RadioService.EXTRA_SOURCE, next.key))
    }

    private fun play() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        active.value = true; phase.value = PlaybackPhase.CONNECTING; detail.value = ""
        ContextCompat.startForegroundService(this, Intent(this, RadioService::class.java)
            .setAction(RadioService.ACTION_PLAY).putExtra(RadioService.EXTRA_SOURCE, selected.value.key))
    }

    private fun pause() {
        active.value = false; phase.value = PlaybackPhase.PAUSED; detail.value = ""
        sendBroadcast(Intent(RadioService.ACTION_PAUSE).setPackage(packageName))
    }
}

@Composable
fun RadioScreen(
    source: AudioSource, phase: PlaybackPhase, active: Boolean, detail: String,
    onSelect: (AudioSource) -> Unit, onToggle: () -> Unit, onExit: () -> Unit, appVersion: String
) {
    val uriHandler = LocalUriHandler.current
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.fondobanner), contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .96f))) {
                    Column(Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Radio Escolar 388", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = source == AudioSource.RECORDING,
                                onClick = { onSelect(AudioSource.RECORDING) }, label = { Text("Programa grabado") },
                                modifier = Modifier.testTag("source_recording"))
                            FilterChip(selected = source == AudioSource.LIVE,
                                onClick = { onSelect(AudioSource.LIVE) }, label = { Text("En vivo") },
                                modifier = Modifier.testTag("source_live"))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(source.title, style = MaterialTheme.typography.titleLarge)
                        Text(if (source == AudioSource.RECORDING) "Archivo de la radio · 4:33 · Sin conexión"
                            else "Requiere que la emisora esté transmitiendo", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(16.dp))
                        IconButton(onClick = onToggle, modifier = Modifier.size(88.dp).testTag("play_pause")) {
                            Icon(painterResource(if (active) R.drawable.ic_pause_24 else R.drawable.ic_play_24),
                                contentDescription = if (active) "Pausar" else "Reproducir",
                                modifier = Modifier.size(72.dp), tint = Color.Unspecified)
                        }
                        Text(phase.label, modifier = Modifier.testTag("playback_status"))
                        if (detail.isNotBlank()) Text(detail, modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodyMedium)
                        if (source == AudioSource.RECORDING) TextButton(onClick = { uriHandler.openUri(AudioSource.EPISODE_PAGE) }) {
                            Text("Ver programa en iVoox")
                        }
                    }
                }
            }
            FooterBar(onExit = onExit, appVersion = appVersion)
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

    val lineGap = 0.dp

    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Gray.copy(alpha = 0.9f))
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .heightIn(max = 60.dp)
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AtriaDev - ", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 10.sp))
                    TextButton(
                        onClick = { uriHandler.openUri("https://atriasur.org") },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Atriasur.org", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 10.sp))
                    }
                }

                Spacer(Modifier.height(lineGap))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { uriHandler.openUri("https://atriasur.org/terms-and-conditions") },
                        contentPadding = PaddingValues(bottom = 3.dp)
                    ) { Text("Términos - ", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 10.sp)) }

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
