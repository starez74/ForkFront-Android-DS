package com.tbd.forkfront.window.message;

import android.content.SharedPreferences;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.tbd.forkfront.R;
import com.tbd.forkfront.engine.NetHackIO;
import com.tbd.forkfront.gamepad.GamepadDeviceWatcher;
import com.tbd.forkfront.input.Input;
import com.tbd.forkfront.input.KeyEventResult;
import com.tbd.forkfront.window.NH_Window;
import com.tbd.forkfront.window.text.NHW_Text;
import com.tbd.forkfront.window.text.TextAttr;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class NHW_Message implements NH_Window {
  // Listener interface for message updates
  public interface MessageUpdateListener {
    void onMessageAdded(String message);

    // Replaces the latest line when NetHack incrementally echoes typed commands.
    void onMessageUpdated(String message);

    void onMessagesCleared();

    void onMoreCountChanged(int moreCount);
  }

  public static final int SHOW_MAX_LINES = 10;

  private NetHackIO mIO;
  private AppCompatActivity mContext;
  private final int MaxLog = 256;
  private String[] mLog = new String[MaxLog];
  private int mCurrentIdx;
  private int mLogCount;
  private int mDispCount;
  private UI mUI;
  private NHW_Text mLogView;
  private boolean mIsVisible;
  private int mWid;
  private List<MessageUpdateListener> mListeners;

  public NHW_Message(AppCompatActivity context, NetHackIO io) {
    mIO = io;
    mListeners = new ArrayList<>();
    setContext(context);
  }

  public void addListener(MessageUpdateListener listener) {
    if (!mListeners.contains(listener)) {
      mListeners.add(listener);
      // Send current state to new listener
      int lineCount = Math.min(SHOW_MAX_LINES, mDispCount);
      int iStart = mCurrentIdx - lineCount + 1;
      for (int i = 0; i < lineCount; i++) {
        listener.onMessageAdded(mLog[getIndex(iStart + i)]);
      }
      listener.onMoreCountChanged(Math.max(0, mDispCount - SHOW_MAX_LINES));
    }
  }

  public void removeListener(MessageUpdateListener listener) {
    mListeners.remove(listener);
  }

  public String getRecentMessages(int maxLines) {
    return getLogLine(maxLines);
  }

  @Override
  public String getTitle() {
    return "NHW_Message";
  }

  @Override
  public void setContext(AppCompatActivity context) {
    if (mContext == context) return;
    mContext = context;
    mUI = new UI();
    if (mIsVisible) mUI.showInternal();
    else mUI.hideInternal();
    if (mLogView != null) mLogView.setContext(context);
  }

  @Override
  public KeyEventResult handleKeyDown(
      char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount) {
    KeyEventResult ret;
    if (isLogShowing()
        && (ret = mLogView.handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount))
            != KeyEventResult.IGNORED) return ret;
    return mUI.handleKeyDown(ch) ? KeyEventResult.HANDLED : KeyEventResult.IGNORED;
  }

  @Override
  public void clear() {
    mDispCount = 0;
    mLogCount = 0;
    mCurrentIdx = 0;
    mUI.clear();
    notifyMessagesCleared();
  }

  private int getIndex(int i) {
    if (mLogCount == 0) return 0;
    return i & (MaxLog - 1);
  }

  @Override
  public void printString(int attr, String str, int append, int color) {
    mCurrentIdx = getIndex(mLogCount - 1);

    if (append < 0 && mLogCount > 0) {
      append++;
      String l = mLog[mCurrentIdx];
      if (append < -l.length()) append = -l.length();
      l = l.substring(0, l.length() + append);
      mLog[mCurrentIdx] = l + str;
      notifyMessageUpdated(mLog[mCurrentIdx]);
    } else if (append > 0 && mLogCount > 0) {
      if (str.length() > 0) {
        mLog[mCurrentIdx] = mLog[mCurrentIdx] + str;
        notifyMessageUpdated(mLog[mCurrentIdx]);
      }
    } else {
      addMessage(str);
    }
    mUI.update();
  }

  @Override
  public void setCursorPos(int x, int y) {}

  private void addMessage(String newMsg) {
    mCurrentIdx = getIndex(mCurrentIdx + 1);
    mLog[mCurrentIdx] = newMsg;
    mDispCount++;
    mLogCount++;
    notifyMessageAdded(newMsg);
    notifyMoreCountChanged();
  }

  private void notifyMessageAdded(String message) {
    for (MessageUpdateListener listener : mListeners) {
      listener.onMessageAdded(message);
    }
  }

  private void notifyMessageUpdated(String message) {
    for (MessageUpdateListener listener : mListeners) {
      listener.onMessageUpdated(message);
    }
  }

  private void notifyMessagesCleared() {
    for (MessageUpdateListener listener : mListeners) {
      listener.onMessagesCleared();
    }
  }

  private void notifyMoreCountChanged() {
    int moreCount = Math.max(0, mDispCount - SHOW_MAX_LINES);
    for (MessageUpdateListener listener : mListeners) {
      listener.onMoreCountChanged(moreCount);
    }
  }

  public String getLogLine(int maxLineCount) {
    if (mDispCount <= 0) return "";

    int nLines = Math.min(mDispCount, maxLineCount);

    StringBuilder line = new StringBuilder();
    for (int i = nLines - 1; i >= 0; i--) {
      int idx = getIndex(mCurrentIdx - i);
      line.append(mLog[idx]);
      line.append(' ');
    }
    line.append('\n');
    return line.toString();
  }

  @Override
  public void show(boolean bBlocking) {
    mIsVisible = true;
    mUI.showInternal();
    if (bBlocking) {
      // unblock immediately
      mIO.sendKeyCmd(' ');
    }
  }

  @Override
  public void destroy() {
    mIsVisible = false;
    mUI.hideInternal();
  }

  public void showLog(boolean bBlocking) {
    if (mLogView == null) mLogView = new NHW_Text(0, mContext, mIO);

    boolean highlightNew = mDispCount > SHOW_MAX_LINES;

    int nLogs = 0;
    for (int n = 0; n < MaxLog; n++) {
      if (mLog[n] != null) nLogs++;
    }

    int attr = 0;
    mLogView.clear();
    int i = mCurrentIdx + 1;
    for (int n = 0; n < MaxLog; n++, i++) {
      String s = mLog[getIndex(i)];
      if (s != null) {
        nLogs--;
        if (highlightNew && nLogs < mDispCount) attr = TextAttr.ATTR_BOLD;
        mLogView.printString(attr, s, 0, 15);
      }
    }
    mLogView.show(bBlocking);
    mLogView.scrollToEnd();
    clear();
    mUI.update();
  }

  private boolean isLogShowing() {
    return mLogView != null && mLogView.isVisible();
  }

  public void setId(int wid) {
    mWid = wid;
  }

  @Override
  public int id() {
    return mWid;
  }

  public boolean isMoreVisible() {
    return mUI != null && mUI.isMoreVisible();
  }

  @Override
  public boolean handleGamepadKey(android.view.KeyEvent ev) {
    if (isLogShowing()) return mLogView.handleGamepadKey(ev);
    if (!isMoreVisible()) return false;
    if (ev.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;

    switch (ev.getKeyCode()) {
      case android.view.KeyEvent.KEYCODE_BUTTON_A:
      case android.view.KeyEvent.KEYCODE_BUTTON_B:
      case android.view.KeyEvent.KEYCODE_BUTTON_X:
      case android.view.KeyEvent.KEYCODE_BUTTON_Y:
      case android.view.KeyEvent.KEYCODE_BUTTON_SELECT:
        showLog(false);
        return true;
    }
    return false;
  }

  @Override
  public boolean handleGamepadMotion(android.view.MotionEvent ev) {
    return false;
  }

  @Override
  public boolean isVisible() {
    return mIsVisible;
  }

  @Override
  public void preferencesUpdated(SharedPreferences prefs) {}

  private class UI {
    private TextView m_view;
    private TextView m_more;

    public UI() {
      m_view = (TextView) mContext.findViewById(R.id.nh_message);
      m_more = (TextView) mContext.findViewById(R.id.more);
      m_more.setVisibility(View.GONE);
      m_more.setFocusable(true);
      if (GamepadDeviceWatcher.isGamepadConnected(mContext)) m_more.setFocusableInTouchMode(true);
      m_more.setOnClickListener(
          new OnClickListener() {
            @Override
            public void onClick(View v) {
              showLog(false);
            }
          });
    }

    public boolean isMoreVisible() {
      return m_more.getVisibility() == View.VISIBLE;
    }

    public void showInternal() {
      update();
      m_view.setVisibility(View.VISIBLE);
    }

    public void hideInternal() {
      //	m_view.setVisibility(View.INVISIBLE);
      //	m_more.setVisibility(View.GONE);
    }

    public void clear() {
      m_more.setVisibility(View.GONE);
      m_view.setText("");
    }

    public void update() {
      updateText();
      if (mDispCount > SHOW_MAX_LINES) {
        m_more.setText("--" + Integer.toString(mDispCount - SHOW_MAX_LINES) + " more--");
        m_more.setVisibility(View.VISIBLE);
      } else m_more.setVisibility(View.GONE);
    }

    public boolean handleGamepadKey(android.view.KeyEvent ev) {
      if (isMoreVisible() && !isLogShowing()) {
        showLog(false);
        return true;
      }
      return false;
    }

    public boolean handleKeyDown(char ch) {
      if (isMoreVisible() && ch == ' ' && !isLogShowing()) {
        showLog(false);
        return true;
      }
      return false;
    }

    private void updateText() {
      m_view.setText("");
      if (mDispCount > 0) {
        int lineCount = Math.min(SHOW_MAX_LINES, mDispCount);
        int iStart = mCurrentIdx - lineCount + 1;
        for (int i = 0; i < lineCount; i++) {
          String msg = mLog[getIndex(iStart + i)];
          if (i > 0) m_view.append("\n");
          m_view.append(msg);
        }
      }
    }
  }
}
