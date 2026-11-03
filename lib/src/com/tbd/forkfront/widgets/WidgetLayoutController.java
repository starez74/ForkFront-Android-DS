package com.tbd.forkfront.widgets;

import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tbd.forkfront.ForkFrontHost;
import com.tbd.forkfront.R;
import com.tbd.forkfront.context.CmdRegistry;
import com.tbd.forkfront.context.ContextualActionsEngine;
import com.tbd.forkfront.engine.EngineCommands;
import com.tbd.forkfront.input.DirectionalPadView;
import com.tbd.forkfront.settings.WidgetLayout;
import com.tbd.forkfront.settings.WidgetPropertiesFragment;
import com.tbd.forkfront.ui.ActivityScope;
import com.tbd.forkfront.ui.ThemeUtils;
import com.tbd.forkfront.window.map.MapInputCoordinator;
import com.tbd.forkfront.window.map.NHW_Map;
import com.tbd.forkfront.window.map.Tileset;
import com.tbd.forkfront.window.message.NHW_Message;
import com.tbd.forkfront.window.message.NHW_Status;
import java.util.function.Supplier;

public class WidgetLayoutController {
  private final ActivityScope mScope;
  private final Supplier<NHW_Status> mStatus;
  private final Supplier<NHW_Message> mMessage;
  private final Supplier<NHW_Map> mMap;
  private final Tileset mTileset;
  private final EngineCommands mCommands;
  private final MapInputCoordinator mMapInput;
  private final ContextualActionsEngine mContextActions;
  private final ForkFrontHost mHost;

  private WidgetLayout mPrimaryWidgetLayout;
  private WidgetLayout mSecondaryWidgetLayout;
  private com.tbd.forkfront.NH_State mNhState;

  public WidgetLayoutController(
      ActivityScope scope,
      Supplier<NHW_Status> status,
      Supplier<NHW_Message> message,
      Supplier<NHW_Map> map,
      Tileset tileset,
      EngineCommands commands,
      MapInputCoordinator mapInput,
      ContextualActionsEngine contextActions,
      ForkFrontHost host) {
    mScope = scope;
    mStatus = status;
    mMessage = message;
    mMap = map;
    mTileset = tileset;
    mCommands = commands;
    mMapInput = mapInput;
    mContextActions = contextActions;
    mHost = host;
  }

  public void setNHState(com.tbd.forkfront.NH_State nhState) {
    mNhState = nhState;
    if (mPrimaryWidgetLayout != null) {
      mPrimaryWidgetLayout.setDependencies(this, mCommands, mMapInput);
    }
    if (mSecondaryWidgetLayout != null) {
      mSecondaryWidgetLayout.setDependencies(this, mCommands, mMapInput);
    }
  }

  public void attachPrimary(WidgetLayout layout) {
    mPrimaryWidgetLayout = layout;
    if (mPrimaryWidgetLayout != null) {
      mPrimaryWidgetLayout.setDependencies(this, mCommands, mMapInput);
      // Fixed tablet controls use real layout regions, not movable saved widgets.
      if (isFixedTablet()) {
        mPrimaryWidgetLayout.setEditMode(false);
      } else {
        mPrimaryWidgetLayout.loadLayout();
        mPrimaryWidgetLayout.setEditMode(isEditMode());
      }
    }
  }

  public void attachSecondaryWidgetLayout(WidgetLayout layout) {
    mSecondaryWidgetLayout = layout;
    if (mSecondaryWidgetLayout != null) {
      mSecondaryWidgetLayout.setDependencies(this, mCommands, mMapInput);
      if (isEditMode()) {
        mSecondaryWidgetLayout.enterEditMode();
        mSecondaryWidgetLayout.setEditMode(true);
        mSecondaryWidgetLayout.loadLayout();
      } else {
        mSecondaryWidgetLayout.loadLayout();
      }
    }
  }

  public void detachSecondaryWidgetLayout() {
    mSecondaryWidgetLayout = null;
  }

  public void onConfigurationChanged(android.content.res.Configuration newConfig) {
    if (mPrimaryWidgetLayout != null && !isFixedTablet()) {
      mPrimaryWidgetLayout.reloadForNewOrientation(newConfig);
    }
    if (mSecondaryWidgetLayout != null) {
      mSecondaryWidgetLayout.reloadForNewOrientation(newConfig);
    }
  }

  public void onContextAttached(AppCompatActivity activity) {
    if (mPrimaryWidgetLayout != null) {
      mPrimaryWidgetLayout.setDependencies(this, mCommands, mMapInput);
    }
    if (mSecondaryWidgetLayout != null) {
      mSecondaryWidgetLayout.setDependencies(this, mCommands, mMapInput);
    }
  }

  public void setPrimaryWidgetLayout(WidgetLayout layout) {
    mPrimaryWidgetLayout = layout;
  }

  public void setSecondaryWidgetLayout(WidgetLayout layout) {
    mSecondaryWidgetLayout = layout;
  }

  public WidgetLayout getPrimaryWidgetLayout() {
    return mPrimaryWidgetLayout;
  }

  public WidgetLayout getSecondaryWidgetLayout() {
    return mSecondaryWidgetLayout;
  }

  public NHW_Status getStatusWindow() {
    return mStatus.get();
  }

  public NHW_Message getMessageWindow() {
    return mMessage.get();
  }

  public NHW_Map getMapWindow() {
    return mMap.get();
  }

  public Tileset getTileset() {
    return mTileset;
  }

  public ContextualActionsEngine getContextActions() {
    return mContextActions;
  }

  public String getDeviceKey() {
    return com.tbd.forkfront.ui.DeviceProfile.detect(mScope.getApp());
  }

  public static CmdRegistry.Category parseCategory(String category) {
    if (category == null || category.isEmpty()) {
      return null;
    }
    try {
      return CmdRegistry.Category.valueOf(category);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private boolean isFixedTablet() {
    return mScope.getActivity() instanceof com.tbd.forkfront.ForkFront
        && ((com.tbd.forkfront.ForkFront) mScope.getActivity()).isFixedTabletLayout();
  }

  public static ControlWidget findDpadWidget(WidgetLayout layout) {
    if (layout == null) return null;
    for (int i = 0; i < layout.getChildCount(); i++) {
      View child = layout.getChildAt(i);
      if (child instanceof ControlWidget) {
        ControlWidget cw = (ControlWidget) child;
        if ("dpad".equals(cw.getWidgetData().type)) {
          return cw;
        }
      }
    }
    return null;
  }

  public void applyWidgetOpacity(ControlWidget widget, int opacity) {
    widget.applyOpacity();
  }

  public void setLayoutEditMode(WidgetLayout layout, boolean enabled, boolean showEditBar) {
    if (layout == null || (layout == mPrimaryWidgetLayout && isFixedTablet())) return;
    if (enabled) {
      layout.enterEditMode();
      layout.loadLayout();
    }
    layout.setEditMode(enabled);

    if (showEditBar && layout.getParent() instanceof View) {
      View bar = ((View) layout.getParent()).findViewById(R.id.secondary_edit_bar);
      if (bar != null) {
        bar.setVisibility(enabled ? View.VISIBLE : View.GONE);
      }
    }
  }

  public void addPaletteWidget(AppCompatActivity activity) {
    addPaletteWidgetForLayout(activity, mPrimaryWidgetLayout);
  }

  public void addPaletteWidgetForLayout(AppCompatActivity activity, final WidgetLayout layout) {
    float density = activity.getResources().getDisplayMetrics().density;

    ControlWidget.WidgetData data = new ControlWidget.WidgetData();
    data.type = "palette";
    data.label = "Commands";
    data.x = 100;
    data.y = 100;
    data.w = (int) (100 * density);
    data.h = (int) (60 * density);

    MaterialButton btn = ThemeUtils.createButtonText(activity);
    btn.setText("Commands");
    btn.setIconResource(android.R.drawable.ic_menu_search);
    btn.setOnClickListener(
        v -> {
          if (!layout.isEditMode()) {
            showCommandPaletteForLayout(activity, layout);
          }
        });

    ControlWidget widget = new ControlWidget(activity, btn, "palette");
    widget.setWidgetData(data);
    layout.addWidget(widget);
  }

  public void addDPadWidget(AppCompatActivity activity) {
    addDPadWidgetForLayout(activity, mPrimaryWidgetLayout);
  }

  public void addDPadWidgetForLayout(AppCompatActivity activity, final WidgetLayout layout) {
    float density = activity.getResources().getDisplayMetrics().density;
    int dpadSize = (int) (180 * density);

    ControlWidget.WidgetData dpadData = new ControlWidget.WidgetData();
    dpadData.type = "dpad";
    dpadData.x = 100;
    dpadData.y = 100;
    dpadData.w = dpadSize;
    dpadData.h = dpadSize;

    DirectionalPadView dpadView = new DirectionalPadView(activity);
    dpadView.setOnDirectionListener(cmd -> mCommands.sendDirKeyCmd(cmd));
    ControlWidget dpadWidget = new ControlWidget(activity, dpadView, "dpad");
    dpadWidget.setWidgetData(dpadData);
    layout.addWidget(dpadWidget);
  }

  public void addStatusWidget(AppCompatActivity activity) {
    addStatusWidgetForLayout(activity, mPrimaryWidgetLayout);
  }

  public void addStatusWidgetForLayout(AppCompatActivity activity, final WidgetLayout layout) {
    float density = activity.getResources().getDisplayMetrics().density;

    ControlWidget.WidgetData statusData = new ControlWidget.WidgetData();
    statusData.type = "status";
    statusData.x = 100;
    statusData.y = 100;
    statusData.w = (int) (400 * density);
    statusData.h = (int) (60 * density);

    StatusWidget statusWidget = new StatusWidget(activity, mStatus.get());
    statusWidget.setWidgetData(statusData);
    layout.addWidget(statusWidget);
  }

  public void addMessageWidget(AppCompatActivity activity) {
    addMessageWidgetForLayout(activity, mPrimaryWidgetLayout);
  }

  public void addMessageWidgetForLayout(AppCompatActivity activity, final WidgetLayout layout) {
    float density = activity.getResources().getDisplayMetrics().density;

    ControlWidget.WidgetData messageData = new ControlWidget.WidgetData();
    messageData.type = "message";
    messageData.x = 100;
    messageData.y = 200;
    messageData.w = (int) (400 * density);
    messageData.h = (int) (80 * density);

    MessageWidget messageWidget = new MessageWidget(activity, mMessage.get());
    messageWidget.setWidgetData(messageData);
    layout.addWidget(messageWidget);
  }

  public void addMinimapWidget(AppCompatActivity activity) {
    addMinimapWidgetForLayout(activity, mPrimaryWidgetLayout);
  }

  public void addMinimapWidgetForLayout(AppCompatActivity activity, final WidgetLayout layout) {
    float density = activity.getResources().getDisplayMetrics().density;

    ControlWidget.WidgetData minimapData = new ControlWidget.WidgetData();
    minimapData.type = "minimap";
    minimapData.x = 100;
    minimapData.y = 100;
    minimapData.w = (int) (240 * density);
    minimapData.h = (int) (80 * density);

    MinimapWidget minimapWidget = new MinimapWidget(activity, mMap.get(), mTileset);
    minimapWidget.setWidgetData(minimapData);
    layout.addWidget(minimapWidget);
  }

  public void addCommandPaletteWidget(AppCompatActivity activity) {
    addCommandPaletteWidgetForLayout(activity, mPrimaryWidgetLayout);
  }

  public void addCommandPaletteWidgetForLayout(
      AppCompatActivity activity, final WidgetLayout layout) {
    float density = activity.getResources().getDisplayMetrics().density;

    ControlWidget.WidgetData paletteData = new ControlWidget.WidgetData();
    paletteData.type = "command_palette";
    paletteData.x = 100;
    paletteData.y = 100;
    paletteData.w = (int) (300 * density);
    paletteData.h = (int) (200 * density);
    paletteData.rows = 3;
    paletteData.columns = 3;
    paletteData.category = null;
    paletteData.horizontal = false;

    CommandPaletteWidget paletteWidget =
        new CommandPaletteWidget(
            activity,
            mContextActions,
            mCommands,
            paletteData.rows,
            paletteData.columns,
            null,
            paletteData.horizontal,
            paletteData.contextualOnly,
            paletteData.pinnedCommands);
    paletteWidget.setWidgetData(paletteData);
    layout.addWidget(paletteWidget);
  }

  public void showAddWidgetDialog(AppCompatActivity activity, WidgetLayout layout) {
    showAddWidgetDialogForLayout(activity, layout);
  }

  public void showAddWidgetDialogForLayout(AppCompatActivity activity, final WidgetLayout layout) {
    String[] options = {
      "Directional Pad",
      "Custom Action Button",
      "Command List",
      "Command Palette",
      "Status Window",
      "Message Window",
      "Minimap"
    };
    new MaterialAlertDialogBuilder(activity)
        .setTitle(R.string.add_widget)
        .setItems(
            options,
            (dialog, which) -> {
              switch (which) {
                case 0:
                  addDPadWidgetForLayout(activity, layout);
                  break;
                case 1:
                  showCommandPaletteForLayout(activity, layout);
                  break;
                case 2:
                  addPaletteWidgetForLayout(activity, layout);
                  break;
                case 3:
                  addCommandPaletteWidgetForLayout(activity, layout);
                  break;
                case 4:
                  addStatusWidgetForLayout(activity, layout);
                  break;
                case 5:
                  addMessageWidgetForLayout(activity, layout);
                  break;
                case 6:
                  addMinimapWidgetForLayout(activity, layout);
                  break;
              }
            })
        .show();
  }

  public void showCommandPaletteForLayout(AppCompatActivity activity, final WidgetLayout layout) {
    mHost.expandCommandPalette(
        cmd -> {
          if (layout.isEditMode()) {
            // Add a new button widget for this command
            ControlWidget.WidgetData data = new ControlWidget.WidgetData();
            data.type = "button";
            data.label = cmd.getDisplayName();
            data.command = cmd.getCommand();
            data.x = 100;
            data.y = 100;
            data.w = (int) (100 * activity.getResources().getDisplayMetrics().density);
            data.h = (int) (60 * activity.getResources().getDisplayMetrics().density);

            MaterialButton btn = ThemeUtils.createButtonText(activity);
            btn.setText(data.label);
            btn.setOnClickListener(v -> mCommands.executeCommand(cmd));

            ControlWidget widget = new ControlWidget(activity, btn, "button");
            widget.setWidgetData(data);
            layout.addWidget(widget);
          } else {
            mCommands.executeCommand(cmd);
          }
        });
  }

  public void wireButtons(final WidgetLayout layout, View root) {
    View btnAdd = root.findViewById(R.id.btn_add_widget);
    if (btnAdd != null)
      btnAdd.setOnClickListener(
          v -> showAddWidgetDialogForLayout((AppCompatActivity) mScope.getActivity(), layout));
    View btnDiscard = root.findViewById(R.id.btn_discard_changes);
    if (btnDiscard != null) btnDiscard.setOnClickListener(v -> discardChangesAndExitEditMode());
    View btnSave = root.findViewById(R.id.btn_save_layout);
    if (btnSave != null) btnSave.setOnClickListener(v -> saveLayoutAndExitEditMode());
  }

  public void saveLayoutAndExitEditMode() {
    if (mPrimaryWidgetLayout != null) {
      mPrimaryWidgetLayout.commitDraftToLayout();
    }
    if (mSecondaryWidgetLayout != null) {
      mSecondaryWidgetLayout.commitDraftToLayout();
    }
    setEditMode(false);
  }

  public void discardChangesAndExitEditMode() {
    if (mPrimaryWidgetLayout != null) {
      mPrimaryWidgetLayout.clearDraft();
      mPrimaryWidgetLayout.loadCommittedLayout();
    }
    if (mSecondaryWidgetLayout != null) {
      mSecondaryWidgetLayout.clearDraft();
      mSecondaryWidgetLayout.loadCommittedLayout();
    }
    setEditMode(false);
  }

  public void setEditMode(boolean enabled) {
    if (enabled && isFixedTablet()) return;
    setLayoutEditMode(mPrimaryWidgetLayout, enabled, false);
    setLayoutEditMode(mSecondaryWidgetLayout, enabled, true);

    mHost.setDrawerEditMode(enabled);
    if (!enabled) {
      PreferenceManager.getDefaultSharedPreferences(mScope.getApp())
          .edit()
          .putBoolean("edit_mode", false)
          .apply();
    }
  }

  public boolean isEditMode() {
    return (mPrimaryWidgetLayout != null && mPrimaryWidgetLayout.isEditMode())
        || (mSecondaryWidgetLayout != null && mSecondaryWidgetLayout.isEditMode());
  }

  public void showWidgetProperties(ControlWidget widget) {
    AppCompatActivity activity = (AppCompatActivity) mScope.getActivity();
    if (activity == null) return;
    ControlWidget.WidgetData data = widget.getWidgetData();
    boolean isButton = "button".equals(data.type);
    boolean isCommandPalette = "command_palette".equals(data.type);
    boolean isText = "status".equals(data.type) || "message".equals(data.type);
    boolean showFontSize =
        isText
            || "button".equals(data.type)
            || "dpad".equals(data.type)
            || "command_palette".equals(data.type)
            || "palette".equals(data.type);
    boolean showMoveButton = mPrimaryWidgetLayout != null && mSecondaryWidgetLayout != null;

    WidgetPropertiesFragment fragment =
        WidgetPropertiesFragment.newInstance(
            data.label,
            isButton,
            isCommandPalette,
            data.horizontal,
            data.opacity,
            showFontSize,
            data.fontSize,
            data.rows,
            data.columns,
            data.category,
            data.contextualOnly,
            data.pinnedCommands,
            showMoveButton);
    fragment.setOnPropertiesListener(
        new WidgetPropertiesFragment.OnPropertiesListener() {
          @Override
          public void onLabelChanged(String newLabel) {
            data.label = newLabel;
            if (isButton && widget.getContentView() instanceof MaterialButton) {
              ((MaterialButton) widget.getContentView()).setText(newLabel);
            }
            ((WidgetLayout) widget.getParent()).saveDraftLayout();
          }

          @Override
          public void onOrientationChanged(boolean horizontal) {
            data.horizontal = horizontal;
            if (isCommandPalette && widget instanceof CommandPaletteWidget) {
              ((CommandPaletteWidget) widget)
                  .setConfiguration(
                      data.rows,
                      data.columns,
                      parseCategory(data.category),
                      horizontal,
                      data.contextualOnly,
                      data.pinnedCommands);
            }
            ((WidgetLayout) widget.getParent()).saveDraftLayout();
          }

          @Override
          public void onOpacityChanged(int opacity) {
            data.opacity = opacity;
            applyWidgetOpacity(widget, opacity);
            ((WidgetLayout) widget.getParent()).saveDraftLayout();
          }

          @Override
          public void onFontSizeChanged(int fontSize) {
            data.fontSize = fontSize;
            widget.setFontSize(fontSize);
            ((WidgetLayout) widget.getParent()).saveDraftLayout();
          }

          @Override
          public void onRowsChanged(int rows) {
            data.rows = rows;
            if (isCommandPalette && widget instanceof CommandPaletteWidget) {
              ((CommandPaletteWidget) widget)
                  .setConfiguration(
                      rows,
                      data.columns,
                      parseCategory(data.category),
                      data.horizontal,
                      data.contextualOnly,
                      data.pinnedCommands);
            }
            ((WidgetLayout) widget.getParent()).saveDraftLayout();
          }

          @Override
          public void onColumnsChanged(int columns) {
            data.columns = columns;
            if (isCommandPalette && widget instanceof CommandPaletteWidget) {
              ((CommandPaletteWidget) widget)
                  .setConfiguration(
                      data.rows,
                      columns,
                      parseCategory(data.category),
                      data.horizontal,
                      data.contextualOnly,
                      data.pinnedCommands);
            }
            ((WidgetLayout) widget.getParent()).saveDraftLayout();
          }

          @Override
          public void onCategoryChanged(String category) {
            data.category = category;
            if (isCommandPalette && widget instanceof CommandPaletteWidget) {
              ((CommandPaletteWidget) widget)
                  .setConfiguration(
                      data.rows,
                      data.columns,
                      parseCategory(category),
                      data.horizontal,
                      data.contextualOnly,
                      data.pinnedCommands);
            }
            ((WidgetLayout) widget.getParent()).saveDraftLayout();
          }

          @Override
          public void onContextualOnlyChanged(boolean contextualOnly) {
            data.contextualOnly = contextualOnly;
            if (isCommandPalette && widget instanceof CommandPaletteWidget) {
              ((CommandPaletteWidget) widget)
                  .setConfiguration(
                      data.rows,
                      data.columns,
                      parseCategory(data.category),
                      data.horizontal,
                      contextualOnly,
                      data.pinnedCommands);
            }
            ((WidgetLayout) widget.getParent()).saveDraftLayout();
          }

          @Override
          public void onPinnedCommandsChanged(java.util.Set<String> pinnedCommands) {
            data.pinnedCommands = pinnedCommands;
            if (isCommandPalette && widget instanceof CommandPaletteWidget) {
              ((CommandPaletteWidget) widget)
                  .setConfiguration(
                      data.rows,
                      data.columns,
                      parseCategory(data.category),
                      data.horizontal,
                      data.contextualOnly,
                      pinnedCommands);
            }
            ((WidgetLayout) widget.getParent()).saveDraftLayout();
          }

          @Override
          public void onMoveToOtherScreen() {
            moveWidgetToOtherScreen(widget);
          }

          @Override
          public void onDelete() {
            ((WidgetLayout) widget.getParent()).removeWidget(widget);
          }
        });
    fragment.show(activity.getSupportFragmentManager(), "widget_properties");
  }

  public void moveWidgetToOtherScreen(ControlWidget w) {
    if (mPrimaryWidgetLayout == null || mSecondaryWidgetLayout == null || w == null) return;

    WidgetLayout source = (WidgetLayout) w.getParent();
    if (source == null) return;

    WidgetLayout destination =
        (source == mPrimaryWidgetLayout) ? mSecondaryWidgetLayout : mPrimaryWidgetLayout;

    // Snapshot data
    ControlWidget.WidgetData data = w.getWidgetData();

    // Simple coordinate translation: center horizontally, maintain vertical fraction
    float destWidth = destination.getWidth();
    float destHeight = destination.getHeight();
    float sourceWidth = source.getWidth();
    float sourceHeight = source.getHeight();

    if (destWidth > 0 && destHeight > 0 && sourceWidth > 0 && sourceHeight > 0) {
      data.x = (destWidth - data.w) / 2f;
      float yFraction = data.y / sourceHeight;
      data.y = yFraction * destHeight;
    }

    // Remove from source
    source.removeWidget(w);

    // Create and add to destination
    ControlWidget newWidget = destination.createWidget(data);
    if (newWidget != null) {
      newWidget.setWidgetData(data);
      newWidget.setFontSize(data.fontSize);
      destination.addWidget(newWidget);
    }

    // Persist move to draft when in edit mode
    if (source.isEditMode() || destination.isEditMode()) {
      source.saveDraftLayout();
      destination.saveDraftLayout();
    }
  }
}
