# Architecture (Draft)

This document describes the planned architecture for `rikka-agent`.

## Goals

- Beautiful, chat-style rendering of SSH command outputs
- Low-latency UX (connection reuse + streaming updates)
- Security by default (encrypted keys, strict host verification)
- Modular design so apps can reuse the SSH core without adopting the full UI

## Module Layout

- `:app` — Application entry, DI (Koin), Navigation, Screens, ViewModels
- `:core:model` — Data classes: SshProfile, ChatMessage, AuthType, HostKeyPolicy, enums
- `:core:storage` — Room database (AppDatabase, SshProfileEntity, DAO), DataStore (AppPreferences), ProfileStore
- `:core:ssh` — SSH interfaces: SshExecRunner, ExecEvent, SshSessionManager (implementation pending)
- `:core:ui` — Compose components: ChatBubble, ChatInput, AnsiStripper, Theme

## Navigation Graph

```
ProfilesScreen (start)
├── → ProfileEditorScreen (profileId: String?)
├── → Session/ChatScreen (profileId: String)
└── → SettingsScreen
```

Routes are defined as `@Serializable` data classes in `nav/Screen.kt` using Jetpack Navigation Compose with type-safe navigation.

## DI Architecture (Koin 3.5)

```
AppModule
├── AppDatabase (Room, singleton)
├── SshProfileDao (from AppDatabase)
├── RoomProfileStore (ProfileStore impl)
└── AppPreferences (DataStore)

ViewModelModule
├── ChatViewModel
├── ProfilesViewModel(RoomProfileStore)
├── ProfileEditorViewModel(profileId, RoomProfileStore)
└── SettingsViewModel(AppPreferences)
```

## Data Flow (Exec Mode)

1. UI triggers `runCommand(profileId, command)`
2. `SshSessionManager` ensures an authenticated SSH session exists
3. `SshExecRunner` opens an exec channel and emits a stream of events:
   - stdout chunk
   - stderr chunk
   - exit code
   - error / cancelled
4. UI collects the stream and updates an output message incrementally (with throttling)

