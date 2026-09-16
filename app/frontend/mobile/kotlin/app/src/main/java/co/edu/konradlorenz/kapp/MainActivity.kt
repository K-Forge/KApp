package co.edu.konradlorenz.kapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import co.edu.konradlorenz.kapp.ui.navigation.KAppNavHost
import co.edu.konradlorenz.kapp.ui.theme.KAppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge to edge on purpose: the login band runs under the status bar and the system paints
        // its icons on top. Forcing the dark style keeps those icons white, which is the only way
        // they read against #522567 - the automatic style would pick dark icons on a light theme.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setContent {
            KAppTheme {
                KAppNavHost()
            }
        }
    }
}
