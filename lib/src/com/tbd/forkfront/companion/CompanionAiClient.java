package com.tbd.forkfront.companion;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

/** Opt-in HTTPS client; AndroidKeyStore token never enters NetHack commands or logs. */
public final class CompanionAiClient {
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Handler ui = new Handler(Looper.getMainLooper());

  public static final class Source {
    public final String title;
    public final String url;
    Source(String title, String url) { this.title = title; this.url = url; }
  }
  public static final class Reply {
    public final String answer;
    public final List<Source> sources;
    public final boolean grounded;
    Reply(String answer, List<Source> sources, boolean grounded) {
      this.answer = answer; this.sources = sources; this.grounded = grounded;
    }
  }
  public interface Callback {
    void complete(Reply result, String error);
  }

  public void ask(Context context, String question, String role, boolean factual,
      JSONObject authorizedState, JSONArray history, Callback callback) {
    final Context app = context.getApplicationContext();
    worker.execute(() -> {
      Reply reply = null;
      String error = null;
      try {
        reply = fetch(app, question, role, factual, authorizedState, history);
      } catch (Exception exception) {
        if (exception instanceof java.net.SocketTimeoutException) {
          error = "AI server timed out. Wiki-only search is still available.";
        } else if (exception instanceof java.net.UnknownHostException) {
          error = "Cannot find the AI server. Check the HTTPS endpoint.";
        } else if (exception instanceof java.net.ConnectException) {
          error = "Cannot connect to the AI server. Check deployment and Wi-Fi.";
        } else if (exception instanceof java.io.IOException) {
          error = exception.getMessage();
        } else {
          error = "AI connection unavailable. Check setup or the pairing token.";
        }
      }
      final Reply completed = reply;
      final String failure = error;
      ui.post(() -> callback.complete(completed, failure));
    });
  }

  public void close() { worker.shutdownNow(); }

  private static Reply fetch(Context context, String question, String role, boolean factual,
      JSONObject state, JSONArray history) throws Exception {
    String endpoint = CompanionCredentials.getEndpoint(context);
    if (!CompanionCredentials.isValidEndpoint(endpoint))
      throw new java.io.IOException("Configure an HTTPS AI backend first.");
    String token = CompanionCredentials.readToken(context);
    URL url = new URL(endpoint);
    if (!"https".equalsIgnoreCase(url.getProtocol()))
      throw new java.io.IOException("Only encrypted HTTPS connections are permitted.");
    HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
    conn.setInstanceFollowRedirects(false);
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setConnectTimeout(10000);
    conn.setReadTimeout(40000);
    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
    conn.setRequestProperty("Accept", "application/json");
    conn.setRequestProperty("Authorization", "Bearer " + token);
    conn.setUseCaches(false);

    JSONObject payload = new JSONObject();
    payload.put("question", question);
    payload.put("role", role);
    payload.put("mode", factual ? "factual" : "in_world");
    if (state != null) payload.put("state", state);
    if (history != null) payload.put("history", history);
    byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
    if (bytes.length > 12000) throw new java.io.IOException("Question too large");

    try {
      try (OutputStream output = conn.getOutputStream()) {
        output.write(bytes);
      }
      int status = conn.getResponseCode();
      if (status != 200) {
        if (status == 401) throw new java.io.IOException("Pairing token rejected by the server.");
        if (status == 429) throw new java.io.IOException("AI request limit reached. Retry shortly.");
        if (status == 503) throw new java.io.IOException("AI backend not configured yet.");
        if (status == 502) throw new java.io.IOException("Wiki or AI provider is currently unavailable.");
        throw new java.io.IOException("AI server returned HTTP " + status);
      }
      StringBuilder body = new StringBuilder();
      try (InputStream data = conn.getInputStream();
           BufferedReader lines = new BufferedReader(
               new InputStreamReader(data, StandardCharsets.UTF_8))) {
        String line;
        while ((line = lines.readLine()) != null) {
          if (body.length() + line.length() > 12000)
            throw new java.io.IOException("AI response too long.");
          body.append(line);
        }
      }
      JSONObject json = new JSONObject(body.toString());
      String answer = json.optString("answer", "").trim();
      if (answer.isEmpty()) throw new java.io.IOException("AI did not return an answer.");
      List<Source> sources = new ArrayList<>();
      JSONArray array = json.optJSONArray("sources");
      if (array != null) for (int i = 0; i < array.length() && i < 5; i++) {
        JSONObject article = array.optJSONObject(i);
        if (article == null) continue;
        String sourceUrl = article.optString("url", "");
        if (sourceUrl.startsWith("https://nethackwiki.com/wiki/")) {
          sources.add(new Source(article.optString("title", "Source"), sourceUrl));
        }
      }
      return new Reply(answer, sources, json.optBoolean("grounded", false));
    } finally {
      conn.disconnect();
    }
  }
}
