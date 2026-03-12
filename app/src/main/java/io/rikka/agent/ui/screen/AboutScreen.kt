package io.rikka.agent.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.rikka.agent.BuildConfig
import io.rikka.agent.R
import androidx.compose.ui.res.stringResource

private data class OssLib(
  val name: String,
  val license: String,
  val url: String,
)

private val libraries = listOf(
  OssLib("Jetpack Compose", "Apache 2.0", "developer.android.com"),
  OssLib("Material 3", "Apache 2.0", "m3.material.io"),
  OssLib("Kotlin", "Apache 2.0", "kotlinlang.org"),
  OssLib("sshj", "BSD 2-Clause", "github.com/hierynomus/sshj"),
  OssLib("BouncyCastle", "MIT", "bouncycastle.org"),
  OssLib("Room", "Apache 2.0", "developer.android.com"),
  OssLib("DataStore", "Apache 2.0", "developer.android.com"),
  OssLib("Koin", "Apache 2.0", "insert-koin.io"),
  OssLib("Coil", "Apache 2.0", "coil-kt.github.io/coil"),
  OssLib("kotlinx.serialization", "Apache 2.0", "github.com/Kotlin/kotlinx.serialization"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
  onBack: () -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.about)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineMedium,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "v${BuildConfig.VERSION_NAME}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = stringResource(R.string.about_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(modifier = Modifier.height(32.dp))

      // License
      Text(
        text = stringResource(R.string.section_license),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = stringResource(R.string.apache_license),
        style = MaterialTheme.typography.bodyMedium,
      )
      Text(
        text = stringResource(R.string.about_copyright),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(modifier = Modifier.height(32.dp))

      // OSS libs
      Text(
        text = stringResource(R.string.section_oss),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(modifier = Modifier.height(8.dp))

      libraries.forEach { lib ->
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        ) {
          Text(
            text = lib.name,
            style = MaterialTheme.typography.bodyMedium,
          )
          Text(
            text = "${lib.license} · ${lib.url}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
          )
        }
      }

      Spacer(modifier = Modifier.height(32.dp))
    }
  }
}
