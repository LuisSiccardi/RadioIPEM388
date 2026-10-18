package ar.edu.ipem388.radio

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class PlaybackInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun prepareOwnPreviewPermissions() {
        if (Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(BuildConfig.APPLICATION_ID, Manifest.permission.POST_NOTIFICATIONS)
    }
    @After fun stopAudio() {
        compose.activity.sendBroadcast(Intent(RadioService.ACTION_STOP).setPackage(BuildConfig.APPLICATION_ID))
    }
    private fun awaitStatus(expected: String) {
        compose.waitUntil(15_000) {
            runCatching { compose.onNodeWithTag("playback_status").assertTextEquals(expected) }.isSuccess
        }
    }
    @Test fun recordingPlaysPausesResumesAndSourceChangeStopsIt() {
        compose.onNodeWithTag("source_recording").performClick()
        compose.onNodeWithTag("play_pause").performClick()
        awaitStatus("Reproduciendo")
        compose.onNodeWithContentDescription("Pausar").assertIsDisplayed()
        compose.onNodeWithTag("play_pause").performClick()
        awaitStatus("En pausa")
        compose.onNodeWithContentDescription("Reproducir").assertIsDisplayed()
        compose.onNodeWithTag("play_pause").performClick()
        awaitStatus("Reproduciendo")
        compose.onNodeWithContentDescription("Pausar").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        awaitStatus("Reproduciendo")
        compose.onNodeWithContentDescription("Pausar").assertIsDisplayed()
        compose.onNodeWithTag("source_live").performClick()
        awaitStatus("Detenido")
        compose.onNodeWithTag("source_recording").performClick()
        awaitStatus("Detenido")
        compose.onNodeWithText("El Molle de beber").assertIsDisplayed()
        compose.runOnUiThread {
            val root = compose.activity.window.decorView
            val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(image))
            File(compose.activity.filesDir, "radio_preview.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            image.recycle()
        }
    }
    @Test fun cancellingLiveConnectionDoesNotStartAudioLater() {
        compose.onNodeWithTag("source_live").performClick()
        compose.onNodeWithTag("play_pause").performClick()
        compose.onNodeWithTag("play_pause").performClick()
        awaitStatus("En pausa")
        compose.onNodeWithContentDescription("Reproducir").assertIsDisplayed()
        // Covers the resolver's deadline, including a response arriving after Pause.
        Thread.sleep(19_000)
        compose.onNodeWithTag("playback_status").assertTextEquals("En pausa")
        compose.onNodeWithTag("source_recording").performClick()
    }
}
