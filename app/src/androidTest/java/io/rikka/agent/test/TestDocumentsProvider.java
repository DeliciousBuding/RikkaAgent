package io.rikka.agent.test;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.IOException;

public class TestDocumentsProvider extends ContentProvider {
  public static final String AUTHORITY = "io.rikka.agent.test.documents";
  public static final String DOC_ID = "test-key";

  @Override
  public boolean onCreate() {
    return true;
  }

  @Override
  public Cursor query(
      Uri uri,
      String[] projection,
      String selection,
      String[] selectionArgs,
      String sortOrder
  ) {
    String[] columns = projection != null ? projection : new String[] {"_display_name", "_size"};
    MatrixCursor cursor = new MatrixCursor(columns);
    cursor.newRow()
        .add("_display_name", "test-key.pem")
        .add("_size", 32L);
    return cursor;
  }

  @Override
  public String getType(Uri uri) {
    return "application/octet-stream";
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    return null;
  }

  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs) {
    return 0;
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
    return 0;
  }

  @Override
  public ParcelFileDescriptor openFile(Uri uri, String mode) {
    File file = new File(getContext().getCacheDir(), "test-key.pem");
    if (!file.exists()) {
      File parent = file.getParentFile();
      if (parent != null) {
        parent.mkdirs();
      }
      try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
        out.write("-----BEGIN PRIVATE KEY-----\nTEST\n-----END PRIVATE KEY-----\n".getBytes());
      } catch (IOException ignored) {
        // Best effort for tests.
      }
    }
    try {
      return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    } catch (java.io.FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
