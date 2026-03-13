package io.rikka.agent.ui.screen

import android.app.Activity
import android.app.Application
import android.app.Instrumentation.ActivityResult
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.rikka.agent.R
import io.rikka.agent.di.appModule
import io.rikka.agent.di.viewModelModule
import io.rikka.agent.test.TestDocumentsProvider
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

@RunWith(AndroidJUnit4::class)
class ProfileEditorSafPickerTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var app: Application

  @Before
  fun setUp() {
    app = ApplicationProvider.getApplicationContext()
    if (GlobalContext.getOrNull() == null) {
      startKoin {
        androidContext(app)
        modules(appModule, viewModelModule)
      }
    }
    Intents.init()
  }

  @After
  fun tearDown() {
    Intents.release()
  }

  @Test
  fun safPickerPersistsPermissionAndStoresKeyRef() {
    val uri = Uri.parse("content://${TestDocumentsProvider.AUTHORITY}/${TestDocumentsProvider.DOC_ID}")
    val resultData = Intent().apply {
      data = uri
      clipData = ClipData.newUri(app.contentResolver, "test-key", uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }
    Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
      .respondWith(ActivityResult(Activity.RESULT_OK, resultData))

    composeRule.setContent {
      MaterialTheme {
        ProfileEditorScreen(
          profileId = null,
          onBack = {},
          onSaved = {},
        )
      }
    }

    composeRule.onNodeWithText(app.getString(R.string.btn_select_file))
      .performScrollTo()
      .performClick()

    composeRule.onNodeWithText("test-key", substring = true)
      .assertIsDisplayed()

    composeRule.onNodeWithText(app.getString(R.string.snackbar_key_permission_not_persisted))
      .assertIsDisplayed()
  }
}
