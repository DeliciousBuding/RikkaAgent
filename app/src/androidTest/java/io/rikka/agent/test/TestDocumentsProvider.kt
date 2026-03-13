package io.rikka.agent.test

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import java.io.File

class TestDocumentsProvider : DocumentsProvider() {

  override fun onCreate(): Boolean = true

  override fun queryRoots(projection: Array<out String>?): Cursor {
    val columns = projection ?: arrayOf(
      DocumentsContract.Root.COLUMN_ROOT_ID,
      DocumentsContract.Root.COLUMN_DOCUMENT_ID,
      DocumentsContract.Root.COLUMN_TITLE,
      DocumentsContract.Root.COLUMN_FLAGS,
      DocumentsContract.Root.COLUMN_MIME_TYPES,
    )
    return MatrixCursor(columns).apply {
      newRow().apply {
        add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)
        add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, DOC_ID)
        add(DocumentsContract.Root.COLUMN_TITLE, "Test Keys")
        add(DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_SUPPORTS_SEARCH)
        add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*")
      }
    }
  }

  override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
    createDocumentCursor(projection).apply { addDocumentRow(this, documentId) }

  override fun queryChildDocuments(
    parentDocumentId: String,
    projection: Array<out String>?,
    sortOrder: String?,
  ): Cursor = createDocumentCursor(projection).apply { addDocumentRow(this, DOC_ID) }

  override fun openDocument(
    documentId: String,
    mode: String,
    signal: CancellationSignal?,
  ): ParcelFileDescriptor {
    val context = context ?: error("Missing context")
    val file = File(context.cacheDir, "$documentId.key")
    if (!file.exists()) {
      file.writeText("-----BEGIN PRIVATE KEY-----\nTEST\n-----END PRIVATE KEY-----\n")
    }
    return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
  }

  private fun createDocumentCursor(projection: Array<out String>?): MatrixCursor {
    val columns = projection ?: arrayOf(
      DocumentsContract.Document.COLUMN_DOCUMENT_ID,
      DocumentsContract.Document.COLUMN_DISPLAY_NAME,
      DocumentsContract.Document.COLUMN_MIME_TYPE,
      DocumentsContract.Document.COLUMN_FLAGS,
      DocumentsContract.Document.COLUMN_SIZE,
    )
    return MatrixCursor(columns)
  }

  private fun addDocumentRow(cursor: MatrixCursor, documentId: String) {
    cursor.newRow().apply {
      add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId)
      add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, "test-key.pem")
      add(DocumentsContract.Document.COLUMN_MIME_TYPE, "application/octet-stream")
      add(
        DocumentsContract.Document.COLUMN_FLAGS,
        DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL,
      )
      add(DocumentsContract.Document.COLUMN_SIZE, 32L)
    }
  }

  companion object {
    const val AUTHORITY = "io.rikka.agent.test.documents"
    const val ROOT_ID = "test-root"
    const val DOC_ID = "test-key"
  }
}
