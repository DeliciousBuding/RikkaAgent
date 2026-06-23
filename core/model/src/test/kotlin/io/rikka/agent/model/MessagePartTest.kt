package io.rikka.agent.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagePartTest {

    private val json = ChatMessage.json

    // ── Serialization round-trip for each MessagePart subtype ─────────────────

    @Test
    fun `Text serializes and deserializes`() {
        val part = MessagePart.Text("Hello, world!")
        val encoded = json.encodeToString(MessagePart.serializer(), part)
        val decoded = json.decodeFromString<MessagePart>(encoded)

        assertTrue(decoded is MessagePart.Text)
        assertEquals("Hello, world!", (decoded as MessagePart.Text).text)
    }

    @Test
    fun `Command serializes with exitCode`() {
        val part = MessagePart.Command("ls -la", exitCode = 0, startedAtEpochMs = 1700000000000)
        val encoded = json.encodeToString(MessagePart.serializer(), part)
        val decoded = json.decodeFromString<MessagePart>(encoded)

        assertTrue(decoded is MessagePart.Command)
        val cmd = decoded as MessagePart.Command
        assertEquals("ls -la", cmd.command)
        assertEquals(0, cmd.exitCode)
        assertEquals(1700000000000L, cmd.startedAtEpochMs)
        assertTrue(cmd.isFinished)
        assertTrue(cmd.isSuccess)
    }

    @Test
    fun `Command serializes with null exitCode`() {
        val part = MessagePart.Command("top", exitCode = null)
        val encoded = json.encodeToString(MessagePart.serializer(), part)
        val decoded = json.decodeFromString<MessagePart>(encoded)

        assertTrue(decoded is MessagePart.Command)
        val cmd = decoded as MessagePart.Command
        assertEquals("top", cmd.command)
        assertEquals(null, cmd.exitCode)
        assertEquals(false, cmd.isFinished)
    }

    @Test
    fun `Stdout serializes`() {
        val part = MessagePart.Stdout("total 42\n")
        val encoded = json.encodeToString(MessagePart.serializer(), part)
        val decoded = json.decodeFromString<MessagePart>(encoded)

        assertTrue(decoded is MessagePart.Stdout)
        assertEquals("total 42\n", (decoded as MessagePart.Stdout).text)
    }

    @Test
    fun `Stderr serializes`() {
        val part = MessagePart.Stderr("Permission denied\n")
        val encoded = json.encodeToString(MessagePart.serializer(), part)
        val decoded = json.decodeFromString<MessagePart>(encoded)

        assertTrue(decoded is MessagePart.Stderr)
        assertEquals("Permission denied\n", (decoded as MessagePart.Stderr).text)
    }

    @Test
    fun `Reasoning serializes with stepId`() {
        val part = MessagePart.Reasoning("Let me think...", stepId = "step-1")
        val encoded = json.encodeToString(MessagePart.serializer(), part)
        val decoded = json.decodeFromString<MessagePart>(encoded)

        assertTrue(decoded is MessagePart.Reasoning)
        val r = decoded as MessagePart.Reasoning
        assertEquals("Let me think...", r.text)
        assertEquals("step-1", r.stepId)
    }

    @Test
    fun `Code serializes with language`() {
        val part = MessagePart.Code("println(\"hello\")", language = "kotlin")
        val encoded = json.encodeToString(MessagePart.serializer(), part)
        val decoded = json.decodeFromString<MessagePart>(encoded)

        assertTrue(decoded is MessagePart.Code)
        val c = decoded as MessagePart.Code
        assertEquals("println(\"hello\")", c.code)
        assertEquals("kotlin", c.language)
    }

    @Test
    fun `Code serializes with null language`() {
        val part = MessagePart.Code("echo hi", language = null)
        val encoded = json.encodeToString(MessagePart.serializer(), part)
        val decoded = json.decodeFromString<MessagePart>(encoded)

        assertTrue(decoded is MessagePart.Code)
        assertEquals(null, (decoded as MessagePart.Code).language)
    }

    @Test
    fun `Error serializes with all fields`() {
        val part = MessagePart.Error(
            message = "Connection refused",
            cause = "ECONNREFUSED 127.0.0.1:22",
            code = 255,
        )
        val encoded = json.encodeToString(MessagePart.serializer(), part)
        val decoded = json.decodeFromString<MessagePart>(encoded)

        assertTrue(decoded is MessagePart.Error)
        val e = decoded as MessagePart.Error
        assertEquals("Connection refused", e.message)
        assertEquals("ECONNREFUSED 127.0.0.1:22", e.cause)
        assertEquals(255, e.code)
    }

    @Test
    fun `Mermaid serializes with caption`() {
        val part = MessagePart.Mermaid(
            definition = "graph TD; A-->B;",
            caption = "Architecture overview",
        )
        val encoded = json.encodeToString(MessagePart.serializer(), part)
        val decoded = json.decodeFromString<MessagePart>(encoded)

        assertTrue(decoded is MessagePart.Mermaid)
        val m = decoded as MessagePart.Mermaid
        assertEquals("graph TD; A-->B;", m.definition)
        assertEquals("Architecture overview", m.caption)
    }

    // ── Polymorphic discriminator in JSON ─────────────────────────────────────

    @Test
    fun `JSON contains type discriminator`() {
        val part = MessagePart.Stdout("hello")
        val encoded = json.encodeToString(MessagePart.serializer(), part)

        assertTrue(encoded.contains("\"type\":\"stdout\""))
    }

    @Test
    fun `list of mixed parts serializes correctly`() {
        val parts: List<MessagePart> = listOf(
            MessagePart.Command("uname -a"),
            MessagePart.Stdout("Linux host 5.15.0\n"),
            MessagePart.Stderr(""),
            MessagePart.Command("uname -a", exitCode = 0),
        )
        val encoded = json.encodeToString(parts)
        val decoded = json.decodeFromString<List<MessagePart>>(encoded)

        assertEquals(4, decoded.size)
        assertTrue(decoded[0] is MessagePart.Command)
        assertTrue(decoded[1] is MessagePart.Stdout)
        assertTrue(decoded[2] is MessagePart.Stderr)
        assertTrue(decoded[3] is MessagePart.Command)
        assertEquals(0, (decoded[3] as MessagePart.Command).exitCode)
    }

    // ── ChatMessage backward compatibility ────────────────────────────────────

    @Test
    fun `ChatMessage with legacy content field deserializes`() {
        // Simulate old JSON format: content as a direct string field, no parts
        val legacyJson = """
            {
                "id": "msg-1",
                "role": "User",
                "content": "hello world",
                "timestampMs": 1700000000000,
                "status": "Final"
            }
        """.trimIndent()

        val message = json.decodeFromString<ChatMessage>(legacyJson)

        assertEquals("msg-1", message.id)
        assertEquals(ChatRole.User, message.role)
        // content accessor should return the legacy string
        @Suppress("DEPRECATION")
        assertEquals("hello world", message.content)
    }

    @Test
    fun `ChatMessage with new parts field deserializes`() {
        val newJson = """
            {
                "id": "msg-2",
                "role": "Assistant",
                "parts": [
                    {"type": "text", "text": "Here is the output:"},
                    {"type": "stdout", "text": "OK\n"}
                ],
                "timestampMs": 1700000000000,
                "status": "Final"
            }
        """.trimIndent()

        val message = json.decodeFromString<ChatMessage>(newJson)

        assertEquals(2, message.parts.size)
        assertTrue(message.parts[0] is MessagePart.Text)
        assertTrue(message.parts[1] is MessagePart.Stdout)
        assertEquals("Here is the output:", message.textContent)
    }

    @Test
    fun `migrateToParts converts legacy content to Text part`() {
        @Suppress("DEPRECATION")
        val legacy = ChatMessage(
            id = "msg-3",
            role = ChatRole.User,
            _content = "old content",
            timestampMs = 1700000000000,
        )

        val migrated = legacy.migrateToParts()

        assertEquals(1, migrated.parts.size)
        assertTrue(migrated.parts[0] is MessagePart.Text)
        assertEquals("old content", (migrated.parts[0] as MessagePart.Text).text)
    }

    @Test
    fun `migrateToParts is idempotent for already-migrated messages`() {
        val message = ChatMessage.text(
            id = "msg-4",
            role = ChatRole.User,
            text = "already migrated",
        )

        val migrated = message.migrateToParts()

        assertEquals(message.parts, migrated.parts)
    }

    // ── ChatMessage helper methods ────────────────────────────────────────────

    @Test
    fun `ChatMessage textContent extracts text parts only`() {
        val message = ChatMessage(
            id = "msg-5",
            role = ChatRole.Assistant,
            parts = listOf(
                MessagePart.Text("line 1"),
                MessagePart.Stdout("stdout line"),
                MessagePart.Text("line 2"),
            ),
            timestampMs = 1700000000000,
        )

        assertEquals("line 1\nline 2", message.textContent)
    }

    @Test
    fun `ChatMessage commands extracts command parts`() {
        val message = ChatMessage(
            id = "msg-6",
            role = ChatRole.Assistant,
            parts = listOf(
                MessagePart.Command("ls", exitCode = 0),
                MessagePart.Stdout("file.txt\n"),
                MessagePart.Command("cat file.txt", exitCode = 1),
            ),
            timestampMs = 1700000000000,
        )

        assertEquals(2, message.commands.size)
        assertEquals("ls", message.commands[0].command)
        assertEquals("cat file.txt", message.commands[1].command)
        assertEquals("cat file.txt", message.lastCommand?.command)
    }

    @Test
    fun `ChatMessage stdoutText concatenates stdout parts`() {
        val message = ChatMessage(
            id = "msg-7",
            role = ChatRole.Assistant,
            parts = listOf(
                MessagePart.Command("echo hello"),
                MessagePart.Stdout("hel"),
                MessagePart.Stdout("lo\n"),
            ),
            timestampMs = 1700000000000,
        )

        assertEquals("hello\n", message.stdoutText)
    }

    @Test
    fun `ChatMessage stderrText concatenates stderr parts`() {
        val message = ChatMessage(
            id = "msg-8",
            role = ChatRole.Assistant,
            parts = listOf(
                MessagePart.Command("cat /nonexistent"),
                MessagePart.Stderr("cat: /nonexistent: "),
                MessagePart.Stderr("No such file or directory\n"),
            ),
            timestampMs = 1700000000000,
        )

        assertEquals("cat: /nonexistent: No such file or directory\n", message.stderrText)
    }

    // ── ChatMessage factory methods ───────────────────────────────────────────

    @Test
    fun `ChatMessage text factory creates correct message`() {
        val msg = ChatMessage.text("id-1", ChatRole.User, "hello")

        assertEquals(1, msg.parts.size)
        assertTrue(msg.parts[0] is MessagePart.Text)
        assertEquals("hello", msg.textContent)
        assertEquals(ChatRole.User, msg.role)
    }

    @Test
    fun `ChatMessage command factory creates command with output`() {
        val msg = ChatMessage.command(
            id = "id-2",
            command = "whoami",
            stdout = "root\n",
            stderr = "",
            exitCode = 0,
        )

        assertEquals(2, msg.parts.size) // Command + Stdout
        assertTrue(msg.parts[0] is MessagePart.Command)
        assertTrue(msg.parts[1] is MessagePart.Stdout)
        assertEquals("root\n", msg.stdoutText)
        assertEquals(MessageStatus.Final, msg.status)
    }

    @Test
    fun `ChatMessage command factory with running command has Streaming status`() {
        val msg = ChatMessage.command(
            id = "id-3",
            command = "sleep 100",
            exitCode = null,
        )

        assertEquals(1, msg.parts.size) // Command only, no output yet
        assertEquals(MessageStatus.Streaming, msg.status)
    }

    // ── Round-trip: ChatMessage with parts through JSON ───────────────────────

    @Test
    fun `full ChatMessage round-trip preserves parts`() {
        val original = ChatMessage(
            id = "rt-1",
            role = ChatRole.Assistant,
            parts = listOf(
                MessagePart.Command("docker ps", exitCode = 0, startedAtEpochMs = 1700000000000),
                MessagePart.Stdout("CONTAINER ID   IMAGE\n"),
                MessagePart.Reasoning("The container is running normally.", stepId = "s1"),
                MessagePart.Mermaid("graph LR; A-->B;"),
            ),
            timestampMs = 1700000000000,
            status = MessageStatus.Final,
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ChatMessage>(encoded)

        assertEquals(original.id, decoded.id)
        assertEquals(original.role, decoded.role)
        assertEquals(original.parts.size, decoded.parts.size)
        assertTrue(decoded.parts[0] is MessagePart.Command)
        assertTrue(decoded.parts[1] is MessagePart.Stdout)
        assertTrue(decoded.parts[2] is MessagePart.Reasoning)
        assertTrue(decoded.parts[3] is MessagePart.Mermaid)
        assertEquals(0, (decoded.parts[0] as MessagePart.Command).exitCode)
        assertEquals("s1", (decoded.parts[2] as MessagePart.Reasoning).stepId)
    }

    // ── Extension functions ───────────────────────────────────────────────────

    @Test
    fun `String toTextPart creates Text`() {
        val part = "hello".toTextPart()
        assertEquals(MessagePart.Text("hello"), part)
    }

    @Test
    fun `String toTextParts creates single-element list`() {
        val parts = "hello".toTextParts()
        assertEquals(1, parts.size)
        assertEquals(MessagePart.Text("hello"), parts[0])
    }
}
