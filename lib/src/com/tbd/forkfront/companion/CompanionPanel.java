package com.tbd.forkfront.companion;

import android.app.Dialog;
import androidx.appcompat.app.AlertDialog;
import android.text.InputType;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.tbd.forkfront.ForkFront;
import java.util.List;

/**
 * Docked, read-only wiki companion. In-world mode changes the framing,
 * factual mode presents sources plainly. Does not claim to generate AI answers.
 */
public final class CompanionPanel {
  private static final int TEXT = 0xFFE4E5E8;
  private static final int DIM = 0xFFB1B5C0;
  private static final int ACCENT = 0xFF89D65D;
  private static final int BACKGROUND = 0xFF101015;
  private final ForkFront activity;
  private final CompanionWikiLookup wiki = new CompanionWikiLookup();
  private final CompanionAiClient aiClient = new CompanionAiClient();
  private final ArrayList<JSONObject> conversation = new ArrayList<>();
  private JSONObject latestStatus;
  private boolean shareStatus;
  private TextView sharingButton;
  private TextView configButton;
  private String lastAiQuestion = "";
  private String lastAiRole = "Adventurer";
  private CompanionAiClient.Reply lastAiReply;
  private final LinearLayout root;
  private final TextView heading;
  private final TextView modeButton;
  private final EditText input;
  private final TextView status;
  private final LinearLayout results;
  private final Spinner rolePicker;
  private Dialog resultsDialog;
  private boolean factual;
  private String detectedRole = "Adventurer";
  private int generation;
  private boolean destroyed;

  public CompanionPanel(ForkFront activity, FrameLayout parent) {
    this.activity = activity;
    factual = activity.getPreferences(Context.MODE_PRIVATE)
        .getBoolean("companion_factual_mode", false);

    root = new LinearLayout(activity);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(7), dp(6), dp(7), dp(6));
    root.setBackgroundColor(BACKGROUND);
    root.setVisibility(View.GONE);
    parent.addView(root, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    LinearLayout header = row();
    heading = label("Companion", 13, ACCENT);
    heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    header.addView(heading, new LinearLayout.LayoutParams(0, dp(32), 1));
    TextView close = button("×");
    close.setContentDescription("Close companion");
    close.setOnClickListener(v -> hide());
    header.addView(close, new LinearLayout.LayoutParams(dp(32), dp(32)));
    root.addView(header);

    rolePicker = new Spinner(activity);
    ArrayAdapter<String> roles = new ArrayAdapter<>(
        activity, android.R.layout.simple_spinner_dropdown_item, CompanionRole.OPTIONS);
    rolePicker.setAdapter(roles);
    root.addView(rolePicker, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
    rolePicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
      @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
          int position, long id) { refreshHeading(); }
      @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
    });

    modeButton = button("");
    modeButton.setOnClickListener(v -> {
      factual = !factual;
      activity.getPreferences(Context.MODE_PRIVATE).edit()
          .putBoolean("companion_factual_mode", factual).apply();
      modeButton.setText(factual ? "Mode: Factual advice" : "Mode: In-world");
    });
    modeButton.setText(factual ? "Mode: Factual advice" : "Mode: In-world");
    root.addView(modeButton, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));

    input = new EditText(activity);
    input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
    input.setTextColor(TEXT);
    input.setHintTextColor(DIM);
    input.setHint("Ask about an item, monster or tactic");
    input.setHintTextColor(0xFF8F939F);
    input.setBackground(outline());
    input.setSingleLine(false);
    input.setMaxLines(2);
    input.setMinLines(1);
    input.setPadding(dp(6), dp(4), dp(6), dp(4));
    input.setShowSoftInputOnFocus(false);
    input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
    input.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
        ask();
        return true;
      }
      return false;
    });
    LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(53));
    editParams.topMargin = dp(4);
    root.addView(input, editParams);

    TextView ask = button("Ask NetHackWiki");
    ask.setOnClickListener(v -> ask());
    root.addView(ask, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));

    configButton = button("Configure secure AI");
    configButton.setOnClickListener(v -> showAiSetup());
    root.addView(configButton, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));

    sharingButton = button("Share HP/level: OFF");
    sharingButton.setOnClickListener(v -> {
      shareStatus = !shareStatus;
      updateSharingButton();
    });
    root.addView(sharingButton, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));

    TextView askAi = button("Ask AI (wiki-grounded)");
    askAi.setOnClickListener(v -> askAi());
    root.addView(askAi, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));

    status = label("Wiki only until secure AI is configured.", 10, DIM);
    status.setMaxLines(2);
    root.addView(status, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    ScrollView scroller = new ScrollView(activity);
    scroller.setFillViewport(false);
    results = new LinearLayout(activity);
    results.setOrientation(LinearLayout.VERTICAL);
    scroller.addView(results);
    LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
    root.addView(scroller, scrollParams);

    TextView source = label("Source: NetHackWiki • CC BY-SA 3.0", 10, DIM);
    source.setOnClickListener(v -> open(CompanionWikiLookup.LICENSE));
    source.setGravity(Gravity.CENTER);
    root.addView(source, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(26)));
    refreshHeading();
    refreshConnectionButton();
  }

  public boolean isVisible() { return root.getVisibility() == View.VISIBLE; }

  public void show() {
    if (destroyed) return;
    root.setVisibility(View.VISIBLE);
    input.requestFocus();
    refreshHeading();
    refreshConnectionButton();
  }

  public void hide() {
    dismissResults();
    generation++;
    root.setVisibility(View.GONE);
    input.clearFocus();
  }

  public void close() {
    dismissResults();
    destroyed = true;
    generation++;
    wiki.close();
    aiClient.close();
    conversation.clear();
    latestStatus = null;
    root.setVisibility(View.GONE);
  }

  public void updateTitle(String statusTitle) {
    String next = CompanionRole.detect(statusTitle);
    if (!next.equals(detectedRole)) {
      detectedRole = next;
      refreshHeading();
    }
  }

  /**
   * Locally cached, deliberately restricted status; only sent when the
   * player explicitly enables the share switch for this session.
   */
  public void updateLimitedStatus(JSONObject state) {
    latestStatus = state;
  }

  private void updateSharingButton() {
    if (sharingButton != null)
      sharingButton.setText(shareStatus ? "Share HP/level: ON" : "Share HP/level: OFF");
  }

  private void refreshConnectionButton() {
    if (configButton != null)
      configButton.setText(CompanionCredentials.isConfigured(activity)
          ? "AI: Configured (edit)" : "Configure secure AI");
  }

  private String currentRole() {
    int index = rolePicker.getSelectedItemPosition();
    if (index > 0 && index < CompanionRole.OPTIONS.length)
      return CompanionRole.OPTIONS[index];
    return detectedRole;
  }

  private void refreshHeading() {
    if (heading != null && rolePicker != null)
      heading.setText("Companion • " + currentRole());
  }

  /** Intercepts only while visible; never lets companion keystrokes reach the game. */
  public boolean handleVirtualKey(char ch, int keyCode) {
    if (!isVisible()) return false;
    if (keyCode == KeyEvent.KEYCODE_ESCAPE || ch == '\033') {
      hide();
      return true;
    }
    if (keyCode == KeyEvent.KEYCODE_ENTER || ch == '\n' || ch == '\r') {
      ask();
      return true;
    }

    Editable e = input.getText();
    int a = Math.max(0, input.getSelectionStart());
    int b = Math.max(0, input.getSelectionEnd());
    int start = Math.min(a, b), end = Math.max(a, b);
    if (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL) {
      if (start < end) e.delete(start, end);
      else if (keyCode == KeyEvent.KEYCODE_DEL && start > 0) e.delete(start - 1, start);
      else if (keyCode == KeyEvent.KEYCODE_FORWARD_DEL && start < e.length())
        e.delete(start, start + 1);
    } else if (ch >= ' ' && ch != 0x7f) {
      e.replace(start, end, String.valueOf(ch));
    }
    return true;
  }

  private void showAiSetup() {
    // Native Samsung input is permitted here solely for entering a server URL
    // and a *pairing token*, never an OpenAI provider key.
    LinearLayout fields = new LinearLayout(activity);
    fields.setOrientation(LinearLayout.VERTICAL);
    fields.setPadding(dp(20), dp(8), dp(20), dp(4));

    TextView explanation = label(
        "Requires your separately deployed HTTPS backend. Do NOT enter an OpenAI API key. "
        + "Only the pairing token is saved, encrypted in Android Keystore.",
        13, TEXT);
    fields.addView(explanation);

    EditText endpoint = new EditText(activity);
    endpoint.setSingleLine(true);
    endpoint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
    endpoint.setHint("https://your-backend.vercel.app/api/ask");
    endpoint.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
    endpoint.setText(CompanionCredentials.getEndpoint(activity));
    fields.addView(endpoint);

    EditText token = new EditText(activity);
    token.setSingleLine(true);
    token.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
    token.setHint("Pairing token (not API key)");
    token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    fields.addView(token);

    AlertDialog dialog = new AlertDialog.Builder(activity)
        .setTitle("Secure AI connection")
        .setView(fields)
        .setPositiveButton("Save", null)
        .setNegativeButton("Cancel", null)
        .setNeutralButton("Disconnect", null)
        .create();
    dialog.setOnShowListener(d -> {
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
        try {
          CompanionCredentials.save(activity, endpoint.getText().toString(),
              token.getText().toString());
          refreshConnectionButton();
          status.setText("AI configured. Ask a question to test connection.");
          dialog.dismiss();
        } catch (Exception error) {
          Toast.makeText(activity, "Invalid HTTPS address or token; check setup.",
              Toast.LENGTH_LONG).show();
        }
      });
      dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
        CompanionCredentials.clear(activity);
        conversation.clear();
        lastAiReply = null;
        refreshConnectionButton();
        status.setText("AI disconnected. Wiki search remains available.");
        dialog.dismiss();
      });
    });
    dialog.show();
  }

  private void askAi() {
    if (!CompanionCredentials.isConfigured(activity)) {
      status.setText("Configure an HTTPS backend and pairing token first.");
      showAiSetup();
      return;
    }
    final String question = input.getText().toString().trim();
    if (question.length() < 2 || question.length() > 160) {
      status.setText("Enter a question between 2 and 160 characters.");
      return;
    }
    dismissResults();
    final int request = ++generation;
    final boolean requestedFactual = factual;
    final String role = currentRole();
    final JSONObject allowedState = shareStatus ? latestStatus : null;
    JSONArray turns = new JSONArray();
    for (JSONObject turn : conversation) turns.put(turn);
    results.removeAllViews();
    status.setText(shareStatus
        ? "Asking AI… sharing limited HP/level data."
        : "Asking AI… sharing role and chat, but not HP/level.");

    aiClient.ask(activity, question, role, requestedFactual, allowedState, turns,
        (reply, error) -> {
          if (destroyed || !isVisible() || request != generation) return;
          results.removeAllViews();
          if (error != null || reply == null) {
            status.setText(error == null ? "AI unavailable." : error);
            return;
          }
          try {
            conversation.add(new JSONObject().put("role", "user")
                .put("content", question));
            conversation.add(new JSONObject().put("role", "assistant")
                .put("content", reply.answer.substring(0, Math.min(reply.answer.length(), 450))));
            while (conversation.size() > 6) conversation.remove(0);
          } catch (Exception ignored) {
            conversation.clear();
          }
          lastAiReply = reply;
          lastAiQuestion = question;
          lastAiRole = role;
          status.setText("AI answer ready • " + reply.sources.size() + " source links");
          TextView reopen = button("Read AI answer ↗");
          reopen.setOnClickListener(v -> showAiResponse(lastAiQuestion, lastAiRole, lastAiReply));
          results.addView(reopen, new LinearLayout.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT, dp(39)));
          showAiResponse(question, role, reply);
        });
  }

  /** Same wide result layout as the proven wiki dialog, but with narrative advice. */
  private void showAiResponse(String question, String role, CompanionAiClient.Reply reply) {
    if (reply == null || destroyed || !isVisible()) return;
    dismissResults();
    Dialog dialog = new Dialog(activity);
    LinearLayout content = new LinearLayout(activity);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(dp(18), dp(12), dp(18), dp(12));
    GradientDrawable background = new GradientDrawable();
    background.setColor(BACKGROUND);
    background.setCornerRadius(dp(9));
    background.setStroke(dp(1), 0xFF747780);
    content.setBackground(background);

    LinearLayout header = row();
    TextView title = label("Dungeon Companion • " + role, 18, ACCENT);
    title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    header.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
    TextView close = button("✕  Close");
    close.setOnClickListener(v -> dialog.dismiss());
    header.addView(close, new LinearLayout.LayoutParams(dp(100), dp(38)));
    content.addView(header);

    TextView prompt = label("You asked: " + question, 13, DIM);
    content.addView(prompt);

    ScrollView scroll = new ScrollView(activity);
    LinearLayout contentList = new LinearLayout(activity);
    contentList.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(contentList);
    LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
    scrollParams.topMargin = dp(12);
    content.addView(scroll, scrollParams);

    TextView answer = label(reply.answer, 16, TEXT);
    answer.setTextIsSelectable(true);
    answer.setPadding(0, 0, 0, dp(16));
    contentList.addView(answer);

    TextView references = label("NetHackWiki sources", 14, ACCENT);
    references.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    contentList.addView(references);
    if (reply.sources.isEmpty()) {
      contentList.addView(label("No verified source excerpts available.", 12, DIM));
    } else {
      for (int i = 0; i < reply.sources.size(); i++) {
        CompanionAiClient.Source source = reply.sources.get(i);
        TextView link = label("[" + (i + 1) + "] " + source.title + " ↗", 13, ACCENT);
        link.setPadding(0, dp(7), 0, dp(7));
        link.setOnClickListener(v -> open(source.url));
        contentList.addView(link);
      }
    }
    TextView note = label(
        "AI advice may be mistaken. Source excerpts: NetHackWiki • CC BY-SA 3.0. "
            + "The game is never controlled automatically.", 11, DIM);
    note.setGravity(Gravity.CENTER);
    content.addView(note, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(37)));
    dialog.setContentView(content);
    dialog.setOnDismissListener(d -> {
      if (resultsDialog == dialog) resultsDialog = null;
    });
    resultsDialog = dialog;
    dialog.show();
    Window w = dialog.getWindow();
    if (w != null) {
      w.setBackgroundDrawableResource(android.R.color.transparent);
      WindowManager.LayoutParams a = w.getAttributes();
      a.dimAmount = 0.58f;
      w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
      w.setAttributes(a);
      int width = activity.getResources().getDisplayMetrics().widthPixels;
      int height = activity.getResources().getDisplayMetrics().heightPixels;
      w.setLayout((int)(width * 0.80f), (int)(height * 0.76f));
      w.setGravity(Gravity.CENTER);
    }
  }

  private void ask() {
    String question = input.getText().toString().trim();
    if (question.isEmpty()) {
      status.setText("Enter a question with the fixed keyboard.");
      return;
    }
    if (question.length() > 160) {
      status.setText("Please keep questions under 160 characters.");
      return;
    }
    dismissResults();
    final int request = ++generation;
    final boolean requestedFactual = factual;
    final String requestedRole = currentRole();
    results.removeAllViews();
    status.setText("Searching NetHackWiki… Only your question is sent.");

    wiki.search(question, (articles, error) -> {
      if (destroyed || !isVisible() || request != generation) return;
      results.removeAllViews();
      if (error != null) {
        status.setText(error);
      } else if (articles.isEmpty()) {
        status.setText("No matching wiki pages found.");
      } else {
        status.setText(articles.size() + " wiki articles found. Read in larger window.");
        TextView view = button("View wiki results ↗");
        view.setOnClickListener(v ->
            showResultsDialog(question, requestedRole, requestedFactual, articles));
        results.addView(view, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
        showResultsDialog(question, requestedRole, requestedFactual, articles);
      }
      TextView fallback = button("Open full Wiki search ↗");
      fallback.setOnClickListener(v -> open(CompanionWikiLookup.searchUrl(question)));
      results.addView(fallback, new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
    });
  }

  /**
   * Results are read in a wider modal window, not squeezed into the
   * 213dp inventory dock. The virtual keyboard and the dungeon remain
   * unchanged beneath it. Closing returns to the companion question.
   */
  private void showResultsDialog(String question, String role, boolean factMode,
      List<CompanionWikiLookup.Result> articles) {
    if (destroyed || !isVisible()) return;
    dismissResults();

    final Dialog dialog = new Dialog(activity);
    LinearLayout content = new LinearLayout(activity);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(dp(18), dp(12), dp(18), dp(12));
    GradientDrawable background = new GradientDrawable();
    background.setColor(BACKGROUND);
    background.setCornerRadius(dp(9));
    background.setStroke(dp(1), 0xFF747780);
    content.setBackground(background);

    LinearLayout header = row();
    TextView title = label("Dungeon Companion • " + role, 18, ACCENT);
    title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    header.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));
    TextView close = button("✕  Close");
    close.setContentDescription("Close wiki results and return to game");
    close.setOnClickListener(v -> dialog.dismiss());
    header.addView(close, new LinearLayout.LayoutParams(dp(100), dp(38)));
    content.addView(header);

    TextView subtitle = label("NetHackWiki results for: " + question, 13, DIM);
    subtitle.setMaxLines(2);
    content.addView(subtitle);

    if (!factMode) {
      TextView intro = label(CompanionRole.inWorldIntro(role), 13, ACCENT);
      intro.setPadding(0, dp(12), 0, dp(7));
      content.addView(intro);
    }

    ScrollView scroll = new ScrollView(activity);
    scroll.setFillViewport(false);
    LinearLayout articleList = new LinearLayout(activity);
    articleList.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(articleList);
    LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
    scrollParams.topMargin = dp(9);
    content.addView(scroll, scrollParams);

    for (CompanionWikiLookup.Result article : articles) {
      TextView heading = label(article.title + "  ↗", 17, ACCENT);
      heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
      heading.setPadding(0, dp(10), 0, dp(8));
      heading.setOnClickListener(v -> open(article.url));
      articleList.addView(heading);

      TextView summary = label(
          article.snippet.isEmpty()
              ? "No readable summary available. Open this article to read the source."
              : article.snippet, 14,
          article.snippet.isEmpty() ? DIM : TEXT);
      summary.setPadding(0, 0, 0, dp(12));
      articleList.addView(summary);

      View divider = new View(activity);
      divider.setBackgroundColor(0xFF494B55);
      articleList.addView(divider, new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
    }

    TextView search = button("Open complete NetHackWiki search ↗");
    search.setOnClickListener(v -> open(CompanionWikiLookup.searchUrl(question)));
    LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(39));
    searchParams.topMargin = dp(9);
    content.addView(search, searchParams);

    TextView source = label("Article excerpts: NetHackWiki • CC BY-SA 3.0", 11, DIM);
    source.setGravity(Gravity.CENTER);
    source.setOnClickListener(v -> open(CompanionWikiLookup.LICENSE));
    content.addView(source, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));

    dialog.setContentView(content);
    dialog.setCancelable(true);
    dialog.setOnDismissListener(d -> {
      if (resultsDialog == dialog) resultsDialog = null;
    });
    resultsDialog = dialog;
    dialog.show();

    Window window = dialog.getWindow();
    if (window != null) {
      window.setBackgroundDrawableResource(android.R.color.transparent);
      window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
      WindowManager.LayoutParams attributes = window.getAttributes();
      attributes.dimAmount = 0.58f;
      window.setAttributes(attributes);
      int width = activity.getResources().getDisplayMetrics().widthPixels;
      int height = activity.getResources().getDisplayMetrics().heightPixels;
      window.setLayout((int) (width * 0.80f), (int) (height * 0.76f));
      window.setGravity(Gravity.CENTER);
    }
  }

  private void dismissResults() {
    if (resultsDialog != null) {
      Dialog dialog = resultsDialog;
      resultsDialog = null;
      dialog.dismiss();
    }
  }

  private void open(String url) {
    if (url == null || !url.startsWith("https://nethackwiki.com/")) return;
    try {
      activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    } catch (Exception e) {
      Toast.makeText(activity, "No browser available for Wiki link", Toast.LENGTH_SHORT).show();
    }
  }

  private LinearLayout row() {
    LinearLayout row = new LinearLayout(activity);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    return row;
  }

  private TextView label(String text, int size, int color) {
    TextView view = new TextView(activity);
    view.setText(text);
    view.setTextColor(color);
    view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
    view.setGravity(Gravity.CENTER_VERTICAL);
    view.setIncludeFontPadding(false);
    return view;
  }

  private TextView button(String text) {
    TextView b = label(text, 12, TEXT);
    b.setGravity(Gravity.CENTER);
    b.setBackground(outline());
    b.setClickable(true);
    b.setFocusable(true);
    return b;
  }

  private GradientDrawable outline() {
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(0xFF23242B);
    bg.setCornerRadius(dp(5));
    bg.setStroke(dp(1), 0xFF575B66);
    return bg;
  }

  private int dp(float n) {
    return (int) (n * activity.getResources().getDisplayMetrics().density + 0.5f);
  }
}
