package io.rikka.agent.storage.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Instrumented migration test for [AppDatabase] v4 → v5.
 *
 * Run with:
 *   ./gradlew :core:storage:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class Migration4To5Test {

  @get:Rule
  val helper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    AppDatabase::class.java,
  )

  companion object {
    private const val DB_NAME = "migration-test"
  }

  /**
   * Helper: create a v4 database, insert test data, close, then migrate to v5.
   * Returns the migrated v5 database (caller must close).
   */
  private fun createV4WithData(): AppDatabase {
    // Build a v4 database using raw SQL (no Room annotations needed for old schema)
    val db = helper.createDatabase(DB_NAME, 4).apply {
      // Insert a thread
      execSQL(
        """
        INSERT INTO chat_threads (id, profileId, title, createdAtMs, updatedAtMs)
        VALUES ('t1', 'p1', 'Test Thread', 1000, 2000)
        """.trimIndent()
      )

      // Insert messages with various content
      execSQL(
        """
        INSERT INTO chat_messages (id, threadId, role, content, timestampMs, status)
        VALUES ('m1', 't1', 'User', 'Hello world', 1000, 'Final')
        """.trimIndent()
      )
      execSQL(
        """
        INSERT INTO chat_messages (id, threadId, role, content, timestampMs, status)
        VALUES ('m2', 't1', 'Assistant', 'Line 1
Line 2 "quoted" end', 1500, 'Final')
        """.trimIndent()
      )
      execSQL(
        """
        INSERT INTO chat_messages (id, threadId, role, content, timestampMs, status)
        VALUES ('m3', 't1', 'User', '', 2000, 'Final')
        """.trimIndent()
      )
      close()
    }

    // Migrate to v5
    return Room.databaseBuilder(
      InstrumentationRegistry.getInstrumentation().targetContext,
      AppDatabase::class.java,
      DB_NAME,
    )
      .addMigrations(Migrations.MIGRATION_4_5)
      .build()
      .also { it.openHelper.writableDatabase } // trigger migration
  }

  @Test
  @Throws(IOException::class)
  fun migrate4To5_addsPartsJsonColumn() {
    val db = createV4WithData()
    try {
      val cursor = db.query("SELECT partsJson FROM chat_messages WHERE id = 'm1'")
      assertTrue(cursor.moveToFirst())
      val partsJson = cursor.getString(0)
      cursor.close()

      assertTrue("partsJson should not be empty", partsJson.isNotEmpty())
      assertTrue("partsJson should be valid JSON array", partsJson.startsWith("["))
    } finally {
      db.close()
    }
  }

  @Test
  @Throws(IOException::class)
  fun migrate4To5_migratesContentToTextPart() {
    val db = createV4WithData()
    try {
      val cursor = db.query("SELECT partsJson, content FROM chat_messages WHERE id = 'm1'")
      assertTrue(cursor.moveToFirst())
      val partsJson = cursor.getString(0)
      val content = cursor.getString(1)
      cursor.close()

      // content column preserved for search
      assertEquals("Hello world", content)

      // partsJson wraps content as a Text part
      assertTrue(partsJson.contains("\"type\":\"text\""))
      assertTrue(partsJson.contains("\"text\":\"Hello world\""))
    } finally {
      db.close()
    }
  }

  @Test
  @Throws(IOException::class)
  fun migrate4To5_handlesQuotesAndNewlines() {
    val db = createV4WithData()
    try {
      val cursor = db.query("SELECT partsJson FROM chat_messages WHERE id = 'm2'")
      assertTrue(cursor.moveToFirst())
      val partsJson = cursor.getString(0)
      cursor.close()

      // Verify the JSON is valid and contains escaped content
      assertTrue("Should contain escaped quotes", partsJson.contains("\\\"quoted\\\""))
      assertTrue("Should contain escaped newlines", partsJson.contains("\\n"))
      assertTrue("Should contain the text", partsJson.contains("Line 1"))
      assertTrue("Should contain the text", partsJson.contains("Line 2"))
    } finally {
      db.close()
    }
  }

  @Test
  @Throws(IOException::class)
  fun migrate4To5_emptyContentGetsDefaultPartsJson() {
    val db = createV4WithData()
    try {
      val cursor = db.query("SELECT partsJson, content FROM chat_messages WHERE id = 'm3'")
      assertTrue(cursor.moveToFirst())
      val partsJson = cursor.getString(0)
      val content = cursor.getString(1)
      cursor.close()

      assertEquals("", content)
      assertEquals("[]", partsJson)
    } finally {
      db.close()
    }
  }

  @Test
  @Throws(IOException::class)
  fun migrate4To5_preservesThreadData() {
    val db = createV4WithData()
    try {
      val cursor = db.query("SELECT title FROM chat_threads WHERE id = 't1'")
      assertTrue(cursor.moveToFirst())
      assertEquals("Test Thread", cursor.getString(0))
      cursor.close()
    } finally {
      db.close()
    }
  }

  @Test
  @Throws(IOException::class)
  fun v5_insertAndReadMessageWithParts() {
    val db = createV4WithData()
    try {
      // Insert a new message with partsJson already populated
      db.execSQL(
        """
        INSERT INTO chat_messages (id, threadId, role, content, partsJson, timestampMs, status)
        VALUES ('m4', 't1', 'Assistant', 'echo hello',
          '[{"type":"command","command":"echo hello","exitCode":0},{"type":"stdout","text":"hello\n"}]',
          3000, 'Final')
        """.trimIndent()
      )

      val cursor = db.query("SELECT partsJson, content FROM chat_messages WHERE id = 'm4'")
      assertTrue(cursor.moveToFirst())
      val partsJson = cursor.getString(0)
      val content = cursor.getString(1)
      cursor.close()

      assertEquals("echo hello", content)
      assertTrue(partsJson.contains("\"type\":\"command\""))
      assertTrue(partsJson.contains("\"type\":\"stdout\""))
    } finally {
      db.close()
    }
  }
}
