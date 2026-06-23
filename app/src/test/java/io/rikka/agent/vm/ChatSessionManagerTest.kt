package io.rikka.agent.vm

import io.rikka.agent.model.ChatMessage
import io.rikka.agent.model.ChatRole
import io.rikka.agent.model.ChatThread
import io.rikka.agent.model.MessagePart
import io.rikka.agent.model.MessageStatus
import io.rikka.agent.storage.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSessionManagerTest {

  private val profileId = "test-profile"

  // ── Thread CRUD ────────────────────────────────────────────────────────────

  @Test
  fun `newSession clears currentThreadId`() = runTest {
    val repo = FakeChatRepository(profileId)
    val manager = ChatSessionManager(profileId, repo, backgroundScope)

    // Create a thread first so currentThreadId is non-null
    val tid = repo.createThread(profileId, "t1")
    manager.switchThread(tid)
    assertEquals(tid, manager.currentThreadId)

    manager.newSession()
    assertNull(manager.currentThreadId)
  }

  @Test
  fun `switchThread sets currentThreadId`() = runTest {
    val repo = FakeChatRepository(profileId)
    val manager = ChatSessionManager(profileId, repo, backgroundScope)

    val tid = repo.createThread(profileId, "t1")
    manager.switchThread(tid)
    assertEquals(tid, manager.currentThreadId)
    assertTrue(manager.isCurrentThread(tid))
  }

  @Test
  fun `switchThread replaces previous thread`() = runTest {
    val repo = FakeChatRepository(profileId)
    val manager = ChatSessionManager(profileId, repo, backgroundScope)

    val tid1 = repo.createThread(profileId, "t1")
    val tid2 = repo.createThread(profileId, "t2")
    manager.switchThread(tid1)
    assertEquals(tid1, manager.currentThreadId)

    manager.switchThread(tid2)
    assertEquals(tid2, manager.currentThreadId)
    assertFalse(manager.isCurrentThread(tid1))
  }

  @Test
  fun `deleteThread returns true and clears current when deleting active thread`() = runTest {
    val repo = FakeChatRepository(profileId)
    val manager = ChatSessionManager(profileId, repo, backgroundScope)

    val tid = repo.createThread(profileId, "active")
    manager.switchThread(tid)

    val wasActive = manager.deleteThread(tid)
    assertTrue(wasActive)
    assertNull(manager.currentThreadId)
  }

  @Test
  fun `deleteThread returns false when deleting inactive thread`() = runTest {
    val repo = FakeChatRepository(profileId)
    val manager = ChatSessionManager(profileId, repo, backgroundScope)

    val activeTid = repo.createThread(profileId, "active")
    val inactiveTid = repo.createThread(profileId, "inactive")
    manager.switchThread(activeTid)

    val wasActive = manager.deleteThread(inactiveTid)
    assertFalse(wasActive)
    assertEquals(activeTid, manager.currentThreadId)
  }

  @Test
  fun `deleteThread removes from repository`() = runTest {
    val repo = FakeChatRepository(profileId)
    val manager = ChatSessionManager(profileId, repo, backgroundScope)

    val tid = repo.createThread(profileId, "t1")
    manager.deleteThread(tid)

    assertTrue(repo.getThreads().none { it.id == tid })
  }

  // ── Title auto-generation ─────────────────────────────────────────────────

  @Test
  fun `persistMessage creates thread on first call and auto-titles from user command`() = runTest {
    val repo = FakeChatRepository(profileId)
    val manager = ChatSessionManager(profileId, repo, backgroundScope)

    val msg = ChatMessage(
      id = "u-1",
      role = ChatRole.User,
      content = "ls -la",
      timestampMs = 1L,
      status = MessageStatus.Final,
    )
    manager.persistMessage(msg)
    advanceUntilIdle()

    assertNotNull(manager.currentThreadId)
    val tid = manager.currentThreadId!!
    val thread = repo.getThreads().first { it.id == tid }
    assertEquals("ls -la", thread.title)
  }

  @Test
  fun `persistMessage truncates long title to 50 chars`() = runTest {
    val repo = FakeChatRepository(profileId)
    val manager = ChatSessionManager(profileId, repo, backgroundScope)

    val longCommand = "a".repeat(60)
    val msg = ChatMessage(
      id = "u-1",
      role = ChatRole.User,
      content = longCommand,
      timestampMs = 1L,
      status = MessageStatus.Final,
    )
    manager.persistMessage(msg)
    advanceUntilIdle()

    val tid = manager.currentThreadId!!
    val thread = repo.getThreads().first { it.id == tid }
    assertEquals("a".repeat(50) + "…", thread.title)
  }

  @Test
  fun `persistMessage does not overwrite title for assistant message`() = runTest {
    val repo = FakeChatRepository(profileId)
    val manager = ChatSessionManager(profileId, repo, backgroundScope)

    // First: user message creates thread with auto-title
    val userMsg = ChatMessage(
      id = "u-1",
      role = ChatRole.User,
      content = "whoami",
      timestampMs = 1L,
      status = MessageStatus.Final,
    )
    manager.persistMessage(userMsg)
    advanceUntilIdle()

    // Second: assistant message should NOT change the title
    val assistantMsg = ChatMessage(
      id = "a-1",
      role = ChatRole.Assistant,
      content = "root",
      timestampMs = 2L,
      status = MessageStatus.Final,
    )
    manager.persistMessage(assistantMsg)
    advanceUntilIdle()

    val tid = manager.currentThreadId!!
    val thread = repo.getThreads().first { it.id == tid }
    assertEquals("whoami", thread.title)
  }

  @Test
  fun `persistMessage reuses existing thread for subsequent calls`() = runTest {
    val repo = FakeChatRepository(profileId)
    val manager = ChatSessionManager(profileId, repo, backgroundScope)

    val msg1 = ChatMessage(id = "u-1", role = ChatRole.User, content = "ls", timestampMs = 1L, status = MessageStatus.Final)
    val msg2 = ChatMessage(id = "a-1", role = ChatRole.Assistant, content = "ok", timestampMs = 2L, status = MessageStatus.Final)

    manager.persistMessage(msg1)
    advanceUntilIdle()
    val firstTid = manager.currentThreadId

    manager.persistMessage(msg2)
    advanceUntilIdle()
    assertEquals(firstTid, manager.currentThreadId)

    val messages = manager.getMessages(firstTid!!)
    assertEquals(2, messages.size)
  }

  // ── getMessages ────────────────────────────────────────────────────────────

  @Test
  fun `getMessages returns persisted messages`() = runTest {
    val repo = FakeChatRepository(profileId)
    val manager = ChatSessionManager(profileId, repo, backgroundScope)

    val tid = repo.createThread(profileId, "test")
    val msg = ChatMessage(id = "u-1", role = ChatRole.User, content = "pwd", timestampMs = 1L, status = MessageStatus.Final)
    repo.insertMessage(tid, msg)

    val result = manager.getMessages(tid)
    assertEquals(listOf(msg), result)
  }

  // ── Sidebar: isCurrentThread ───────────────────────────────────────────────

  @Test
  fun `isCurrentThread returns false when no thread selected`() = runTest {
    val repo = FakeChatRepository(profileId)
    val manager = ChatSessionManager(profileId, repo, backgroundScope)

    assertFalse(manager.isCurrentThread("any-thread"))
  }

  @Test
  fun `isCurrentThread returns true only for current thread`() = runTest {
    val repo = FakeChatRepository(profileId)
    val manager = ChatSessionManager(profileId, repo, backgroundScope)

    val tid1 = repo.createThread(profileId, "t1")
    val tid2 = repo.createThread(profileId, "t2")
    manager.switchThread(tid1)

    assertTrue(manager.isCurrentThread(tid1))
    assertFalse(manager.isCurrentThread(tid2))
  }

  @Test
  fun `threads StateFlow reflects repository changes`() = runTest {
    val repo = FakeChatRepository(profileId)
    val collector = backgroundScope.launch { repo.threadFlow.collect { } }
    val manager = ChatSessionManager(profileId, repo, backgroundScope)

    assertEquals(0, manager.threads.value.size)

    repo.createThread(profileId, "t1")
    assertEquals(1, manager.threads.value.size)

    repo.createThread(profileId, "t2")
    assertEquals(2, manager.threads.value.size)

    collector.cancel()
  }

  // ── persistUpdate ──────────────────────────────────────────────────────────

  @Test
  fun `persistUpdate updates message content and status`() = runTest {
    val repo = FakeChatRepository(profileId)
    val manager = ChatSessionManager(profileId, repo, backgroundScope)

    val tid = repo.createThread(profileId, "test")
    val msg = ChatMessage(id = "a-1", role = ChatRole.Assistant, content = "", timestampMs = 1L, status = MessageStatus.Streaming)
    repo.insertMessage(tid, msg)

    manager.persistUpdate("a-1", "hello", MessageStatus.Final)
    advanceUntilIdle()

    val updated = repo.getMessages(tid).first { it.id == "a-1" }
    assertEquals(MessageStatus.Final, updated.status)
    assertEquals(listOf(MessagePart.Text("hello")), updated.parts)
  }

  @Test
  fun `persistUpdate with empty content produces empty parts`() = runTest {
    val repo = FakeChatRepository(profileId)
    val manager = ChatSessionManager(profileId, repo, backgroundScope)

    val tid = repo.createThread(profileId, "test")
    val msg = ChatMessage(id = "a-1", role = ChatRole.Assistant, content = "old", timestampMs = 1L, status = MessageStatus.Streaming)
    repo.insertMessage(tid, msg)

    manager.persistUpdate("a-1", "", MessageStatus.Final)
    advanceUntilIdle()

    val updated = repo.getMessages(tid).first { it.id == "a-1" }
    assertEquals(emptyList<MessagePart>(), updated.parts)
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private fun <T> Iterable<T>.first(predicate: (T) -> Boolean): T =
    firstOrNull(predicate) ?: throw NoSuchElementException("No element matching predicate")

  /** Minimal fake ChatRepository for unit testing. */
  private class FakeChatRepository(
    private val profileId: String,
  ) : ChatRepository {
    private val threads = linkedMapOf<String, ChatThread>()
    private val messagesByThread = linkedMapOf<String, MutableList<ChatMessage>>()
    val threadFlow = MutableStateFlow(emptyList<ChatThread>())
    private var nextThreadId = 1

    fun getThreads(): List<ChatThread> = threads.values.toList()

    override fun observeThreads(profileId: String): Flow<List<ChatThread>> = threadFlow

    override suspend fun createThread(profileId: String, title: String): String {
      val id = "thread-${nextThreadId++}"
      threads[id] = ChatThread(id = id, title = title, messages = emptyList())
      messagesByThread[id] = mutableListOf()
      threadFlow.update { threads.values.toList() }
      return id
    }

    override suspend fun deleteThread(threadId: String) {
      threads.remove(threadId)
      messagesByThread.remove(threadId)
      threadFlow.update { threads.values.toList() }
    }

    override suspend fun insertMessage(threadId: String, message: ChatMessage) {
      messagesByThread.getOrPut(threadId) { mutableListOf() }.add(message)
    }

    override suspend fun updateMessage(id: String, parts: List<MessagePart>, status: MessageStatus) {
      messagesByThread.values.forEach { list ->
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
          list[index] = list[index].copy(parts = parts, status = status)
          return
        }
      }
    }

    override fun observeMessages(threadId: String): Flow<List<ChatMessage>> =
      flowOf(messagesByThread[threadId].orEmpty().toList())

    override suspend fun getMessages(threadId: String): List<ChatMessage> =
      messagesByThread[threadId].orEmpty().toList()

    override suspend fun updateThreadTitle(threadId: String, title: String) {
      val existing = threads[threadId] ?: return
      threads[threadId] = existing.copy(title = title)
      threadFlow.update { threads.values.toList() }
    }
  }
}
