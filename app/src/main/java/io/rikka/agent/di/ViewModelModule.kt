package io.rikka.agent.di

import io.rikka.agent.vm.ChatViewModel
import io.rikka.agent.vm.ProfileEditorViewModel
import io.rikka.agent.vm.ProfilesViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
  viewModel { ChatViewModel() }
  viewModel { ProfilesViewModel() }
  viewModel { (profileId: String?) -> ProfileEditorViewModel(profileId) }
}
