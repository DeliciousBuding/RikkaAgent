package io.rikka.agent.di

import androidx.room.Room
import io.rikka.agent.storage.AppPreferences
import io.rikka.agent.storage.ProfileStore
import io.rikka.agent.storage.RoomProfileStore
import io.rikka.agent.storage.db.AppDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
  single {
    Room.databaseBuilder(
      androidContext(),
      AppDatabase::class.java,
      "rikka_agent.db",
    ).build()
  }

  single { get<AppDatabase>().sshProfileDao() }

  single<ProfileStore> { RoomProfileStore(get()) }

  single { AppPreferences(androidContext()) }
}
