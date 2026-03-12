package io.rikka.agent.di

import io.rikka.agent.vm.ChatViewModel
import io.rikka.agent.vm.ProfileEditorViewModel
import io.rikka.agent.vm.ProfilesViewModel
import io.rikka.agent.vm.SettingsViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
  viewModel { (profileId: String) -> ChatViewModel(profileId, get(), get(), get(), get(), get(), androidApplication()) }
  viewModel { ProfilesViewModel(get()) }
  viewModel { (profileId: String?) -> ProfileEditorViewModel(profileId, get(), androidApplication()) }
  viewModel { SettingsViewModel(get()) }
}
