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
  // NetHackWiki's /w/api.php redirects to /api.php. Redirect following is
  // intentionally disabled so a player's question is never forwarded elsewhere.
  public static final String API = "https://nethackwiki.com/api.php";
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
      } catch (WikiHttpException e) {
        error = "NetHackWiki returned HTTP " + e.status +
            ". Use Open full Wiki search, or retry later.";
      } catch (java.net.SocketTimeoutException e) {
        error = "NetHackWiki timed out. Check Internet and retry.";
      } catch (java.net.UnknownHostException e) {
        error = "Cannot reach NetHackWiki. Check Internet connection.";
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

  private static final class WikiHttpException extends IOException {
    final int status;
    WikiHttpException(int status) { super("HTTP " + status); this.status = status; }
  }

  /** Search first, then request rendered plain-text article intros by page ID. */
  private static List<Result> fetch(String terms) throws Exception {
    if (terms.isEmpty()) return Collections.emptyList();
    JSONObject root = readJson(API + "?action=query&list=search&format=json&srnamespace=0"
        + "&srprop=snippet&srlimit=3&srsearch=" + URLEncoder.encode(terms, "UTF-8"));
    JSONArray hits = root.getJSONObject("query").getJSONArray("search");
    List<Result> results = new ArrayList<>();
    List<Integer> pageIds = new ArrayList<>();
    StringBuilder ids = new StringBuilder();
    for (int i = 0; i < hits.length() && results.size() < 3; i++) {
      JSONObject hit = hits.getJSONObject(i);
      String title = hit.optString("title", "").trim();
      if (title.isEmpty()) continue;
      int id = hit.optInt("pageid", -1);
      pageIds.add(id);
      if (id > 0) {
        if (ids.length() > 0) ids.append('|');
        ids.append(id);
      }
      results.add(new Result(title, cleanSnippet(hit.optString("snippet", ""))));
    }
    if (ids.length() == 0) return results;

    try {
      JSONObject extractRoot = readJson(API
          + "?action=query&prop=extracts&exintro=1&explaintext=1"
          + "&exchars=720&exlimit=3&format=json&formatversion=2&pageids="
          + URLEncoder.encode(ids.toString(), "UTF-8"));
      JSONArray pages = extractRoot.getJSONObject("query").getJSONArray("pages");
      java.util.Map<Integer, String> summaries = new java.util.HashMap<>();
      for (int i = 0; i < pages.length(); i++) {
        JSONObject page = pages.getJSONObject(i);
        String summary = cleanExtract(page.optString("extract", ""));
        if (page.optInt("pageid", -1) > 0 && !summary.isEmpty())
          summaries.put(page.optInt("pageid"), summary);
      }
      for (int i = 0; i < results.size(); i++) {
        String summary = summaries.get(pageIds.get(i));
        if (summary != null) results.set(i, new Result(results.get(i).title, summary));
      }
    } catch (Exception e) {
      // Some MediaWiki installations lack TextExtracts: retain the links.
      android.util.Log.w("CompanionWikiLookup", "Wiki extract unavailable", e);
    }
    return results;
  }

  static String cleanSnippet(String html) {
    String plain = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
        .replaceAll("\\s+", " ").trim();
    // Search snippets sometimes contain unexpanded wiki templates.
    if (containsWikiMarkup(plain)) return "";
    return plain.length() > 240 ? plain.substring(0, 240) + "…" : plain;
  }

  static String cleanExtract(String raw) {
    String plain = raw == null ? "" : raw.replaceAll("\\s+", " ").trim();
    if (containsWikiMarkup(plain)) return "";
    return plain.length() > 700 ? plain.substring(0, 700) + "…" : plain;
  }

  private static boolean containsWikiMarkup(String s) {
    return s.contains("{{") || s.contains("}}") || s.contains("[[") || s.contains("{|");
  }

  private static JSONObject readJson(String url) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setRequestMethod("GET");
    connection.setRequestProperty("User-Agent",
        "NetHackAIEditionTablet/0.1 (wiki search; no user data)");
    connection.setRequestProperty("Accept", "application/json");
    connection.setConnectTimeout(6000);
    connection.setReadTimeout(7000);
    connection.setInstanceFollowRedirects(false);
    try {
      int status = connection.getResponseCode();
      if (status != HttpURLConnection.HTTP_OK) throw new WikiHttpException(status);
      StringBuilder body = new StringBuilder();
      try (InputStream stream = connection.getInputStream();
           BufferedReader reader = new BufferedReader(
               new InputStreamReader(stream, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (body.length() + line.length() > 200_000)
            throw new IOException("Wiki response too large");
          body.append(line);
        }
      }
      JSONObject json = new JSONObject(body.toString());
      if (json.has("error")) throw new IOException("Wiki API error");
      return json;
    } finally {
      connection.disconnect();
    }
  }
}
