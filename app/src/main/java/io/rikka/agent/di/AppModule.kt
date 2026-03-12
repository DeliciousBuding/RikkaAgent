package io.rikka.agent.di

import androidx.room.Room
import io.rikka.agent.ssh.ContentUriKeyContentProvider
import io.rikka.agent.ssh.DataStoreKnownHostsStore
import io.rikka.agent.ssh.KeyContentProvider
import io.rikka.agent.ssh.KnownHostsStore
import io.rikka.agent.storage.AppPreferences
import io.rikka.agent.storage.ChatRepository
import io.rikka.agent.storage.ProfileStore
import io.rikka.agent.storage.RoomChatRepository
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
    ).fallbackToDestructiveMigration().build()
  }

  single { get<AppDatabase>().sshProfileDao() }
  single { get<AppDatabase>().chatMessageDao() }

  single { RoomChatRepository(get()) }

  single<ChatRepository> { get<RoomChatRepository>() }

  single { RoomProfileStore(get()) }

  single<ProfileStore> { get<RoomProfileStore>() }

  single { AppPreferences(androidContext()) }

  // SSH
  single<KnownHostsStore> { DataStoreKnownHostsStore(androidContext()) }
  single { ContentUriKeyContentProvider(androidContext()) }
  single<KeyContentProvider> { get<ContentUriKeyContentProvider>() }
}
