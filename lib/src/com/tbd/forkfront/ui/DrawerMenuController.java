package com.tbd.forkfront.ui;

import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;
import com.tbd.forkfront.ForkFront;
import com.tbd.forkfront.NH_State;
import com.tbd.forkfront.R;
import com.tbd.forkfront.gamepad.GamepadDispatcher;
import com.tbd.forkfront.gamepad.UiContext;
import com.tbd.forkfront.gamepad.UiContextArbiter;
import java.util.Collections;

public class DrawerMenuController {

  private final ForkFront mActivity;
  private DrawerLayout mDrawerLayout;
  private DrawerUiCapture mDrawerUiCapture;

  public DrawerMenuController(ForkFront activity) {
    mActivity = activity;
  }

  public void setup(UiContextArbiter uiContextArbiter, GamepadDispatcher gamepadDispatcher) {
    mDrawerLayout = mActivity.findViewById(R.id.drawer_layout);
    NavigationView navigationView = mActivity.findViewById(R.id.nav_view);
    if (navigationView != null) {
      if (mActivity.isFixedTabletLayout()) {
        android.view.MenuItem edit = navigationView.getMenu().findItem(R.id.nav_edit_overlay);
        android.view.MenuItem keyboard = navigationView.getMenu().findItem(R.id.nav_keyboard);
        if (edit != null) edit.setVisible(false);
        if (keyboard != null) keyboard.setVisible(false);
      }
      navigationView.setNavigationItemSelectedListener(
          item -> {
            handleNavigationItemSelected(item.getItemId());
            if (mDrawerLayout != null) mDrawerLayout.closeDrawers();
            return true;
          });
    }

    if (mDrawerLayout != null) {
      mDrawerLayout.addDrawerListener(
          new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
              mDrawerLayout.invalidate();
              if (slideOffset > 0
                  && uiContextArbiter != null
                  && !uiContextArbiter.contains(UiContext.DRAWER_OPEN)) {
                uiContextArbiter.push(UiContext.DRAWER_OPEN);
              }
            }

            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
              if (uiContextArbiter != null && uiContextArbiter.current() != UiContext.DRAWER_OPEN) {
                uiContextArbiter.push(UiContext.DRAWER_OPEN);
              }
              NavigationView nav = mActivity.findViewById(R.id.nav_view);
              if (nav != null && gamepadDispatcher != null) {
                if (mDrawerUiCapture == null) {
                  mDrawerUiCapture = new DrawerUiCapture(mDrawerLayout, nav);
                }
                gamepadDispatcher.enterUiCapture(mDrawerUiCapture);
              }
              if (nav != null) focusFirstMenuItemWhenReady(nav);
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
              if (uiContextArbiter != null) {
                uiContextArbiter.remove(UiContext.DRAWER_OPEN);
              }
              if (gamepadDispatcher != null && mDrawerUiCapture != null) {
                gamepadDispatcher.exitUiCapture(mDrawerUiCapture);
              }
            }

            @Override
            public void onDrawerStateChanged(int newState) {
              if (newState != DrawerLayout.STATE_IDLE && uiContextArbiter != null) {
                uiContextArbiter.pushUnique(UiContext.DRAWER_OPEN);
              }
            }
          });
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mDrawerLayout != null) {
      int edgePx = (int) (32 * mActivity.getResources().getDisplayMetrics().density);
      mDrawerLayout.addOnLayoutChangeListener(
          (v, l, t, r, b, ol, ot, or, ob) -> {
            Rect rect = new Rect(v.getWidth() - edgePx, 0, v.getWidth(), v.getHeight());
            v.setSystemGestureExclusionRects(Collections.singletonList(rect));
          });
    }
  }

  public DrawerLayout getDrawerLayout() {
    return mDrawerLayout;
  }

  public void setDrawerEditMode(boolean editMode) {
    NavigationView navigationView = mActivity.findViewById(R.id.nav_view);
    if (navigationView == null) return;
    navigationView.getMenu().clear();
    navigationView.inflateMenu(editMode ? R.menu.drawer_menu_edit : R.menu.drawer_menu);
  }

  private void handleNavigationItemSelected(int itemId) {
    NH_State state = mActivity.getState();
    if (state == null) return;

    if (itemId == R.id.nav_keyboard) {
      mActivity.showGameKeyboard();
    } else if (itemId == R.id.nav_settings) {
      mActivity.launchSettings();
    } else if (itemId == R.id.nav_edit_overlay) {
      state.getWidgets().setEditMode(true);
    } else if (itemId == R.id.nav_save_game) {
      state.getCommands().sendKeyCmd('S');
    } else if (itemId == R.id.nav_quit) {
      showQuitConfirmation();
    } else if (itemId == R.id.nav_quit_no_save) {
      showQuitNoSaveConfirmation();
    } else if (itemId == R.id.nav_help) {
      state.getCommands().sendKeyCmd('?');
    } else if (itemId == R.id.nav_version) {
      state.getCommands().sendStringCmd("#version\n");
    } else if (itemId == R.id.nav_add_widget) {
      state
          .getWidgets()
          .showAddWidgetDialog(mActivity, state.getWidgets().getPrimaryWidgetLayout());
    } else if (itemId == R.id.nav_save_changes) {
      state.getWidgets().saveLayoutAndExitEditMode();
    } else if (itemId == R.id.nav_discard_changes) {
      state.getWidgets().discardChangesAndExitEditMode();
    }
  }

  private void showQuitConfirmation() {
    new androidx.appcompat.app.AlertDialog.Builder(mActivity)
        .setTitle("Save and Quit")
        .setMessage("Save your game and quit?")
        .setPositiveButton(
            android.R.string.ok,
            (dialog, which) -> {
              NH_State state = mActivity.getState();
              if (state != null) {
                state.getCommands().sendKeyCmd('S');
              }
            })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void showQuitNoSaveConfirmation() {
    new androidx.appcompat.app.AlertDialog.Builder(mActivity)
        .setTitle("Quit without Saving")
        .setMessage("Are you sure? All progress since last save will be lost!")
        .setPositiveButton(
            android.R.string.ok,
            (dialog, which) -> {
              NH_State state = mActivity.getState();
              if (state != null) {
                state.getCommands().sendStringCmd("#quit\n");
              }
            })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void focusFirstMenuItemWhenReady(NavigationView nav) {
    final NavigationView navFinal = nav;
    final int[] tries = {0};
    final android.view.ViewTreeObserver.OnGlobalLayoutListener[] holder =
        new android.view.ViewTreeObserver.OnGlobalLayoutListener[1];
    holder[0] =
        new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
          @Override
          public void onGlobalLayout() {
            View target = findFirstMenuItem(navFinal);
            if (target != null) {
              target.requestFocus();
              navFinal.getViewTreeObserver().removeOnGlobalLayoutListener(holder[0]);
            } else if (++tries[0] > 5) {
              navFinal.getViewTreeObserver().removeOnGlobalLayoutListener(holder[0]);
            }
          }
        };
    navFinal.getViewTreeObserver().addOnGlobalLayoutListener(holder[0]);
  }

  private View findFirstMenuItem(android.view.ViewGroup group) {
    for (int i = 0; i < group.getChildCount(); i++) {
      View child = group.getChildAt(i);
      if (child.getClass().getSimpleName().equals("NavigationMenuItemView")
          && child.isFocusable()
          && child.getVisibility() == View.VISIBLE) {
        return child;
      }
      if (child instanceof android.view.ViewGroup) {
        View found = findFirstMenuItem((android.view.ViewGroup) child);
        if (found != null) return found;
      }
    }
    return null;
  }

  private View findFirstFocusableChild(android.view.ViewGroup group) {
    for (int i = 0; i < group.getChildCount(); i++) {
      View child = group.getChildAt(i);
      if (child.isFocusable() && child.getVisibility() == View.VISIBLE) {
        return child;
      }
      if (child instanceof android.view.ViewGroup) {
        View found = findFirstFocusableChild((android.view.ViewGroup) child);
        if (found != null) return found;
      }
    }
    return null;
  }
}
