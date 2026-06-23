package io.rikka.agent.testutil

import io.rikka.agent.model.AuthType
import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatRole
import io.rikka.agent.model.ChatThread
import io.rikka.agent.model.HostKeyPolicy
import io.rikka.agent.model.MessagePart
import io.rikka.agent.model.MessageStatus
import io.rikka.agent.model.ProfileGroup
import io.rikka.agent.model.SshProfile
import java.util.concurrent.atomic.AtomicLong

/**
 * Factory methods for creating test data objects.
 *
 * Provides convenient builders for domain model objects with sensible
 * defaults. All generated IDs are deterministic or sequential to
 * ensure test reproducibility.
 *
 * ## Usage
 *
 * ```kotlin
 * class MyTest {
 *     @Test
 *     fun testWithFactoryData() {
 *         val profile = TestDataFactory.createProfile()
 *         val thread = TestDataFactory.createThread(profileId = profile.id)
 *         val message = TestDataFactory.createTextMessage(
 *             role = ChatRole.User,
 *             content = "Hello"
 *         )
 *     }
 * }
 * ```
 */
object TestDataFactory {

    // ── ID Generators ──────────────────────────────────────────────────────────

    private val messageCounter = AtomicLong(0)
    private val threadCounter = AtomicLong(0)
    private val profileCounter = AtomicLong(0)

    /** Generate a unique message ID. */
    fun nextMessageId(): String = "msg-${messageCounter.incrementAndGet()}"

    /** Generate a unique thread ID. */
    fun nextThreadId(): String = "thread-${threadCounter.incrementAndGet()}"

    /** Generate a unique profile ID. */
    fun nextProfileId(): String = "profile-${profileCounter.incrementAndGet()}"

    /** Reset all counters. Call in @Before for deterministic tests. */
    fun resetCounters() {
        messageCounter.set(0)
        threadCounter.set(0)
        profileCounter.set(0)
    }

    // ── Profile Factory ────────────────────────────────────────────────────────

    /**
     * Create an [SshProfile] with sensible defaults.
     *
     * ```kotlin
     * val profile = TestDataFactory.createProfile(
     *     name = "Production Server",
     *     host = "10.0.1.50",
     *     username = "deploy",
     *     group = ProfileGroup.Production,
     *     tags = listOf("web", "nginx"),
     * )
     * ```
     */
    fun createProfile(
        id: String = nextProfileId(),
        name: String = "Test Server",
        host: String = "localhost",
        port: Int = 22,
        username: String = "testuser",
        authType: AuthType = AuthType.PublicKey,
        keyRef: String? = "~/.ssh/id_ed25519",
        hostKeyPolicy: HostKeyPolicy = HostKeyPolicy.TrustFirstUse,
        keepaliveIntervalSec: Int = 60,
        codexMode: Boolean = false,
        codexWorkDir: String? = null,
        codexApiKey: String? = null,
        group: ProfileGroup = ProfileGroup.None,
        tags: List<String> = emptyList(),
    ): SshProfile = SshProfile(
        id = id,
        name = name,
        host = host,
        port = port,
        username = username,
        authType = authType,
        keyRef = keyRef,
        hostKeyPolicy = hostKeyPolicy,
        keepaliveIntervalSec = keepaliveIntervalSec,
        codexMode = codexMode,
        codexWorkDir = codexWorkDir,
        codexApiKey = codexApiKey,
        group = group,
        tags = tags,
    )

    /**
     * Create a password-authenticated [SshProfile].
     */
    fun createPasswordProfile(
        id: String = nextProfileId(),
        name: String = "Password Server",
        host: String = "192.168.1.100",
        username: String = "admin",
    ): SshProfile = createProfile(
        id = id,
        name = name,
        host = host,
        username = username,
        authType = AuthType.Password,
        keyRef = null,
    )

    /**
     * Create a Codex-enabled [SshProfile].
     */
    fun createCodexProfile(
        id: String = nextProfileId(),
        name: String = "Codex Server",
        host: String = "codex.example.com",
        username: String = "codex",
        workDir: String? = "/home/codex/project",
        apiKey: String? = "test-api-key",
    ): SshProfile = createProfile(
        id = id,
        name = name,
        host = host,
        username = username,
        codexMode = true,
        codexWorkDir = workDir,
        codexApiKey = apiKey,
    )

    // ── Message Factory ────────────────────────────────────────────────────────

    /**
     * Create a text [ChatMessage].
     *
     * ```kotlin
     * val msg = TestDataFactory.createTextMessage(
     *     role = ChatRole.User,
     *     content = "Show disk usage"
     * )
     * ```
     */
    fun createTextMessage(
        id: String = nextMessageId(),
        role: ChatRole = ChatRole.User,
        content: String = "Test message",
        timestampMs: Long = System.currentTimeMillis(),
        status: MessageStatus = MessageStatus.Final,
    ): ChatMessage = ChatMessage.text(
        id = id,
        role = role,
        text = content,
        timestampMs = timestampMs,
        status = status,
    )

    /**
     * Create a user text message.
     */
    fun createUserMessage(
        id: String = nextMessageId(),
        content: String = "User message",
        timestampMs: Long = System.currentTimeMillis(),
    ): ChatMessage = createTextMessage(
        id = id,
        role = ChatRole.User,
        content = content,
        timestampMs = timestampMs,
    )

    /**
     * Create an assistant text message.
     */
    fun createAssistantMessage(
        id: String = nextMessageId(),
        content: String = "Assistant response",
        timestampMs: Long = System.currentTimeMillis(),
        status: MessageStatus = MessageStatus.Final,
    ): ChatMessage = createTextMessage(
        id = id,
        role = ChatRole.Assistant,
        content = content,
        timestampMs = timestampMs,
        status = status,
    )

    /**
     * Create a streaming assistant message.
     */
    fun createStreamingMessage(
        id: String = nextMessageId(),
        content: String = "Generating...",
    ): ChatMessage = createAssistantMessage(
        id = id,
        content = content,
        status = MessageStatus.Streaming,
    )

    /**
     * Create an error message.
     */
    fun createErrorMessage(
        id: String = nextMessageId(),
        content: String = "An error occurred",
        timestampMs: Long = System.currentTimeMillis(),
    ): ChatMessage = createTextMessage(
        id = id,
        role = ChatRole.Assistant,
        content = content,
        timestampMs = timestampMs,
        status = MessageStatus.Error,
    )

    /**
     * Create a canceled message.
     */
    fun createCanceledMessage(
        id: String = nextMessageId(),
        content: String = "Partial output",
        timestampMs: Long = System.currentTimeMillis(),
    ): ChatMessage = createTextMessage(
        id = id,
        role = ChatRole.Assistant,
        content = content,
        timestampMs = timestampMs,
        status = MessageStatus.Canceled,
    )

    /**
     * Create a command [ChatMessage] with output.
     *
     * ```kotlin
     * val msg = TestDataFactory.createCommandMessage(
     *     command = "df -h",
     *     stdout = "Filesystem      Size  Used Avail Use% Mounted on\n...",
     *     exitCode = 0
     * )
     * ```
     */
    fun createCommandMessage(
        id: String = nextMessageId(),
        command: String = "ls -la",
        stdout: String = "",
        stderr: String = "",
        exitCode: Int? = 0,
        timestampMs: Long = System.currentTimeMillis(),
    ): ChatMessage = ChatMessage.command(
        id = id,
        command = command,
        stdout = stdout,
        stderr = stderr,
        exitCode = exitCode,
        timestampMs = timestampMs,
    )

    /**
     * Create a message with custom parts.
     */
    fun createMessageWithParts(
        id: String = nextMessageId(),
        role: ChatRole = ChatRole.Assistant,
        parts: List<MessagePart> = listOf(MessagePart.Text("Default text")),
        timestampMs: Long = System.currentTimeMillis(),
        status: MessageStatus = MessageStatus.Final,
    ): ChatMessage = ChatMessage(
        id = id,
        role = role,
        parts = parts,
        timestampMs = timestampMs,
        status = status,
    )

    // ── Thread Factory ─────────────────────────────────────────────────────────

    /**
     * Create a [ChatThread] with optional messages.
     *
     * ```kotlin
     * val thread = TestDataFactory.createThread(
     *     profileId = "profile-1",
     *     title = "Disk usage check",
     *     messages = listOf(
     *         TestDataFactory.createUserMessage(content = "df -h"),
     *         TestDataFactory.createAssistantMessage(content = "total 8"),
     *     )
     * )
     * ```
     */
    fun createThread(
        id: String = nextThreadId(),
        title: String = "Test Thread",
        messages: List<ChatMessage> = emptyList(),
    ): ChatThread = ChatThread(
        id = id,
        title = title,
        messages = messages,
    )

    /**
     * Create a thread with a conversation.
     */
    fun createConversation(
        id: String = nextThreadId(),
        title: String = "Conversation",
        userMessages: List<String> = listOf("Hello", "How are you?"),
        assistantMessages: List<String> = listOf("Hi!", "I'm fine, thanks!"),
    ): ChatThread {
        require(userMessages.size == assistantMessages.size) {
            "userMessages and assistantMessages must have the same size"
        }
        val messages = userMessages.zip(assistantMessages).flatMap { (user, assistant) ->
            listOf(
                createUserMessage(content = user),
                createAssistantMessage(content = assistant),
            )
        }
        return createThread(id = id, title = title, messages = messages)
    }

    // ── Batch Factory ──────────────────────────────────────────────────────────

    /**
     * Create multiple profiles.
     */
    fun createProfiles(count: Int): List<SshProfile> =
        (1..count).map { createProfile(name = "Server $it") }

    /**
     * Create multiple messages.
     */
    fun createMessages(
        count: Int,
        role: ChatRole = ChatRole.User,
        contentPrefix: String = "Message",
    ): List<ChatMessage> =
        (1..count).map { createTextMessage(role = role, content = "$contentPrefix $it") }

    /**
     * Create a conversation with alternating user/assistant messages.
     */
    fun createAlternatingConversation(
        turns: Int = 3,
        userPrefix: String = "User question",
        assistantPrefix: String = "Assistant answer",
    ): List<ChatMessage> =
        (1..turns).flatMap { turn ->
            listOf(
                createUserMessage(content = "$userPrefix $turn"),
                createAssistantMessage(content = "$assistantPrefix $turn"),
            )
        }
}
