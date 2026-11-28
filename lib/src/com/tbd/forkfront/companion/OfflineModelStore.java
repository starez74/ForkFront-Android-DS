package com.tbd.forkfront.companion;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.system.ErrnoException;
import android.system.Os;
import android.os.Looper;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Copies a user-selected GGUF into private app storage; never uploads it. */
public final class OfflineModelStore {
  private static final String FILE_NAME = "offline-companion.gguf";
  private static final long MIN_BYTES = 150L * 1024 * 1024;
  private static final long MAX_BYTES = 800L * 1024 * 1024;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());

  public interface Callback { void complete(String error, long size); }

  public static File model(Context context) {
    return new File(new File(context.getFilesDir(), "offline-ai"), FILE_NAME);
  }

  public static boolean installed(Context context) {
    File file = model(context);
    return file.isFile() && file.length() >= MIN_BYTES && file.length() <= MAX_BYTES;
  }

  public static String sizeLabel(Context context) {
    File f = model(context);
    if (!installed(context)) return "No offline model imported";
    return "Local model ready • " + (f.length() / (1024 * 1024)) + " MB";
  }

  public void importFromUri(Context context, Uri uri, Callback callback) {
    final Context app = context.getApplicationContext();
    worker.execute(() -> {
      String error = null;
      long bytes = 0;
      File target = model(app);
      File folder = target.getParentFile();
      File temporary = new File(folder, "importing-model.tmp");
      try {
        if (!folder.exists() && !folder.mkdirs())
          throw new IllegalStateException("Unable to create private model directory");
        if (!folder.isDirectory())
          throw new IllegalStateException("Private model path is not a directory");
        // Some tablet installations created this directory without search (x)
        // permission. Repair existing directories as well as newly created ones
        // before attempting to open or copy a GGUF file inside it.
        try {
          Os.chmod(folder.getAbsolutePath(), 0700);
        } catch (ErrnoException permissionError) {
          throw new IllegalStateException("Cannot access private model directory", permissionError);
        }
        if (!folder.canExecute() || !folder.canWrite())
          throw new IllegalStateException("Private model directory is not writable");
        if (folder.getUsableSpace() < MIN_BYTES + 80L * 1024 * 1024)
          throw new IllegalStateException("Not enough free space for a GGUF");
        try (InputStream in = app.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(temporary)) {
          if (in == null) throw new IllegalStateException("File picker did not provide a model");
          byte[] buffer = new byte[64 * 1024];
          int n;
          while ((n = in.read(buffer)) != -1) {
            bytes += n;
            if (bytes > MAX_BYTES) throw new IllegalStateException(
                "Model is too large for the first tablet prototype (800 MB limit)");
            out.write(buffer, 0, n);
          }
          out.getFD().sync();
        }
        if (bytes < MIN_BYTES) throw new IllegalStateException(
            "This is too small to be a supported model file");
        byte[] magic = new byte[4];
        try (FileInputStream check = new FileInputStream(temporary)) {
          if (check.read(magic) != 4 || magic[0] != 'G' || magic[1] != 'G'
              || magic[2] != 'U' || magic[3] != 'F')
            throw new IllegalStateException("Not a GGUF model file");
        }
        if (target.exists() && !target.delete())
          throw new IllegalStateException("Cannot replace existing model");
        if (!temporary.renameTo(target))
          throw new IllegalStateException("Could not finish private model import");
      } catch (Exception e) {
        error = e.getMessage() == null ? "Unable to import GGUF" : e.getMessage();
      } finally {
        if (temporary.exists()) temporary.delete();
      }
      final long finalSize = bytes;
      final String finalError = error;
      main.post(() -> callback.complete(finalError, finalSize));
    });
  }

  public void close() { worker.shutdownNow(); }
}
