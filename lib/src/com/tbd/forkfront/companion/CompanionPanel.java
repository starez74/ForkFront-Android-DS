package com.tbd.forkfront.companion;

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
  private final LinearLayout root;
  private final TextView heading;
  private final TextView modeButton;
  private final EditText input;
  private final TextView status;
  private final LinearLayout results;
  private final Spinner rolePicker;
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

    status = label("Answers show wiki sources, not guesses.", 10, DIM);
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
  }

  public boolean isVisible() { return root.getVisibility() == View.VISIBLE; }

  public void show() {
    if (destroyed) return;
    root.setVisibility(View.VISIBLE);
    input.requestFocus();
    refreshHeading();
  }

  public void hide() {
    generation++;
    root.setVisibility(View.GONE);
    input.clearFocus();
  }

  public void close() {
    destroyed = true;
    generation++;
    wiki.close();
    root.setVisibility(View.GONE);
  }

  public void updateTitle(String statusTitle) {
    String next = CompanionRole.detect(statusTitle);
    if (!next.equals(detectedRole)) {
      detectedRole = next;
      refreshHeading();
    }
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
        status.setText("Wiki results • tap a heading to read the source");
        if (!requestedFactual)
          addText(CompanionRole.inWorldIntro(requestedRole), ACCENT, 11, true);
        for (CompanionWikiLookup.Result article : articles) {
          TextView title = addText(article.title + "  ↗", ACCENT, 12, true);
          title.setOnClickListener(v -> open(article.url));
          if (!article.snippet.isEmpty())
            addText(article.snippet, TEXT, 11, false);
          View line = new View(activity);
          line.setBackgroundColor(0xFF41434D);
          LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
          lp.setMargins(0, dp(5), 0, dp(5));
          results.addView(line, lp);
        }
      }
      TextView fallback = button("Open full Wiki search ↗");
      fallback.setOnClickListener(v -> open(CompanionWikiLookup.searchUrl(question)));
      results.addView(fallback, new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, dp(33)));
    });
  }

  private TextView addText(String text, int color, int size, boolean bold) {
    TextView v = label(text, size, color);
    if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    v.setPadding(dp(3), dp(3), dp(3), dp(3));
    results.addView(v);
    return v;
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
