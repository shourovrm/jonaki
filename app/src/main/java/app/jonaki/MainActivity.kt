package app.jonaki

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.jonaki.ui.JonakiApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            JonakiApp(
                application = application as JonakiApplication,
                onDarkThemeChange = ::useSystemBarIconsFor,
            )
        }
    }

    /**
     * The app's own Light/Dark choice can differ from the system's, so the bar
     * icons follow the theme the app actually shows.
     */
    private fun useSystemBarIconsFor(isDark: Boolean) {
        val style = if (isDark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
