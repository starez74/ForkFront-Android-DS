package com.tbd.forkfront.dialog;

import android.content.Context;
import android.content.SharedPreferences.Editor;
import android.text.InputFilter;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import com.tbd.forkfront.*;
import com.tbd.forkfront.R;
import com.tbd.forkfront.engine.EngineCommandSender;
import com.tbd.forkfront.gamepad.UiContext;
import com.tbd.forkfront.input.Input;
import com.tbd.forkfront.input.KeyEventResult;
import com.tbd.forkfront.ui.Util;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class NH_GetLine {
  private static final int MAX_HISTORY = 10;
  private UI mUI;
  private EngineCommandSender mIO;
  private String mTitle;
  private int mMaxChars;
  private NH_State mState;
  private Context mContext;
  private List<String> mHistory;

  public NH_GetLine(EngineCommandSender io, NH_State state) {
    mIO = io;
    mState = state;
  }

  public void show(AppCompatActivity context, final String title, final int nMaxChars) {
    mContext = context;
    mTitle = title;
    mMaxChars = nMaxChars;
    mHistory = loadHistory();
    boolean useNativeKeyboard = !(context instanceof ForkFront)
        || !((ForkFront) context).isFixedTabletLayout();
    mUI = new UI(context, mIO, true, useNativeKeyboard, getInitText());
    mState.getGamepadContext().pushContext(UiContext.GETLINE);
  }

  public void setContext(AppCompatActivity context) {
    mContext = context;
    if (mUI != null) {
      boolean useNativeKeyboard = !(context instanceof ForkFront)
          || !((ForkFront) context).isFixedTabletLayout();
      mUI = new UI(context, mIO, mUI.mSaveHistory, useNativeKeyboard, getInitText());
    }
  }

  private String getInitText() {
    if (mTitle.contains("For what do you wish")) return "";
    if (mTitle.startsWith("Replace annotation \"")) {
      int i = mTitle.lastIndexOf('"');
      if (i > 19) return mTitle.substring(20, i);
    }
    if (mTitle.startsWith("Replace previous annotation \"")) {
      int i = mTitle.lastIndexOf('"');
      if (i > 28) return mTitle.substring(29, i);
    }
    if (mTitle.startsWith("What do you want to call") || mTitle.startsWith("Call ")) {
      int i = mTitle.indexOf(" called ");
      if (i > 0) return mTitle.substring(i + 8, mTitle.length() - 1);
    }
    if (mTitle.startsWith("What do you want to name")) {
      int i = mTitle.indexOf(" named ");
      if (i > 0) return mTitle.substring(i + 7, mTitle.length() - 1);
    }
    if (mHistory.size() > 0) return mHistory.get(0);
    return "";
  }

  public KeyEventResult handleKeyDown(
      char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount) {
    if (mUI == null) return KeyEventResult.IGNORED;
    return mUI.handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount);
  }

  /**
   * Touchscreen keyboard clicks bypass Android's hardware-key dispatch. Forward
   * them into an active GetLine EditText, preserving its input filters and cursor
   * selection. Normal hardware keys continue through handleKeyDown unchanged.
   */
  public boolean handleFixedTabletKey(char ch, int keyCode) {
    if (mUI == null || mUI.mRoot == null || mUI.mInput == null) return false;

    if (keyCode == KeyEvent.KEYCODE_ENTER) {
      mUI.ok();
      return true;
    }
    if (keyCode == KeyEvent.KEYCODE_ESCAPE || ch == '\033') {
      mUI.cancel();
      return true;
    }

    android.text.Editable text = mUI.mInput.getText();
    int start = Math.max(0, mUI.mInput.getSelectionStart());
    int end = Math.max(0, mUI.mInput.getSelectionEnd());
    int left = Math.min(start, end);
    int right = Math.max(start, end);

    if (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL) {
      if (left != right) text.delete(left, right);
      else if (keyCode == KeyEvent.KEYCODE_DEL && left > 0) text.delete(left - 1, left);
      else if (keyCode == KeyEvent.KEYCODE_FORWARD_DEL && left < text.length()) {
        text.delete(left, left + 1);
      }
      return true;
    }

    if (ch >= ' ' && ch != 0x7f) {
      text.replace(left, right, String.valueOf(ch));
    }
    // Never forward unrecognised control keys from GetLine to gameplay.
    return true;
  }

  public boolean isFocused() {
    return mUI != null && mUI.mRoot != null && mUI.mInput != null && mUI.mInput.isFocused();
  }

  public boolean handleGamepadKey(KeyEvent ev) {
    return mUI != null && mUI.handleGamepadKey(ev);
  }

  public boolean handleGamepadMotion(MotionEvent ev) {
    return false;
  }

  private void storeHistory(List<String> history, String newString) {
    if (newString.trim().length() == 0) return;
    history.remove(newString);
    history.add(0, newString);
    while (history.size() > MAX_HISTORY) history.remove(history.size() - 1);
    StringBuilder builder = new StringBuilder();
    for (String h : history) {
      h = h.replace("%", "%1");
      h = h.replace(";", "%2");
      builder.append(h);
      builder.append(';');
    }
    Editor editor = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
    editor.putString("lineHistory", builder.toString());
    editor.apply();
  }

  private List<String> loadHistory() {
    String value =
        PreferenceManager.getDefaultSharedPreferences(mContext).getString("lineHistory", "");
    String[] strings = value.split(";");
    List<String> history = new ArrayList<>(strings.length);
    for (String s : strings) {
      s = s.replace("%2", ";");
      s = s.replace("%1", "%");
      history.add(s);
    }
    return history;
  }

  private class UI extends BaseModalUi {
    private EditText mInput;

    public boolean handleGamepadKey(KeyEvent ev) {
      if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
      switch (ev.getKeyCode()) {
        case KeyEvent.KEYCODE_BUTTON_B:
        case KeyEvent.KEYCODE_BUTTON_SELECT:
          cancel();
          return true;
        case KeyEvent.KEYCODE_BUTTON_L1:
          // history cycle - TODO
          return true;
        case KeyEvent.KEYCODE_BUTTON_R1:
          // history cycle - TODO
          return true;
      }
      return false;
    }

    private ListView mHistoryList;
    // private NH_Dialog mDialog;
    private View mRoot;
    private ArrayAdapter<String> mAdapter;
    public boolean mSaveHistory;

    public UI(
        AppCompatActivity context,
        EngineCommandSender io,
        boolean saveHistory,
        boolean showKeyboard,
        String initText) {
      super(context, io);

      mRoot = Util.inflate(context, R.layout.dialog_getline, R.id.dlg_frame);
      mInput = (EditText) mRoot.findViewById(R.id.input);
      mInput.setFilters(new InputFilter[] {new InputFilter.LengthFilter(mMaxChars)});
      mInput.setOnKeyListener(
          new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
              if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

              if (keyCode == KeyEvent.KEYCODE_ENTER) ok();
              else if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE)
                cancel();
              else if (keyCode
                  == KeyEvent.KEYCODE_SEARCH) // This is doing weird stuff, might as well block it
              return true;
              return false;
            }
          });

      ((TextView) mRoot.findViewById(R.id.title)).setText(mTitle);

      mRoot
          .findViewById(R.id.history)
          .setOnClickListener(
              new OnClickListener() {
                @Override
                public void onClick(View v) {
                  if (v != null) {
                    toggleHistory();
                  }
                }
              });

      mHistoryList = (ListView) mRoot.findViewById(R.id.history_list);

      mAdapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, mHistory);
      mHistoryList.setAdapter(mAdapter);

      mHistoryList.setVisibility(View.GONE);

      mHistoryList.setOnItemClickListener(
          new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
              mInput.setText(mAdapter.getItem(position));
              mInput.selectAll();
              mHistoryList.setVisibility(View.GONE);
            }
          });

      mHistoryList.setOnKeyListener(
          new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
              return false;
            }
          });

      mRoot
          .findViewById(R.id.btn_0)
          .setOnClickListener(
              new OnClickListener() {
                @Override
                public void onClick(View v) {
                  if (v != null) {
                    ok();
                  }
                }
              });
      mRoot
          .findViewById(R.id.btn_1)
          .setOnClickListener(
              new OnClickListener() {
                @Override
                public void onClick(View v) {
                  cancel();
                }
              });

      mState.getMapInput().hideControls();
      mInput.requestFocus();

      mInput.setText(initText);
      mInput.selectAll();

      if (showKeyboard) Util.showKeyboard(context, mInput);
    }

    protected void toggleHistory() {
      mHistoryList.setVisibility(
          mHistoryList.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    public KeyEventResult handleKeyDown(
        char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount) {
      if (mRoot == null) return KeyEventResult.IGNORED;

      switch (keyCode) {
        case KeyEvent.KEYCODE_DPAD_UP:
        case KeyEvent.KEYCODE_DPAD_DOWN:
        case KeyEvent.KEYCODE_DPAD_LEFT:
        case KeyEvent.KEYCODE_DPAD_RIGHT:
        case KeyEvent.KEYCODE_DPAD_CENTER:
        case KeyEvent.KEYCODE_DEL:
        case KeyEvent.KEYCODE_FORWARD_DEL:
          return KeyEventResult.RETURN_TO_SYSTEM;

        case KeyEvent.KEYCODE_BACK:
          cancel();
          break;

        case KeyEvent.KEYCODE_ENTER:
          ok();
          break;

        default:
          if (ch == '\033') cancel();
          break;
      }
      return KeyEventResult.HANDLED;
    }

    @Override
    public void dismiss() {
      Util.hideKeyboard(mContext, mInput);
      if (mRoot != null) {
        mRoot.setVisibility(View.GONE);
        super.dismiss();
        mState.getMapInput().showControls();
      }
      mUI = null;
    }

    private void ok() {
      if (mRoot != null) {
        String text = mInput.getText().toString();
        mIO.sendLineCmd(text);
        if (mSaveHistory) storeHistory(mHistory, text);
        mState.getGamepadContext().popContext(UiContext.GETLINE);
        dismiss();
      }
    }

    private void cancel() {
      if (mRoot != null) {
        mIO.sendLineCmd("\033 ");
        mState.getGamepadContext().popContext(UiContext.GETLINE);
        dismiss();
      }
    }
  }
}
