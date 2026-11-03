/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tbd.forkfront;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.tbd.forkfront.commands.CommandPickerFragment;
import com.tbd.forkfront.context.CmdRegistry;
import com.tbd.forkfront.engine.ByteDecoder;
import com.tbd.forkfront.engine.CP437;
import com.tbd.forkfront.engine.NetHackViewModel;
import com.tbd.forkfront.engine.UpdateAssets;
import com.tbd.forkfront.gamepad.GamepadDeviceWatcher;
import com.tbd.forkfront.gamepad.GamepadDispatcher;
import com.tbd.forkfront.gamepad.UiActionId;
import com.tbd.forkfront.gamepad.UiContext;
import com.tbd.forkfront.gamepad.UiContextArbiter;
import com.tbd.forkfront.input.Input;
import com.tbd.forkfront.input.Input.Modifier;
import com.tbd.forkfront.settings.Settings;
import com.tbd.forkfront.ui.CommandPaletteController;
import com.tbd.forkfront.ui.DrawerMenuController;
import com.tbd.forkfront.ui.SecondaryDisplayController;
import java.io.File;
import java.util.EnumSet;
import java.util.Locale;

public class ForkFront extends AppCompatActivity implements ForkFrontHost {
  private NetHackViewModel mViewModel;
  private com.tbd.forkfront.ui.FixedTabletUi mFixedTabletUi;
  private boolean mBackTracking;
  private DrawerMenuController mDrawerMenuController;
  private SecondaryDisplayController mSecondaryDisplayController;

  // Gamepad support
  private GamepadDispatcher mGamepadDispatcher;
  private UiContextArbiter mUiContextArbiter;
  private GamepadDeviceWatcher mGamepadDeviceWatcher;

  // Command palette bottom sheet
  private CommandPaletteController mCommandPaletteController;

  // Command picker (gamepad-only full-screen picker)
  private CommandPickerFragment mCommandPickerFragment;

  private final GamepadDispatcher.SyntheticDispatcher mSyntheticDispatcher =
      new GamepadDispatcher.SyntheticDispatcher() {
        @Override
        public void dispatchKey(int keyCode) {
          android.view.View decor = getWindow().getDecorView();
          long now = android.os.SystemClock.uptimeMillis();
          android.view.KeyEvent down =
              new android.view.KeyEvent(
                  now,
                  now,
                  android.view.KeyEvent.ACTION_DOWN,
                  keyCode,
                  0,
                  0,
                  android.view.KeyCharacterMap.VIRTUAL_KEYBOARD,
                  0,
                  0,
                  GamepadDispatcher.SOURCE_SYNTHETIC);
          android.view.KeyEvent up =
              new android.view.KeyEvent(
                  now,
                  now,
                  android.view.KeyEvent.ACTION_UP,
                  keyCode,
                  0,
                  0,
                  android.view.KeyCharacterMap.VIRTUAL_KEYBOARD,
                  0,
                  0,
                  GamepadDispatcher.SOURCE_SYNTHETIC);
          decor.dispatchKeyEvent(down);
          decor.dispatchKeyEvent(up);
        }

        @Override
        public void dispatchBack() {
          onBackPressed();
        }
      };

  // Modern Activity Result API for settings
  private final ActivityResultLauncher<Intent> mSettingsLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            // Called when Settings activity returns
            if (mUiContextArbiter != null) {
              mUiContextArbiter.remove(UiContext.SETTINGS_OPEN);
            }
            NH_State state = getState();
            if (state != null) {
              state.getPrefs().apply();
            }
          });

  private final ActivityResultLauncher<String> mPermissionLauncher =
      registerForActivityResult(
          new ActivityResultContracts.RequestPermission(),
          isGranted -> {
            if (isGranted) {
              goodToGo();
            } else {
              finish();
            }
          });

  @Override
  public void onCreate(Bundle savedInstanceState) {
    // Apply theme mode before super.onCreate
    String themeMode =
        PreferenceManager.getDefaultSharedPreferences(this).getString("theme_mode", "-1");
    AppCompatDelegate.setDefaultNightMode(Integer.parseInt(themeMode));

    super.onCreate(savedInstanceState);

    Log.print("onCreate");

    // Enable edge-to-edge display
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

    // Complete edge-to-edge configuration
    Window window = getWindow();

    // Make system bars transparent
    window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
    window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);

    // Enable drawing into display cutout area (notches)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      window.getAttributes().layoutInDisplayCutoutMode =
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
    }

    // Configure system bar appearance based on theme
    WindowInsetsControllerCompat insetsController =
        WindowCompat.getInsetsController(window, window.getDecorView());
    if (insetsController != null) {
      boolean isDark =
          (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
              == Configuration.UI_MODE_NIGHT_YES;
      // Use dark icons for status/nav bars on light backgrounds (light theme)
      insetsController.setAppearanceLightStatusBars(!isDark);
      insetsController.setAppearanceLightNavigationBars(!isDark);

      // Sticky immersive: hide both bars; swipe from a hidden bar's edge
      // briefly reveals it, then it auto-hides again.
      insetsController.hide(WindowInsetsCompat.Type.systemBars());
      insetsController.setSystemBarsBehavior(
          WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    // Back-press handling: dismiss command picker or collapse palette
    // sheet if open, otherwise let the default back behavior run.
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new androidx.activity.OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                if (mCommandPickerFragment != null && mCommandPickerFragment.isAdded()) {
                  dismissCommandPicker();
                } else if (mCommandPaletteController != null
                    && mCommandPaletteController.isExpanded()) {
                  mCommandPaletteController.collapse();
                } else {
                  setEnabled(false);
                  getOnBackPressedDispatcher().onBackPressed();
                }
              }
            });

    if (DEBUG.isOn()) {
      if (getResources().getString(R.string.namespace).length() == 0
          || getResources().getString(R.string.nativeDataDir).length() == 0
          || getResources().getString(R.string.libraryName).length() == 0
          || getResources().getString(R.string.defaultsFile).length() == 0)
        throw new RuntimeException("missing config vars");
      if (getResources().getBoolean(R.bool.hearseAvailable)) {
        if (getResources().getString(R.string.hearseClientName).length() == 0
            || getResources().getString(R.string.hearseNethackVersion).length() == 0
            || getResources().getString(R.string.hearseRoles).length() == 0)
          throw new RuntimeException("missing config vars");
      }
    }
    // turn off the window's title bar
    supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
    setDefaultKeyMode(DEFAULT_KEYS_DISABLE);
    // takeKeyEvents(true);

    setContentView(R.layout.mainwindow);
    mFixedTabletUi = com.tbd.forkfront.ui.FixedTabletUi.install(this);

    // Initialization of controllers
    mSecondaryDisplayController = new SecondaryDisplayController(this);
    mCommandPaletteController = new CommandPaletteController(this);
    mDrawerMenuController = new DrawerMenuController(this);

    // Apply window insets to avoid system bars cutting off UI elements
    View rootView = findViewById(R.id.base_frame);
    if (rootView != null) {
      ViewCompat.setOnApplyWindowInsetsListener(
          rootView,
          (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());

            // Combine system bars and display cutout insets
            int topInset = Math.max(systemBars.top, displayCutout.top);
            int bottomInset = Math.max(systemBars.bottom, displayCutout.bottom);
            int leftInset = Math.max(systemBars.left, displayCutout.left);
            int rightInset = Math.max(systemBars.right, displayCutout.right);

            // Apply top padding to status display area to avoid status bar
            View statusContainer = findViewById(R.id.nh_stat0);
            if (statusContainer != null && statusContainer.getParent() instanceof View) {
              View parentLayout = (View) statusContainer.getParent();
              parentLayout.setPadding(
                  leftInset, topInset, rightInset, parentLayout.getPaddingBottom());
            }

            return WindowInsetsCompat.CONSUMED;
          });
    }

    ensureReadWritePermissions();
  }

  private void goodToGo() {
    // Get or create ViewModel (survives configuration changes)
    mViewModel = new ViewModelProvider(this).get(NetHackViewModel.class);

    ByteDecoder decoder;
    if (getResources().getBoolean(R.bool.useCP437Decoder)) decoder = new CP437();
    else
      decoder =
          new ByteDecoder() {
            @Override
            public char decode(int b) {
              return (char) b;
            }

            @Override
            public String decode(byte[] bytes) {
              return new String(bytes);
            }
          };

    // Initialize ViewModel with Application context (only happens once)
    mViewModel.initialize(getApplication(), decoder);

    // Attach current Activity context
    mViewModel.attachActivity(this);
    if (mFixedTabletUi != null) mFixedTabletUi.attach(mViewModel.getState());

    // Initialize gamepad support
    initGamepad();

    // Set up command palette bottom sheet
    mCommandPaletteController.setup();

    // Get progress UI elements
    View loadingOverlay = findViewById(R.id.loading_overlay);
    LinearProgressIndicator progressBar = findViewById(R.id.asset_progress);
    TextView progressText = findViewById(R.id.progress_text);

    // Start asset loading with progress callback
    UpdateAssets updateAssets =
        new UpdateAssets(
            this,
            onAssetsReady,
            (current, total) -> {
              if (progressBar != null && progressText != null) {
                int percentage = (total > 0) ? (int) ((current * 100L) / total) : 0;
                progressBar.setMax(total);
                progressBar.setProgress(current);
                progressText.setText(percentage + "%");

                if (loadingOverlay != null && loadingOverlay.getVisibility() != View.VISIBLE) {
                  loadingOverlay.setVisibility(View.VISIBLE);
                }
              }
            });
    updateAssets.execute((Void[]) null);
  }

  public void showCommandPicker() {
    if (mCommandPickerFragment != null && mCommandPickerFragment.isAdded()) return;

    mCommandPickerFragment = new CommandPickerFragment();
    mCommandPickerFragment.setOnCommandPickedListener(
        new CommandPickerFragment.OnCommandPickedListener() {
          @Override
          public void onCommandPicked(CmdRegistry.CmdInfo cmd) {
            NH_State state = getState();
            if (state != null) {
              state.getCommands().executeCommand(cmd);
            }
            dismissCommandPicker();
          }

          @Override
          public void onDismissed() {
            dismissCommandPicker();
          }
        });

    getSupportFragmentManager()
        .beginTransaction()
        .add(R.id.window_fragment_host, mCommandPickerFragment, "command_picker")
        .commit();

    if (mUiContextArbiter != null) {
      mUiContextArbiter.push(UiContext.COMMAND_PICKER);
    }
  }

  public void dismissCommandPicker() {
    if (mCommandPickerFragment != null && mCommandPickerFragment.isAdded()) {
      getSupportFragmentManager().beginTransaction().remove(mCommandPickerFragment).commit();
    }
    mCommandPickerFragment = null;
    if (mUiContextArbiter != null) {
      mUiContextArbiter.remove(UiContext.COMMAND_PICKER);
    }
  }

  public void ensureReadWritePermissions() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      goodToGo();
      return;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
          == PackageManager.PERMISSION_DENIED) {
        mPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
      } else {
        goodToGo();
      }
    } else {
      goodToGo();
    }
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    Log.print("onConfigurationChanged");
    // Let AppCompat update the theme/night-mode override on the
    // configuration before widgets query it for their colors.
    super.onConfigurationChanged(newConfig);
    NH_State nhState = getState();
    if (nhState != null) {
      nhState.getWidgets().onConfigurationChanged(newConfig);
    }
  }

  private UpdateAssets.Listener onAssetsReady =
      new UpdateAssets.Listener() {
        @Override
        public void onAssetsReady(File path) {
          // Hide loading overlay
          View loadingOverlay = findViewById(R.id.loading_overlay);
          if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.GONE);
          }

          // Create save directory if it doesn't exist
          File nhSaveDir = new File(path, "save");
          if (!nhSaveDir.exists()) nhSaveDir.mkdir();

          PreferenceManager.setDefaultValues(ForkFront.this, R.xml.preferences, false);

          // Start engine through ViewModel
          mViewModel.startEngine(path.getAbsolutePath());
        }
      };

  @Override
  protected void onStart() {

    Log.print("onStart");
    if (DEBUG.runTrace()) Debug.startMethodTracing("nethack");
    super.onStart();
  }

  @Override
  protected void onResume() {

    Log.print("onResume");
    if (mSecondaryDisplayController != null) mSecondaryDisplayController.onResume();

    // Reattach Activity to ViewModel when resuming
    if (mViewModel != null) {
      mViewModel.attachActivity(this);
    }

    if (mGamepadDispatcher != null) {
      mGamepadDispatcher.setSyntheticDispatcher(mSyntheticDispatcher);
    }
    super.onResume();
  }

  @Override
  protected void onPause() {
    if (mSecondaryDisplayController != null) mSecondaryDisplayController.onPause();
    // Do NOT dismiss the presentation here. It should survive across
    // activity transitions (e.g. Settings) so the secondary display
    // remains active and IME can be routed to it.

    // Reset chord tracker to clear any stuck modifier state
    if (mGamepadDispatcher != null) {
      mGamepadDispatcher.resetTracker();
      mGamepadDispatcher.setSyntheticDispatcher(null);
    }

    // Detach Activity from ViewModel when pausing
    if (mViewModel != null) {
      mViewModel.detachActivity();
    }
    super.onPause();
  }

  @Override
  protected void onStop() {
    Log.print("onStop");
    // Do NOT dismiss the presentation here. It should survive across
    // activity transitions (e.g. Settings) so the secondary display
    // remains active and IME can be routed to it.
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    Log.print("onDestroy()");

    if (mGamepadDeviceWatcher != null) mGamepadDeviceWatcher.unregister();
    if (mGamepadDispatcher != null) mGamepadDispatcher.destroy();
    if (mSecondaryDisplayController != null) mSecondaryDisplayController.onDestroy();

    if (mFixedTabletUi != null) mFixedTabletUi.detach();

    // ViewModel's onCleared() will handle saveAndQuit() when Activity is truly finished
    // (not just being recreated for configuration change)
    super.onDestroy();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {

    Log.print("onCreateOptionsMenu");
    menu.add(0, 1, 0, "Settings");

    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {

    Log.print(String.format(Locale.ROOT, "onOptionsItemSelected(item=%d)", item.getItemId()));
    if (item.getItemId() == 1) {
      launchSettings();
      return true;
    }

    return false;
  }

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {

    super.onCreateContextMenu(menu, v, menuInfo);
  }

  public void onContextMenuClosed(Menu menu) {
    super.onContextMenuClosed(menu);
    NH_State state = getState();
    if (state != null) state.getSysUi().applyImmersiveFlags();
  }

  @Override
  public boolean onContextItemSelected(MenuItem item) {

    return super.onContextItemSelected(item);
  }

  /** Launch the settings activity. Public method for use by NH_State and other components. */
  public void launchSettings() {
    if (mUiContextArbiter != null) {
      mUiContextArbiter.pushUnique(UiContext.SETTINGS_OPEN);
    }
    Intent prefsActivity = new Intent(getBaseContext(), Settings.class);
    mSettingsLauncher.launch(prefsActivity);
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    Log.print("onSaveInstanceState(Bundle outState)");
    NH_State state = getState();
    if (state != null) state.getCommands().saveState();
  }

  // Native Android/Samsung keyboard bridge for NetHack.
  private View mGameKeyboardView;

  /** Check whether the native gameplay keyboard is currently visible. */
  public boolean isGameKeyboardVisible() {
    if (mGameKeyboardView == null || !mGameKeyboardView.hasFocus()) return false;

    WindowInsetsCompat insets =
        ViewCompat.getRootWindowInsets(getWindow().getDecorView());

    return insets != null
        && insets.isVisible(WindowInsetsCompat.Type.ime());
  }
  public void showGameKeyboard() {
    // The fixed keyboard is always visible; never open the floating Samsung IME here.
    if (mFixedTabletUi != null) return;
    if (mGameKeyboardView == null) {
      mGameKeyboardView = new View(this) {
        @Override
        public boolean onCheckIsTextEditor() {
          return true;
        }

        @Override
        public android.view.inputmethod.InputConnection onCreateInputConnection(
            android.view.inputmethod.EditorInfo attrs) {

          attrs.inputType =
              android.text.InputType.TYPE_CLASS_TEXT
                  | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                  | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;

          attrs.imeOptions =
              android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
                  | android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN;

          return new android.view.inputmethod.BaseInputConnection(this, false) {
            @Override
            public boolean setComposingText(CharSequence text, int cursorPosition) {
              android.util.Log.i("NH_IME_DEBUG", "COMPOSING: " + text);
              return super.setComposingText(text, cursorPosition);
            }
            @Override
            public boolean commitText(CharSequence text, int cursorPosition) {
              android.util.Log.i("NH_IME_DEBUG", "COMMITTED: " + text);
              NH_State state = getState();
              if (state == null || text == null) return false;

              for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                state.getCommands().sendKeyCmd(ch == '\n' ? '\r' : ch);
              }
              return true;
            }

            @Override
            public boolean deleteSurroundingText(int before, int after) {
              NH_State state = getState();
              if (state != null && before > 0) {
                state.getCommands().sendKeyCmd('\b');
                return true;
              }
              return super.deleteSurroundingText(before, after);
            }

            @Override
            public boolean sendKeyEvent(KeyEvent event) {
              if (event.getAction() == KeyEvent.ACTION_DOWN) {
                NH_State state = getState();
                if (state != null) {
                  if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    state.getCommands().sendKeyCmd('\r');
                    return true;
                  }
                  if (event.getKeyCode() == KeyEvent.KEYCODE_DEL) {
                    state.getCommands().sendKeyCmd('\b');
                    return true;
                  }
                }
              }
              return super.sendKeyEvent(event);
            }
          };
        }
      };

      mGameKeyboardView.setFocusable(true);
      mGameKeyboardView.setFocusableInTouchMode(true);

      android.view.ViewGroup decor =
          (android.view.ViewGroup) getWindow().getDecorView();

      decor.addView(
          mGameKeyboardView,
          new android.view.ViewGroup.LayoutParams(1, 1));
    }

    // Allow the navigation drawer to close before opening the keyboard.
    mGameKeyboardView.postDelayed(() -> {
      mGameKeyboardView.requestFocus();

      android.view.inputmethod.InputMethodManager imm =
          (android.view.inputmethod.InputMethodManager)
              getSystemService(INPUT_METHOD_SERVICE);

      if (imm != null) {
        imm.showSoftInput(
            mGameKeyboardView,
            android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
      }
    }, 300);
  }
  private void handleUiAction(UiActionId id) {
    switch (id) {
      case OPEN_DRAWER:
        if (mDrawerMenuController != null && mDrawerMenuController.getDrawerLayout() != null)
          mDrawerMenuController.getDrawerLayout().openDrawer(androidx.core.view.GravityCompat.END);
        break;
      case OPEN_SETTINGS:
        launchSettings();
        break;
      case OPEN_COMMAND_PALETTE:
        if (mCommandPaletteController != null) mCommandPaletteController.expand(null);
        break;
      case OPEN_COMMAND_PICKER:
        showCommandPicker();
        break;
      case TOGGLE_KEYBOARD:
        android.view.inputmethod.InputMethodManager imm =
            (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View focus = getCurrentFocus();
        if (imm != null && focus != null) {
          imm.toggleSoftInput(0, 0);
        }
        break;
      case ZOOM_IN:
        NH_State sIn = getState();
        if (sIn != null) sIn.getMapInput().zoomIn();
        break;
      case ZOOM_OUT:
        NH_State sOut = getState();
        if (sOut != null) sOut.getMapInput().zoomOut();
        break;
      case TOGGLE_MAP_LOCK:
        break;
      case RECENTER_MAP:
        NH_State sRecenter = getState();
        if (sRecenter != null) sRecenter.getMapInput().recenterMap();
        break;
      case RESEND_LAST_CMD:
        break;
    }
  }

  private void initGamepad() {
    NH_State state = mViewModel != null ? mViewModel.getState() : null;
    if (state == null) {
      android.util.Log.w("ForkFront", "initGamepad: NH_State not available yet");
      return;
    }

    mUiContextArbiter =
        new UiContextArbiter(
            new UiContextArbiter.ContextOverrideQuery() {
              @Override
              public boolean expectsDirection() {
                NH_State s = getState();
                return s != null && s.getCommands().expectsDirection();
              }

              @Override
              public boolean isMouseLocked() {
                NH_State s = getState();
                return s != null && s.getCommands().isMouseLocked();
              }
            });

    final NH_State finalState = state;
    GamepadDispatcher.NH_StateRef stateRef =
        new GamepadDispatcher.NH_StateRef() {
          @Override
          public boolean sendKeyCmd(int nhKey) {
            return finalState.getCommands().sendKeyCmd(nhKey);
          }

          @Override
          public boolean sendDirKeyCmd(int nhKey) {
            return finalState.getCommands().sendDirKeyCmd(nhKey);
          }

          @Override
          public void sendStringCmd(String str) {
            finalState.getCommands().sendStringCmd(str);
          }

          @Override
          public boolean expectsDirection() {
            return finalState.getCommands().expectsDirection();
          }

          @Override
          public boolean isMouseLocked() {
            return finalState.getCommands().isMouseLocked();
          }
        };

    mGamepadDispatcher =
        new GamepadDispatcher(
            getApplicationContext(), stateRef, mUiContextArbiter, this::handleUiAction);

    state.getGamepadContext().setArbiter(mUiContextArbiter);

    // Register the game UiCapture so in-game windows get routed correctly
    mGamepadDispatcher.enterUiCapture(state.getRouter().asUiCapture());

    mGamepadDeviceWatcher = new GamepadDeviceWatcher(this, mGamepadDispatcher);
    mGamepadDeviceWatcher.register();

    if (mDrawerMenuController != null) {
      mDrawerMenuController.setup(mUiContextArbiter, mGamepadDispatcher);
    }
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    // Handle back key long press manually
    if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
      boolean overlaysOpen =
          (mCommandPickerFragment != null && mCommandPickerFragment.isAdded())
              || (mCommandPaletteController != null && mCommandPaletteController.isExpanded());
      if (overlaysOpen) {
        mBackTracking = false;
        return super.dispatchKeyEvent(event);
      }
      if (event.getAction() == KeyEvent.ACTION_DOWN) {
        if (event.getRepeatCount() == 0) {
          mBackTracking = true;
        } else if (mBackTracking && event.isLongPress()) {
          launchSettings();
          mBackTracking = false;
        }
      } else if (event.getAction() == KeyEvent.ACTION_UP) {
        if (mBackTracking && !event.isCanceled()) {
          EnumSet<Modifier> modifiers = Input.modifiersFromKeyEvent(event);
          handleKeyDown(
              event.getKeyCode(), event.getUnicodeChar(), event.getRepeatCount(), modifiers);
        }
        mBackTracking = false;
      }
      return true;
    }
    mBackTracking = false;

    // Gamepad pre-pass: intercept before the existing key-down chain.
    // Track whether this was a gamepad event and its context so we can gate
    // handleKeyDown below (gamepad events in non-gameplay contexts must not
    // reach the game engine even when the dispatcher returns false).
    boolean wasGamepadEvent = false;
    UiContext gamepadCtx = UiContext.GAMEPLAY;
    if (mGamepadDispatcher != null && mGamepadDispatcher.isGamepadEvent(event)) {
      wasGamepadEvent = true;
      gamepadCtx = mUiContextArbiter.current();
      if (mGamepadDispatcher.handleKeyEvent(event, gamepadCtx)) {
        return true;
      }
    }

    // When an EditText has focus (e.g. GetLine, command palette search),
    // text-editing keys must reach Android's focus system so the EditText can
    // handle backspace, delete, and cursor navigation. Without this bypass the
    // game engine consumes these keys and the EditText never sees them.
    View focused = getCurrentFocus();
    if (focused instanceof android.widget.EditText) {
      switch (event.getKeyCode()) {
        case KeyEvent.KEYCODE_DEL:
        case KeyEvent.KEYCODE_FORWARD_DEL:
        case KeyEvent.KEYCODE_DPAD_UP:
        case KeyEvent.KEYCODE_DPAD_DOWN:
        case KeyEvent.KEYCODE_DPAD_LEFT:
        case KeyEvent.KEYCODE_DPAD_RIGHT:
        case KeyEvent.KEYCODE_DPAD_CENTER:
          return super.dispatchKeyEvent(event);
      }
    }

    // In non-gameplay contexts (drawer, settings) and for synthetic events injected
    // by the dispatcher's baseline fallback, skip the game key handler so D-pad
    // navigation and synthetic keys go to Android's focus system instead of NetHack.
    boolean isSynthetic =
        (event.getSource() & GamepadDispatcher.SOURCE_SYNTHETIC)
            == GamepadDispatcher.SOURCE_SYNTHETIC;
    boolean shouldHandleKey =
        !isSynthetic
            && (!wasGamepadEvent
                || gamepadCtx == UiContext.GAMEPLAY
                || gamepadCtx == UiContext.DIRECTION_PROMPT);

    if (event.getAction() == KeyEvent.ACTION_DOWN && shouldHandleKey) {
      EnumSet<Modifier> modifiers = Input.modifiersFromKeyEvent(event);
      if (handleKeyDown(
          event.getKeyCode(), event.getUnicodeChar(), event.getRepeatCount(), modifiers))
        return true;
    }

    return super.dispatchKeyEvent(event);
  }

  public boolean isFixedTabletLayout() {
    return mFixedTabletUi != null;
  }

  public int getFixedTabletMenuHostId() {
    return mFixedTabletUi != null ? mFixedTabletUi.getMenuHostId() : View.NO_ID;
  }

  public void setFixedTabletMenuVisible(boolean visible) {
    if (mFixedTabletUi != null) mFixedTabletUi.showMenu(visible);
  }

  public NH_State getState() {
    return mViewModel != null ? mViewModel.getState() : null;
  }

  public boolean handleKeyDown(
      int keyCode, int unicodeChar, int repeatCount, EnumSet<Modifier> modifiers) {
    NH_State state = getState();
    if (state == null) return false;

    int fixedCode = Input.keyCodeToAction(keyCode, this);

    if (fixedCode == KeyEvent.KEYCODE_VOLUME_DOWN || fixedCode == KeyEvent.KEYCODE_VOLUME_UP)
      return false;

    char ch = (char) unicodeChar;

    int nhKey = Input.nhKeyFromKeyCode(fixedCode, ch, modifiers, state.getCommands().isNumPadOn());

    if (state.getRouter().handleKeyDown(ch, nhKey, fixedCode, modifiers, repeatCount)) return true;

    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
      // Prevent default system sound from playing on remapped volume keys
      return true;
    }
    return false;
  }

  @Override
  public boolean onGenericMotionEvent(MotionEvent event) {
    if (mGamepadDispatcher != null && mGamepadDispatcher.isGamepadEvent(event)) {
      UiContext ctx = mUiContextArbiter.current();
      if (mGamepadDispatcher.handleGenericMotion(event, ctx)) return true;
    }
    return super.onGenericMotionEvent(event);
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    int fixedCode = Input.keyCodeToAction(keyCode, this);

    if (fixedCode == KeyEvent.KEYCODE_VOLUME_DOWN || fixedCode == KeyEvent.KEYCODE_VOLUME_UP)
      return false;

    NH_State state = getState();
    if (state != null && state.getMapInput().handleKeyUp(fixedCode)) return true;

    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
      // Prevent default system sound from playing on remapped volume keys
      return true;
    }
    return super.onKeyUp(keyCode, event);
  }

  @Override
  public void setDrawerEditMode(boolean editMode) {
    if (mDrawerMenuController != null) {
      mDrawerMenuController.setDrawerEditMode(editMode);
    }
  }

  @Override
  public void expandCommandPalette(CmdRegistry.OnCommandListener listener) {
    if (mCommandPaletteController != null) {
      mCommandPaletteController.expand(listener);
    }
  }
}
