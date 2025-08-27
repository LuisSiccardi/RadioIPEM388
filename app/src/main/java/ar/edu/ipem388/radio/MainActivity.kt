package ar.edu.ipem388.radio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import ar.edu.ipem388.radio.ui.MainScreen
import ar.edu.ipem388.radio.ui.theme.RadioIPEM388Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RadioIPEM388Theme {
                MainScreen()
            }
        }
    }
}
