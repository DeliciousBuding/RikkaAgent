# Wireframes (Text)

These are **implementation-facing** wireframes (ASCII-ish) to clarify hierarchy, spacing intent, and component composition.

They are not pixel specs.

## 1) Chat Screen

```
┌─────────────────────────────────────────────┐
│  TopAppBar                                  │
│  [Profile name]                    [⋯ menu] │
│  Status: Connected • ping 22ms              │
├─────────────────────────────────────────────┤
│                                             │
│  Timeline (LazyColumn)                      │
│                                             │
│   ┌─────────────── CommandBubble ─────────┐ │
│   │ $ uptime                               │ │
│   │                           [copy][rerun]│ │
│   └────────────────────────────────────────┘ │
│                                             │
│   ┌────────────── OutputBubble ───────────┐ │
│   │ STDOUT                                 │ │
│   │ ┌────────── CodeBlock (text) ────────┐ │ │
│   │ │ 10:22 up  1 day,  3 users...       │ │ │
│   │ │ ...                                 │ │ │
│   │ └─────────────────────────────────────┘ │ │
│   │                           [copy][more]│ │
│   └────────────────────────────────────────┘ │
│                                             │
│   ┌────────────── StatusCard ─────────────┐ │
│   │ Host key verification required         │ │
│   │ SHA256: AbCd...                        │ │
│   │ [Trust & Continue]     [Cancel]        │ │
│   └────────────────────────────────────────┘ │
│                                             │
│                         [Jump to bottom ⬇]  │
├─────────────────────────────────────────────┤
│  InputBar (hazy surface optional)           │
│  [templates]  $ [text field........] [send] │
└─────────────────────────────────────────────┘
```

Composition:

- `TopAppBar`
- `Timeline`
  - `CommandBubble`
  - `OutputBubble`
  - `StatusCard`
- `JumpToBottom`
- `InputBar`

## 2) Output Bubble Variants

### Running

```
┌────────────── OutputBubble ────────────────┐
│ STDOUT                         Running… 12s│
│ ┌────────── CodeBlock (text) ────────────┐ │
│ │ ... streaming content ...              │ │
│ └────────────────────────────────────────┘ │
│ [Cancel]                           [copy]  │
└────────────────────────────────────────────┘
```

### Truncated

```
┌────────────── OutputBubble ────────────────┐
│ STDOUT                      Truncated 2.3MB│
│ ┌────────── CodeBlock (text) ────────────┐ │
│ │ ... last N lines ...                   │ │
│ └────────────────────────────────────────┘ │
│ [Export] [Copy all]                [expand] │
└────────────────────────────────────────────┘
```

### Error / stderr emphasized

```
┌────────────── OutputBubble ────────────────┐
│ STDERR                              Exit 127│
│ ┌────────── CodeBlock (stderr) ──────────┐ │
│ │ bash: foo: command not found           │ │
│ └────────────────────────────────────────┘ │
│ [Copy]                                    │
└────────────────────────────────────────────┘
```

## 3) Profiles List

```
┌─────────────────────────────────────────────┐
│ Profiles                            [+ Add] │
├─────────────────────────────────────────────┤
│  ProfileCard (host)                         │
│  name: prod                                │
│  user@host:port                             │
│  auth: key / agent-forward / password       │
│                                  [Connect]  │
│                                             │
│  ProfileCard                                │
│  ...                                        │
└─────────────────────────────────────────────┘
```

## 4) Profile Editor (Core Fields)

```
┌─────────────────────────────────────────────┐
│ Edit Profile                         [Save] │
├─────────────────────────────────────────────┤
│ Name                                    [ ] │
│ Host                                    [ ] │
│ Port                                    [ ] │
│ User                                    [ ] │
│                                             │
│ Auth method: [Key ▼]                        │
│ - Key: [Select key]                         │
│ - Agent forwarding: [toggle]                │
│ - Password: [••••••] (optional)             │
│                                             │
│ Known host key: [View] [Forget]             │
│                                             │
│ Advanced: timeout, keepalive, env vars…      │
└─────────────────────────────────────────────┘
```

