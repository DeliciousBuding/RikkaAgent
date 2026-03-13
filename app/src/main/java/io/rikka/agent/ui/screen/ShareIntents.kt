package io.rikka.agent.ui.screen

import android.content.Intent

internal object ShareIntents {

  fun plainText(text: String, chooserTitle: String): Intent {
    val share = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, text)
    }
    return Intent.createChooser(share, chooserTitle)
  }

  fun sessionExport(text: String, subject: String, chooserTitle: String): Intent {
    val share = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, text)
      putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    return Intent.createChooser(share, chooserTitle)
  }
}
