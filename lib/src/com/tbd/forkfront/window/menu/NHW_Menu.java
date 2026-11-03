package com.tbd.forkfront.window.menu;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import androidx.annotation.MainThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.tbd.forkfront.engine.EngineCommandSender;
import com.tbd.forkfront.gamepad.UiContext;
import com.tbd.forkfront.input.Input;
import com.tbd.forkfront.input.KeyEventResult;
import com.tbd.forkfront.window.AbstractNhWindow;
import com.tbd.forkfront.window.map.Tileset;
import com.tbd.forkfront.window.text.TextAttr;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@MainThread
public class NHW_Menu extends AbstractNhWindow {
  public enum Type {
    None,
    Menu,
    Text
  }

  public ArrayList<MenuItem> mItems;
  public String mTitle;
  public SpannableStringBuilder mBuilder;
  public Type mType = Type.None;
  public MenuSelectMode mHow;
  public int mKeyboardCount = -1;
  public final Tileset mTileset;
  private Fragment mFragment;
  private boolean mRestoreKeyboardAfterClose;

  public NHW_Menu(int wid, AppCompatActivity context, EngineCommandSender io, Tileset tileset) {
    super(wid, context, io);
    mTileset = tileset;
  }

  public void sendSelectChecked(ArrayList<MenuItem> items) {
    mIO.sendSelectCmd(items);
  }

  public void sendCancelSelect() {
    mIO.sendCancelSelectCmd();
  }

  public void sendSelectOne(MenuItem item, int count) {
    mIO.sendSelectCmd(item.getId(), count);
  }

  public void sendSelectNone() {
    mIO.sendSelectNoneCmd();
  }

  public void sendContinue() {
    mIO.sendKeyCmd(' ');
  }

  private static void assignAccelerators(List<MenuItem> items) {
    for (MenuItem i : items) {
      if (i.hasAcc()) return;
    }
    char acc = 'a';
    for (MenuItem i : items) {
      if (!i.isHeader() && i.isSelectable() && acc != 0) {
        i.setAcc(acc);
        acc++;
        if (acc == 'z' + 1) acc = 'A';
        else if (acc == 'Z' + 1) acc = 0;
      }
    }
  }

  @Override
  public String getTitle() {
    return mTitle;
  }

  @Override
  public void show(boolean bBlocking) {
    mIsVisible = true;
    if (mBuilder != null && mType == Type.None) {
      mType = Type.Text;
    }
    mKeyboardCount = -1;
    mIsBlocking = bBlocking;
    mRestoreKeyboardAfterClose =
        mContext instanceof com.tbd.forkfront.ForkFront
            && ((com.tbd.forkfront.ForkFront) mContext).isGameKeyboardVisible();

    if (mFragment == null || !mFragment.isAdded()) {
      if (mType == Type.Text) mFragment = new MenuTextFragment();
      else mFragment = new MenuFragment();

      Bundle args = new Bundle();
      args.putInt("wid", mWid);
      mFragment.setArguments(args);
      addFragment(mFragment);
    }
    mState
        .getGamepadContext()
        .pushContext(mType == Type.Text ? UiContext.MENU_TEXT : UiContext.MENU);
  }

  @Override
  protected void addFragment(Fragment fragment) {
    if (mType == Type.Menu && mContext instanceof com.tbd.forkfront.ForkFront) {
      com.tbd.forkfront.ForkFront tablet = (com.tbd.forkfront.ForkFront) mContext;
      int host = tablet.getFixedTabletMenuHostId();
      if (host != android.view.View.NO_ID) {
        tablet.setFixedTabletMenuVisible(true);
        mContext.getSupportFragmentManager().beginTransaction()
            .add(host, fragment, "nhw_" + mWid)
            .commitAllowingStateLoss();
        return;
      }
    }
    super.addFragment(fragment);
  }

  private void hide() {
    if (mIsVisible && mType == Type.Menu && mContext instanceof com.tbd.forkfront.ForkFront) {
      ((com.tbd.forkfront.ForkFront) mContext).setFixedTabletMenuVisible(false);
    }
    if (mIsVisible) {
      mState
          .getGamepadContext()
          .popContext(mType == Type.Text ? UiContext.MENU_TEXT : UiContext.MENU);
    }
    mIsVisible = false;
    removeFragment();
  }

  @Override
  public void destroy() {
    mIsVisible = false;
    close();
  }

  @Override
  public boolean handleGamepadKey(KeyEvent ev) {
    if (mFragment != null && mFragment.isAdded()) {
      if (mFragment instanceof MenuFragment) return ((MenuFragment) mFragment).handleGamepadKey(ev);
      if (mFragment instanceof MenuTextFragment)
        return ((MenuTextFragment) mFragment).handleGamepadKey(ev);
    }
    return false;
  }

  @Override
  public boolean handleGamepadMotion(MotionEvent ev) {
    if (mFragment != null && mFragment.isAdded()) {
      if (mFragment instanceof MenuFragment)
        return ((MenuFragment) mFragment).handleGamepadMotion(ev);
      if (mFragment instanceof MenuTextFragment)
        return ((MenuTextFragment) mFragment).handleGamepadMotion(ev);
    }
    return false;
  }

  public void close() {
    boolean restoreKeyboard = mRestoreKeyboardAfterClose;
    mRestoreKeyboardAfterClose = false;
    hide();
    super.close();
    mIsBlocking = false;
    mItems = null;
    mBuilder = null;
    mType = Type.None;
    mKeyboardCount = -1;

    if (restoreKeyboard && mContext instanceof com.tbd.forkfront.ForkFront) {
      ((com.tbd.forkfront.ForkFront) mContext).showGameKeyboard();
    }
  }

  protected void removeFragment() {
    super.removeFragment();
    mFragment = null;
  }

  @Override
  public void clear() {
    // Menus are ephemeral and do not support clear.
    // This is a safe no-op to satisfy the NH_Window contract.
  }

  @Override
  public void printString(final int attr, final String str, int append, int color) {
    if (mBuilder == null) {
      mBuilder = new SpannableStringBuilder(str);
      mItems = null;
    } else {
      mBuilder.append('\n');
      mBuilder.append(TextAttr.style(str, attr, mContext));
    }
    if (mFragment != null && mFragment.isAdded()) {
      if (mFragment instanceof MenuFragment) ((MenuFragment) mFragment).refresh();
      if (mFragment instanceof MenuTextFragment) ((MenuTextFragment) mFragment).refresh();
    }
  }

  @Override
  public void setCursorPos(int x, int y) {}

  @Override
  public boolean isVisible() {
    return mIsVisible;
  }

  public void startMenu() {
    mItems = new ArrayList<MenuItem>(100);
    mBuilder = null;
  }

  public void addMenu(
      int tile,
      long ident,
      int accelerator,
      int groupacc,
      int attr,
      String str,
      int preselected,
      int color) {
    if (str.length() == 0 && tile < 0) return;
    // start_menu is not always called
    if (mItems == null) startMenu();
    mItems.add(
        new MenuItem(tile, ident, accelerator, groupacc, attr, str, preselected, color, mContext));
  }

  public void endMenu(String prompt) {
    mTitle = prompt;
  }

  @Override
  public KeyEventResult handleKeyDown(
      char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount) {
    if (mFragment != null && mFragment.isAdded()) {
      android.util.Log.d("NHW_Menu", "Delegating handleKeyDown to fragment");
      if (mFragment instanceof MenuFragment)
        return ((MenuFragment) mFragment).handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount);
      if (mFragment instanceof MenuTextFragment)
        return ((MenuTextFragment) mFragment)
            .handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount);
    }
    android.util.Log.d("NHW_Menu", "Fragment not ready for handleKeyDown");
    return KeyEventResult.IGNORED;
  }

  public void selectMenu(MenuSelectMode how) {
    mType = Type.Menu;
    mKeyboardCount = -1;
    mHow = how;
    assignAccelerators(mItems);
    show(false);
  }

  @Override
  public void preferencesUpdated(SharedPreferences prefs) {
    if (mFragment != null && mFragment.isAdded()) {
      if (mFragment instanceof MenuFragment) ((MenuFragment) mFragment).refresh();
      if (mFragment instanceof MenuTextFragment) ((MenuTextFragment) mFragment).refresh();
    }
  }
}
