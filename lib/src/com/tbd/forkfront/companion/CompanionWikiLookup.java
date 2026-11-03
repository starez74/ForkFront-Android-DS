package com.tbd.forkfront.companion;

import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Read-only NetHackWiki MediaWiki search. Sends ONLY the user's typed question,
 * never character names, saves, status, inventory, or any credential.
 */
public final class CompanionWikiLookup {
  public static final String API = "https://nethackwiki.com/w/api.php";
  public static final String SEARCH = "https://nethackwiki.com/wiki/Special:Search?search=";
  public static final String LICENSE = "https://nethackwiki.com/wiki/NetHackWiki:Copyrights";
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());

  public static final class Result {
    public final String title;
    public final String snippet;
    public final String url;
    public Result(String title, String snippet) {
      this.title = title;
      this.snippet = snippet;
      this.url = "https://nethackwiki.com/wiki/" +
          android.net.Uri.encode(title.replace(' ', '_'));
    }
  }

  public interface Callback {
    void onComplete(List<Result> articles, String error);
  }

  public void search(String question, Callback callback) {
    final String normalized = extractKeywords(question);
    executor.execute(() -> {
      List<Result> articles = Collections.emptyList();
      String error = null;
      try {
        articles = fetch(normalized);
      } catch (Exception e) {
        error = "NetHackWiki lookup unavailable. Open the wiki in a browser instead.";
      }
      final List<Result> response = articles;
      final String failure = error;
      main.post(() -> callback.onComplete(response, failure));
    });
  }

  public void close() { executor.shutdownNow(); }

  public static String searchUrl(String question) {
    return SEARCH + android.net.Uri.encode(question == null ? "" : question.trim());
  }

  static String extractKeywords(String question) {
    if (question == null) return "";
    String cleaned = question.replaceAll("[^\\p{L}\\p{N}\\s'\\-]", " ").trim();
    String[] words = cleaned.split("\\s+");
    StringBuilder result = new StringBuilder();
    for (String word : words) {
      String w = word.toLowerCase(java.util.Locale.ROOT);
      if (w.matches("what|why|when|where|how|should|could|would|please|about|with|does|do|i|my|the|a|an|is|are|can|to|of|for|me|in")) continue;
      if (result.length() > 0) result.append(' ');
      result.append(word);
      if (result.length() >= 90) break;
    }
    return result.length() > 0 ? result.toString() : cleaned;
  }

  private static List<Result> fetch(String terms) throws Exception {
    if (terms.isEmpty()) return Collections.emptyList();
    String url = API + "?action=query&list=search&format=json&srnamespace=0" +
        "&srprop=snippet&srlimit=3&srsearch=" +
        URLEncoder.encode(terms, "UTF-8");
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setRequestMethod("GET");
    connection.setRequestProperty("User-Agent", "NetHackAIEditionTablet/0.1 (wiki search; no user data)");
    connection.setRequestProperty("Accept", "application/json");
    connection.setConnectTimeout(6000);
    connection.setReadTimeout(7000);
    connection.setInstanceFollowRedirects(false);
    try {
      if (connection.getResponseCode() != 200)
        throw new IOException("Wiki responded with " + connection.getResponseCode());
      StringBuilder body = new StringBuilder();
      try (InputStream stream = connection.getInputStream();
           BufferedReader reader = new BufferedReader(
               new InputStreamReader(stream, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null && body.length() < 100_000)
          body.append(line);
      }
      JSONObject root = new JSONObject(body.toString());
      if (root.has("error")) throw new IOException("Wiki API error");
      JSONArray hits = root.getJSONObject("query").getJSONArray("search");
      List<Result> result = new ArrayList<>();
      for (int i = 0; i < hits.length() && i < 3; i++) {
        JSONObject hit = hits.getJSONObject(i);
        String title = hit.optString("title", "").trim();
        if (title.isEmpty()) continue;
        String raw = hit.optString("snippet", "");
        String plain = Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY)
            .toString().replaceAll("\\s+", " ").trim();
        if (plain.length() > 260) plain = plain.substring(0, 260) + "...";
        result.add(new Result(title, plain));
      }
      return result;
    } finally {
      connection.disconnect();
    }
  }
}
