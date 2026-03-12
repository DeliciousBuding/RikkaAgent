package io.rikka.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.rikka.agent.nav.AppNavHost
import io.rikka.agent.storage.AppPreferences
import io.rikka.agent.ui.RikkaAgentTheme
import io.rikka.agent.ui.ThemeMode
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
  private val prefs: AppPreferences by inject()

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    setContent {
      val themeName by prefs.theme.collectAsState(initial = "system")
      val themeMode = when (themeName) {
        "light" -> ThemeMode.Light
        "dark" -> ThemeMode.Dark
        "amoled" -> ThemeMode.Amoled
        else -> ThemeMode.System
      }

      RikkaAgentTheme(themeMode = themeMode) {
        Surface(modifier = Modifier.fillMaxSize()) {
          AppNavHost()
        }
      }
    }
  }
}

