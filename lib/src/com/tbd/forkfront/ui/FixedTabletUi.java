package com.tbd.forkfront.ui;

import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.tbd.forkfront.ForkFront;
import com.tbd.forkfront.NH_State;
import com.tbd.forkfront.R;
import com.tbd.forkfront.input.DirectionalPadView;
import com.tbd.forkfront.input.Input;
import com.tbd.forkfront.window.message.NHW_Message;
import com.tbd.forkfront.window.message.NHW_Status;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/** Fixed screen regions for landscape tablets. No draggable ControlWidget wrappers. */
public final class FixedTabletUi
    implements NHW_Status.StatusUpdateListener, NHW_Message.MessageUpdateListener {
  private static final int PANEL = 0xFF101015;
  private static final int BORDER = 0xFF747780;
  private static final int TEXT = 0xFFE4E5E8;
  private final ForkFront activity;
  private final TextView statusLine;
  private final TextView messageLine;
  private final FrameLayout menuHost;
  private final TextView menuPlaceholder;
  private NHW_Status statusWindow;
  private NHW_Message messageWindow;
  private long conditionMask;
  private String lastMessage = "";
  private int moreCount;
  private static final String[] CONDITION_NAMES = {
      "Stone", "Slime", "Strngl", "FoodPois", "TermIll", "Blind",
      "Deaf", "Stun", "Conf", "Hallu", "Lev", "Fly", "Ride"
  };
  private final Map<Integer, NHW_Status.StatusField> fields = new HashMap<>();
  private boolean shift, ctrl, symbols;
  private TextView shiftButton, secondShiftButton, ctrlButton, symbolButton;
  private final TextView[] numberButtons = new TextView[10];

  private FixedTabletUi(ForkFront activity, TextView statusLine, TextView messageLine,
      FrameLayout menuHost, TextView menuPlaceholder) {
    this.activity = activity;
    this.statusLine = statusLine;
    this.messageLine = messageLine;
    this.menuHost = menuHost;
    this.menuPlaceholder = menuPlaceholder;
  }

  public static boolean supports(ForkFront activity) {
    Configuration config = activity.getResources().getConfiguration();
    return config.smallestScreenWidthDp >= 600
        && config.orientation == Configuration.ORIENTATION_LANDSCAPE;
  }

  public static FixedTabletUi install(ForkFront activity) {
    if (!supports(activity)) return null;
    LinearLayout root = activity.findViewById(R.id.base_frame);
    FrameLayout game = activity.findViewById(R.id.dlg_frame);
    if (root == null || game == null) return null;
    root.removeView(game);
    root.setBackgroundColor(Color.BLACK);

    LinearLayout header = new LinearLayout(activity);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setPadding(dp(activity, 7), dp(activity, 5), dp(activity, 7), dp(activity, 2));

    TextView status = label(activity, 12, true);
    status.setSingleLine(true);
    status.setEllipsize(TextUtils.TruncateAt.END);
    status.setAutoSizeTextTypeUniformWithConfiguration(9, 12, 1, TypedValue.COMPLEX_UNIT_SP);
    status.setPadding(dp(activity, 9), 0, dp(activity, 6), 0);
    status.setGravity(Gravity.CENTER_VERTICAL);
    status.setBackground(outline(activity));
    status.setText("NetHack AI Edition");
    LinearLayout.LayoutParams statusParam = new LinearLayout.LayoutParams(0, dp(activity, 38), 1);
    statusParam.rightMargin = dp(activity, 7);
    header.addView(status, statusParam);

    TextView message = label(activity, 12, true);
    message.setSingleLine(true);
    message.setEllipsize(TextUtils.TruncateAt.END);
    message.setGravity(Gravity.CENTER_VERTICAL);
    message.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
    message.setBackground(outline(activity));
    message.setText("Game messages");
    header.addView(message,
        new LinearLayout.LayoutParams(dp(activity, 213), dp(activity, 38)));
    root.addView(header, 0,
        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 46)));

    LinearLayout stage = new LinearLayout(activity);
    stage.setOrientation(LinearLayout.HORIZONTAL);
    stage.setPadding(dp(activity, 7), 0, dp(activity, 7), dp(activity, 4));
    stage.addView(game, new LinearLayout.LayoutParams(
        0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

    FrameLayout sidebar = new FrameLayout(activity);
    sidebar.setBackground(outline(activity));
    LinearLayout.LayoutParams sidebarParams =
        new LinearLayout.LayoutParams(dp(activity, 213), ViewGroup.LayoutParams.MATCH_PARENT);
    sidebarParams.leftMargin = dp(activity, 7);
    stage.addView(sidebar, sidebarParams);

    TextView placeholder = label(activity, 13, true);
    placeholder.setText("Inventory / menu");
    placeholder.setPadding(dp(activity, 10), dp(activity, 10), dp(activity, 8), 0);
    sidebar.addView(placeholder, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    FrameLayout menu = new FrameLayout(activity);
    // Stable across Activity restoration: FragmentManager needs a deterministic container ID.
    menu.setId(R.id.fixed_tablet_menu_host);
    menu.setVisibility(View.GONE);
    sidebar.addView(menu, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    root.addView(stage, 1,
        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

    FixedTabletUi ui = new FixedTabletUi(activity, status, message, menu, placeholder);
    LinearLayout controls = new LinearLayout(activity);
    controls.setOrientation(LinearLayout.HORIZONTAL);
    controls.setPadding(dp(activity, 7), dp(activity, 5), 0, dp(activity, 5));
    controls.setBackground(outline(activity));

    // About twice the old floating keyboard's width; reserve a gap before D-pad.
    LinearLayout.LayoutParams keyboardParams =
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2);
    controls.addView(ui.createKeyboard(), keyboardParams);
    controls.addView(new View(activity), new LinearLayout.LayoutParams(
        0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

    DirectionalPadView dpad = new DirectionalPadView(activity);
    dpad.setOnDirectionListener(c -> {
      NH_State state = activity.getState();
      if (state != null) state.getCommands().sendDirKeyCmd(c);
    });
    controls.addView(dpad, new LinearLayout.LayoutParams(
        dp(activity, 213), ViewGroup.LayoutParams.MATCH_PARENT));

    LinearLayout.LayoutParams controlsParams =
        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 223));
    controlsParams.leftMargin = dp(activity, 7);
    controlsParams.rightMargin = dp(activity, 7);
    controlsParams.bottomMargin = dp(activity, 7);
    root.addView(controls, 2, controlsParams);
    return ui;
  }

  private LinearLayout createKeyboard() {
    LinearLayout keyboard = new LinearLayout(activity);
    keyboard.setOrientation(LinearLayout.VERTICAL);
    String[][] rows = {
      {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "Del"},
      {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p", "Bksp"},
      {"a", "s", "d", "f", "g", "h", "j", "k", "l", "Enter"},
      {"Shift", "z", "x", "c", "v", "b", "n", "m", ",", ".", "?", "Shift"},
      {"Ctrl", "!#1", "Esc", "Space", "Tab", "<", ">"}
    };
    for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
      LinearLayout row = new LinearLayout(activity);
      row.setOrientation(LinearLayout.HORIZONTAL);
      for (int keyIndex = 0; keyIndex < rows[rowIndex].length; keyIndex++) {
        String name = rows[rowIndex][keyIndex];
        TextView key = label(activity, 15, false);
        key.setGravity(Gravity.CENTER);
        key.setText(name);
        key.setContentDescription(name);
        key.setBackground(keyOutline());
        key.setOnClickListener(v -> press(name));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.MATCH_PARENT, name.equals("Space") ? 4.2f : 1);
        p.setMargins(dp(activity, 2), dp(activity, 2), dp(activity, 2), dp(activity, 2));
        row.addView(key, p);
        if (rowIndex == 0 && keyIndex < 10) numberButtons[keyIndex] = key;
        if (name.equals("Shift")) {
          if (shiftButton == null) shiftButton = key;
          else secondShiftButton = key;
        }
        if (name.equals("Ctrl")) ctrlButton = key;
        if (name.equals("!#1")) symbolButton = key;
      }
      keyboard.addView(row,
          new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }
    return keyboard;
  }

  private void press(String key) {
    if (key.equals("Shift")) { shift = !shift; updateModifiers(); return; }
    if (key.equals("Ctrl")) { ctrl = !ctrl; updateModifiers(); return; }
    if (key.equals("!#1")) { symbols = !symbols; updateModifiers(); return; }
    char value;
    int keyCode = KeyEvent.KEYCODE_UNKNOWN;
    switch (key) {
      case "Space": value = ' '; keyCode = KeyEvent.KEYCODE_SPACE; break;
      case "Enter": value = '\r'; keyCode = KeyEvent.KEYCODE_ENTER; break;
      case "Bksp":
      case "Del": value = '\b'; keyCode = KeyEvent.KEYCODE_DEL; break;
      case "Esc": value = '\033'; keyCode = KeyEvent.KEYCODE_ESCAPE; break;
      case "Tab": value = '\t'; keyCode = KeyEvent.KEYCODE_TAB; break;
      default:
        value = key.charAt(0);
        if (symbols && value >= '0' && value <= '9') {
          value = ")!@#$%^&*(".charAt(value - '0');
        }
        if (shift) value = Character.toUpperCase(value);
        if (ctrl && Character.isLetter(value)) {
          value = (char) (Character.toUpperCase(value) & 0x1F);
        }
        break;
    }
    if (activity.getState() != null) {
      // GameInputRouter handles menus, prompts and gameplay in the normal order.
      activity.handleKeyDown(keyCode, value, 0, EnumSet.noneOf(Input.Modifier.class));
    }
    shift = false;
    ctrl = false;
    updateModifiers();
  }

  private void updateModifiers() {
    if (shiftButton != null) shiftButton.setTextColor(shift ? 0xFF80D545 : TEXT);
    if (secondShiftButton != null) secondShiftButton.setTextColor(shift ? 0xFF80D545 : TEXT);
    if (ctrlButton != null) ctrlButton.setTextColor(ctrl ? 0xFF80D545 : TEXT);
    if (symbolButton != null) symbolButton.setTextColor(symbols ? 0xFF80D545 : TEXT);
    for (int i = 0; i < numberButtons.length; i++) {
      if (numberButtons[i] != null) {
        numberButtons[i].setText(symbols
            ? String.valueOf(")!@#$%^&*(".charAt((i + 1) % 10))
            : String.valueOf((i + 1) % 10));
      }
    }
  }

  public int getMenuHostId() { return menuHost.getId(); }

  public void showMenu(boolean visible) {
    menuHost.setVisibility(visible ? View.VISIBLE : View.GONE);
    menuPlaceholder.setVisibility(visible ? View.GONE : View.VISIBLE);
  }

  public void attach(NH_State state) {
    detach();
    if (state == null) return;
    statusWindow = state.getStatusWindow();
    messageWindow = state.getMessageWindow();
    if (statusWindow != null) statusWindow.addListener(this);
    if (messageWindow != null) messageWindow.addListener(this);
  }

  public void detach() {
    if (statusWindow != null) statusWindow.removeListener(this);
    if (messageWindow != null) messageWindow.removeListener(this);
    statusWindow = null;
    messageWindow = null;
    fields.clear();
    conditionMask = 0;
  }

  @Override
  public void onFieldUpdated(int index, NHW_Status.StatusField field) {
    fields.put(index, field);
  }

  @Override
  public void onConditionsUpdated(long mask, long[] colors) {
    conditionMask = mask;
  }

  @Override
  public void onFlush() {
    SpannableStringBuilder line = new SpannableStringBuilder();
    append(line, value(NHW_Status.BL_TITLE), TEXT);
    append(line, value(NHW_Status.BL_ALIGN), TEXT);
    appendField(line, NHW_Status.BL_STR, "St:", TEXT);
    appendField(line, NHW_Status.BL_DX, "Dx:", TEXT);
    appendField(line, NHW_Status.BL_CO, "Co:", TEXT);
    appendField(line, NHW_Status.BL_IN, "In:", TEXT);
    appendField(line, NHW_Status.BL_WI, "Wi:", TEXT);
    appendField(line, NHW_Status.BL_CH, "Ch:", TEXT);
    appendField(line, NHW_Status.BL_LEVELDESC, "", TEXT);
    appendField(line, NHW_Status.BL_GOLD, "$:", 0xFFFFD13B);
    if (!value(NHW_Status.BL_HP).isEmpty()) {
      append(line, "HP:" + value(NHW_Status.BL_HP) + "(" + value(NHW_Status.BL_HPMAX) + ")",
          0xFF80D545);
    }
    if (!value(NHW_Status.BL_ENE).isEmpty()) {
      append(line, "Pw:" + value(NHW_Status.BL_ENE) + "(" + value(NHW_Status.BL_ENEMAX) + ")",
          0xFF80D545);
    }
    appendField(line, NHW_Status.BL_AC, "AC:", TEXT);
    appendField(line, NHW_Status.BL_XP, "Xp:", TEXT);
    appendField(line, NHW_Status.BL_TIME, "T:", TEXT);
    appendField(line, NHW_Status.BL_HUNGER, "", TEXT);
    appendField(line, NHW_Status.BL_CAP, "", TEXT);
    for (int i = 0; i < CONDITION_NAMES.length; i++) {
      if ((conditionMask & (1L << i)) != 0) {
        append(line, CONDITION_NAMES[i], 0xFFFFA35B);
      }
    }
    statusLine.setText(line);
  }

  @Override
  public void onReset() {
    fields.clear();
    conditionMask = 0;
    statusLine.setText("");
  }

  private String value(int index) {
    NHW_Status.StatusField f = fields.get(index);
    return f != null && f.enabled && f.value != null ? f.value : "";
  }

  private void appendField(SpannableStringBuilder line, int index, String prefix, int color) {
    String v = value(index);
    if (!v.isEmpty()) append(line, prefix + v, color);
  }

  private static void append(SpannableStringBuilder line, String part, int color) {
    if (part == null || part.isEmpty()) return;
    if (line.length() > 0) line.append(" ");
    int start = line.length();
    line.append(part);
    line.setSpan(new ForegroundColorSpan(color), start, line.length(), 33);
  }

  @Override public void onMessageAdded(String message) {
    lastMessage = message != null ? message : "";
    refreshMessage();
  }
  @Override public void onMessagesCleared() {
    lastMessage = "";
    moreCount = 0;
    refreshMessage();
  }
  @Override public void onMoreCountChanged(int count) {
    moreCount = count;
    refreshMessage();
  }

  private void refreshMessage() {
    messageLine.setText(lastMessage + (moreCount > 0 ? "  --More--" : ""));
  }

  private static TextView label(ForkFront activity, int size, boolean mono) {
    TextView view = new TextView(activity);
    view.setTextColor(TEXT);
    view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
    view.setIncludeFontPadding(false);
    if (mono) view.setTypeface(Typeface.MONOSPACE);
    return view;
  }

  private static GradientDrawable outline(ForkFront activity) {
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(PANEL);
    bg.setCornerRadius(dp(activity, 6));
    bg.setStroke(dp(activity, 1), BORDER);
    return bg;
  }

  private GradientDrawable keyOutline() {
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(0xFF2A2B32);
    bg.setCornerRadius(dp(activity, 5));
    bg.setStroke(dp(activity, 1), 0xFF4D4E55);
    return bg;
  }

  private static int dp(ForkFront activity, float n) {
    return (int) (n * activity.getResources().getDisplayMetrics().density + 0.5f);
  }
}
