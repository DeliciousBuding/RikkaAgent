package io.rikka.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import io.rikka.agent.ui.RikkaAgentTheme
import io.rikka.agent.ui.screen.ChatScreen

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      RikkaAgentTheme {
        val systemUi = rememberSystemUiController()
        SideEffect {
          systemUi.setSystemBarsColor(color = androidx.compose.ui.graphics.Color.Transparent, darkIcons = true)
        }

        Surface(modifier = Modifier.fillMaxSize()) {
          ChatScreen()
        }
      }
    }
  }
}

