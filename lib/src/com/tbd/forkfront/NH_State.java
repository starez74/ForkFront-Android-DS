package com.tbd.forkfront;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import com.tbd.forkfront.context.CmdRegistry;
import com.tbd.forkfront.context.ContextualActionsEngine;
import com.tbd.forkfront.dialog.NH_CharacterPicker;
import com.tbd.forkfront.dialog.NH_GetLine;
import com.tbd.forkfront.dialog.NH_Question;
import com.tbd.forkfront.engine.ByteDecoder;
import com.tbd.forkfront.engine.EngineCommands;
import com.tbd.forkfront.engine.NH_Handler;
import com.tbd.forkfront.engine.NetHackIO;
import com.tbd.forkfront.engine.NetHackViewModel;
import com.tbd.forkfront.engine.NhEngineCallbacks;
import com.tbd.forkfront.gamepad.GameInputRouter;
import com.tbd.forkfront.gamepad.GamepadContextBridge;
import com.tbd.forkfront.hearse.Hearse;
import com.tbd.forkfront.settings.PreferencesCoordinator;
import com.tbd.forkfront.settings.WidgetLayout;
import com.tbd.forkfront.ui.ActivityScope;
import com.tbd.forkfront.ui.SoundPlayer;
import com.tbd.forkfront.ui.SystemUiController;
import com.tbd.forkfront.widgets.WidgetLayoutController;
import com.tbd.forkfront.window.WindowRegistry;
import com.tbd.forkfront.window.map.MapInputCoordinator;
import com.tbd.forkfront.window.map.NHW_Map;
import com.tbd.forkfront.window.map.Tileset;
import com.tbd.forkfront.window.message.NHW_Message;
import com.tbd.forkfront.window.message.NHW_Status;

/**
 * NH_State serves as the composition root for the NetHack Android port. It manages the lifecycle
 * and coordination of specialized collaborators. Every method on this class runs on the Android
 * main UI thread.
 */
public class NH_State implements ForkFrontHost {
  private final ActivityScope mScope;
  private NetHackIO mIO;
  private ByteDecoder mDecoder;
  private NHW_Message mMessage;
  private NHW_Status mStatus;
  private NHW_Map mMap;
  private NH_GetLine mGetLine;
  private NH_CharacterPicker mCharacterPicker;
  private NH_Question mQuestion;
  private final WindowRegistry mWindows = new WindowRegistry();
  private final MapInputCoordinator mMapInput;
  private final EngineCommands mCommands;
  private Tileset mTileset;
  private final WidgetLayoutController mWidgets;
  private Hearse mHearse;
  private SoundPlayer mSoundPlayer;
  private final ContextualActionsEngine mContextActions;
  private final SystemUiController mSysUi;
  private final PreferencesCoordinator mPrefs;
  private final GamepadContextBridge mCtxBridge;
  private final GameInputRouter mRouter;
  private final NhEngineCallbacks mCallbacks;

  public NH_State(Application app, ByteDecoder decoder, NetHackIO io) {
    mScope = new ActivityScope(app, null);
    mIO = io;
    mDecoder = decoder;
    mTileset = new Tileset(app);

    mMapInput = new MapInputCoordinator(mScope, () -> mMap);
    mCommands = new EngineCommands(mIO, mMapInput);
    mContextActions = new ContextualActionsEngine(() -> mMap);
    mSysUi = new SystemUiController(mScope);
    mPrefs =
        new PreferencesCoordinator(
            mScope, mWindows, mTileset, () -> mMap, () -> mStatus, () -> mMessage, mSysUi, this);

    mWidgets =
        new WidgetLayoutController(
            mScope,
            () -> mStatus,
            () -> mMessage,
            () -> mMap,
            mTileset,
            mCommands,
            mMapInput,
            mContextActions,
            this);
    mWidgets.setNHState(this);

    mCtxBridge = new GamepadContextBridge();
    mGetLine = new NH_GetLine(mIO, this);
    mCharacterPicker = new NH_CharacterPicker(mIO, this);
    mQuestion = new NH_Question(mIO, this);
    mRouter =
        new GameInputRouter(
            mWindows,
            () -> mMessage,
            () -> mMap,
            mGetLine,
            mCharacterPicker,
            mQuestion,
            mCommands,
            mCtxBridge);
    mSoundPlayer = new SoundPlayer();

    mCallbacks =
        new NhEngineCallbacks(
            app,
            mWindows,
            mScope,
            mContextActions,
            mMapInput,
            mCommands,
            () -> mMessage,
            () -> mStatus,
            () -> mMap,
            mGetLine,
            mCharacterPicker,
            mQuestion,
            mTileset,
            mIO,
            mSoundPlayer,
            mWidgets,
            mPrefs);
  }

  public void setContext(AppCompatActivity activity) {
    mScope.setActivity(activity);

    if (activity != null) {
      if (mMessage == null) {
        mMessage = new NHW_Message(activity, mIO);
      }
      if (mStatus == null) {
        mStatus = new NHW_Status(activity, mIO);
        mStatus.show(false);
      }
      if (mMap == null) {
        mMap = new NHW_Map(activity, mTileset, mStatus, mCommands, mMapInput, mDecoder);
      }

      mWidgets.attachPrimary((WidgetLayout) activity.findViewById(R.id.widgetLayout1));
    } else {
      mWidgets.setPrimaryWidgetLayout(null);
    }

    mWindows.forEach(w -> w.setContext(activity));
    mGetLine.setContext(activity);
    mCharacterPicker.setContext(activity);
    mQuestion.setContext(activity);
    if (mMessage != null) mMessage.setContext(activity);
    if (mStatus != null) mStatus.setContext(activity);
    if (mMap != null) mMap.setContext(activity);
    mTileset.setContext(activity);
    mWidgets.onContextAttached(activity);
  }

  public void startNetHack(String path) {
    if (mScope.getActivity() == null || mMap == null) {
      throw new IllegalStateException(
          "setContext() must be called with an Activity before starting NetHack");
    }

    mIO.start(path);
    mPrefs.apply();
    mMap.loadZoomLevel();

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mScope.getApp());
    mHearse = new Hearse(mScope.getApp(), prefs, path);
  }

  public void onConfigurationChanged(Configuration newConfig) {
    mWidgets.onConfigurationChanged(newConfig);
    mContextActions.recompute();
    if (mMap != null) mMap.onConfigurationChanged(newConfig);
  }

  @Override
  public void expandCommandPalette(CmdRegistry.OnCommandListener onSelected) {
    if (mScope.getActivity() instanceof ForkFrontHost) {
      ((ForkFrontHost) mScope.getActivity()).expandCommandPalette(onSelected);
    }
  }

  @Override
  public void setDrawerEditMode(boolean enabled) {
    if (mScope.getActivity() instanceof ForkFrontHost) {
      ((ForkFrontHost) mScope.getActivity()).setDrawerEditMode(enabled);
    }
  }

  @Override
  public void launchSettings() {
    if (mScope.getActivity() instanceof ForkFrontHost) {
      ((ForkFrontHost) mScope.getActivity()).launchSettings();
    }
  }

  public ActivityScope getScope() {
    return mScope;
  }

  public EngineCommands getCommands() {
    return mCommands;
  }

  public MapInputCoordinator getMapInput() {
    return mMapInput;
  }

  public WidgetLayoutController getWidgets() {
    return mWidgets;
  }

  public GameInputRouter getRouter() {
    return mRouter;
  }

  /** Route virtual tablet key presses to the current native text-entry field. */
  public boolean handleFixedTabletTextKey(char value, int keyCode) {
    return mGetLine.handleFixedTabletKey(value, keyCode);
  }

  public GamepadContextBridge getGamepadContext() {
    return mCtxBridge;
  }

  public ContextualActionsEngine getContextActions() {
    return mContextActions;
  }

  public SystemUiController getSysUi() {
    return mSysUi;
  }

  public PreferencesCoordinator getPrefs() {
    return mPrefs;
  }

  public WindowRegistry getWindows() {
    return mWindows;
  }

  public NetHackIO getIO() {
    return mIO;
  }

  public Tileset getTileset() {
    return mTileset;
  }

  public NHW_Map getMapWindow() {
    return mMap;
  }

  public NHW_Status getStatusWindow() {
    return mStatus;
  }

  public NHW_Message getMessageWindow() {
    return mMessage;
  }

  /**
   * Get the NH_Handler for this state. Used by NetHackIO to receive JNI callbacks from native
   * engine.
   */
  public NH_Handler getNhHandler() {
    return mCallbacks;
  }

  public void setViewModel(NetHackViewModel viewModel) {
    mScope.setViewModel(viewModel);
  }
}
