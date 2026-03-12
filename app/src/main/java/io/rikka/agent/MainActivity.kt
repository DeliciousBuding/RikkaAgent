package io.rikka.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import io.rikka.agent.nav.AppNavHost
import io.rikka.agent.ui.RikkaAgentTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    setContent {
      RikkaAgentTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          AppNavHost()
        }
      }
    }
  }
}

