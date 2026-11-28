package com.tbd.forkfront.companion;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Optional, offline, PC-built NetHackWiki FTS4 index. No network or writes. */
public final class OfflineWikiIndex {
  private static final int MAX_QUERY_TERMS = 6;
  private static final int MAX_PASSAGE_CHARS = 640;
  private OfflineWikiIndex() {}

  public static File database(Context context) {
    return new File(new File(context.getFilesDir(), "offline-ai"), "wiki-index.sqlite");
  }

  public static boolean installed(Context context) { return database(context).isFile(); }

  public static List<OfflineKnowledge.Note> find(Context context, String question, int maxResults) {
    List<OfflineKnowledge.Note> results = new ArrayList<>();
    if (question == null || maxResults < 1 || !installed(context)) return results;
    StringBuilder query = new StringBuilder();
    int terms = 0;
    String titleKeyword = null;
    for (String word : question.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
      if (word.length() < 4 || word.equals("what") || word.equals("should")
          || word.equals("does") || word.equals("have") || word.equals("with")
          || word.equals("when") || word.equals("about") || word.equals("avoid")
          || word.equals("could") || word.equals("would") || word.equals("your")) continue;
      if (terms++ >= MAX_QUERY_TERMS) break;
      if (titleKeyword == null) titleKeyword = word;
      if (query.length() != 0) query.append(" OR ");
      query.append('"').append(word).append('"');
    }
    if (query.length() == 0) return results;
    try (SQLiteDatabase db = SQLiteDatabase.openDatabase(
        database(context).getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        Cursor cursor = db.rawQuery(
            "SELECT title, snippet(wiki_fts, '', '', ' ... ', 1, 48), url FROM wiki_fts "
                + "WHERE wiki_fts MATCH ? ORDER BY "
                + "(CASE WHEN lower(title) LIKE ? THEN 0 ELSE 1 END), length(body) "
                + "LIMIT ?",
            new String[]{query.toString(), "%" + titleKeyword + "%",
                Integer.toString(Math.min(4, maxResults))})) {
      while (cursor.moveToNext()) {
        String title = cursor.getString(0);
        String body = cursor.getString(1);
        String url = cursor.getString(2);
        if (title == null || body == null || url == null
            || !url.startsWith("https://nethackwiki.com/wiki/")) continue;
        results.add(new OfflineKnowledge.Note(title,
            body.length() > MAX_PASSAGE_CHARS
                ? body.substring(0, MAX_PASSAGE_CHARS) + " [excerpt ends]" : body,
            url, ""));
      }
    } catch (Exception error) {
      android.util.Log.w("OfflineWikiIndex", "Index missing or invalid; using starter notes", error);
      results.clear();
    }
    return results;
  }
}
