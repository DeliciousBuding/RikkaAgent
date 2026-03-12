package io.rikka.agent

import android.app.Application
import io.rikka.agent.di.appModule
import io.rikka.agent.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class RikkaAgentApp : Application() {
  override fun onCreate() {
    super.onCreate()
    startKoin {
      androidContext(this@RikkaAgentApp)
      modules(appModule, viewModelModule)
    }
  }
}
