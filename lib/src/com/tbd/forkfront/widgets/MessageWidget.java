package com.tbd.forkfront.widgets;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import com.tbd.forkfront.R;
import com.tbd.forkfront.ui.ThemeUtils;
import com.tbd.forkfront.window.message.NHW_Message;
import com.tbd.forkfront.window.text.NH_TextView;
import java.util.ArrayList;
import java.util.List;

public class MessageWidget extends ControlWidget implements NHW_Message.MessageUpdateListener {
  private NHW_Message mMessageWindow;
  private NH_TextView mMessageView;
  private NH_TextView mMoreView;
  private List<String> mRecentMessages;
  private int mMoreCount;

  public MessageWidget(Context context, NHW_Message messageWindow) {
    super(context, createMessageView(context), "message");
    mMessageWindow = messageWindow;
    mRecentMessages = new ArrayList<>();

    // Initialize view references
    LinearLayout container = (LinearLayout) getContentView();
    mMessageView = (NH_TextView) container.getChildAt(0);
    mMoreView = (NH_TextView) container.getChildAt(1);

    // Register as listener
    messageWindow.addListener(this);
  }

  private static LinearLayout createMessageView(Context context) {
    LinearLayout container = new LinearLayout(context);
    container.setOrientation(LinearLayout.VERTICAL);
    container.setLayoutParams(
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

    int onSurface =
        ThemeUtils.resolveColor(context, R.attr.colorOnSurface, R.color.md_theme_onSurface);
    int primary = ThemeUtils.resolveColor(context, R.attr.colorPrimary, R.color.md_theme_primary);
    int onPrimary =
        ThemeUtils.resolveColor(context, R.attr.colorOnPrimary, R.color.md_theme_onPrimary);
    // Create message TextView
    NH_TextView messageView = new NH_TextView(context);
    messageView.setTextColor(onSurface);
    messageView.setTextSize(15);
    messageView.setLayoutParams(
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

    // Create --More-- TextView
    NH_TextView moreView = new NH_TextView(context);
    moreView.setText("--More--");
    moreView.setTextColor(onPrimary);
    moreView.setBackgroundColor(primary);
    moreView.setTextSize(15);
    moreView.setClickable(true);
    moreView.setVisibility(View.GONE);
    moreView.setPadding(8, 2, 8, 2);
    moreView.setLayoutParams(
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

    container.addView(messageView);
    container.addView(moreView);

    return container;
  }

  // MessageUpdateListener implementation

  @Override
  public void onMessageAdded(String message) {
    mRecentMessages.add(message);
    // Keep only the last SHOW_MAX_LINES messages
    while (mRecentMessages.size() > NHW_Message.SHOW_MAX_LINES) {
      mRecentMessages.remove(0);
    }
    updateDisplay();
  }

  @Override
  public void onMessageUpdated(String message) {
    if (mRecentMessages.isEmpty()) {
      onMessageAdded(message);
    } else {
      mRecentMessages.set(mRecentMessages.size() - 1, message);
      updateDisplay();
    }
  }

  @Override
  public void onMessagesCleared() {
    mRecentMessages.clear();
    mMoreCount = 0;
    updateDisplay();
  }

  @Override
  public void onMoreCountChanged(int moreCount) {
    mMoreCount = moreCount;
    updateMoreButton();
  }

  public void onFlush() {}

  public void onReset() {
    onMessagesCleared();
  }

  @Override
  public void setFontSize(int size) {
    super.setFontSize(size);
    mMessageView.setBaseTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, size);
    mMoreView.setBaseTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, size);
  }

  private void updateDisplay() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < mRecentMessages.size(); i++) {
      sb.append(mRecentMessages.get(i));
      if (i < mRecentMessages.size() - 1) {
        sb.append("\n");
      }
    }
    mMessageView.setText(sb.toString());
    updateMoreButton();
  }

  private void updateMoreButton() {
    if (mMoreCount > 0) {
      mMoreView.setVisibility(View.VISIBLE);
      mMoreView.setText("--More (" + mMoreCount + ")--");
    } else {
      mMoreView.setVisibility(View.GONE);
    }
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    if (mMessageWindow != null) {
      mMessageWindow.removeListener(this);
    }
  }
}
