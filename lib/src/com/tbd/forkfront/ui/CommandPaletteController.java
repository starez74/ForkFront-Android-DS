package com.tbd.forkfront.ui;

import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.tbd.forkfront.ForkFront;
import com.tbd.forkfront.NH_State;
import com.tbd.forkfront.R;
import com.tbd.forkfront.commands.CommandAdapter;
import com.tbd.forkfront.context.CmdRegistry;

public class CommandPaletteController {

  private final ForkFront mActivity;
  private BottomSheetBehavior<View> mCommandPaletteBehavior;
  private CommandAdapter mCommandAdapter;
  private CmdRegistry.OnCommandListener mCommandPaletteListener;

  public CommandPaletteController(ForkFront activity) {
    mActivity = activity;
  }

  public void setup() {
    View sheetContainer = mActivity.findViewById(R.id.command_palette_sheet);
    if (sheetContainer == null) return;

    View sheet =
        mActivity
            .getLayoutInflater()
            .inflate(R.layout.command_palette, (ViewGroup) sheetContainer, true);

    RecyclerView list = sheet.findViewById(R.id.command_list);
    mCommandAdapter =
        new CommandAdapter(
            CmdRegistry.getPaletteSorted(),
            cmd -> {
              if (mCommandPaletteListener != null) {
                mCommandPaletteListener.onCommandExecute(cmd);
              } else {
                NH_State s = mActivity.getState();
                if (s != null) {
                  s.getCommands().executeCommand(cmd);
                }
              }
              collapse();
            });
    list.setAdapter(mCommandAdapter);

    SearchView searchView = sheet.findViewById(R.id.command_search);
    searchView.setOnQueryTextListener(
        new SearchView.OnQueryTextListener() {
          @Override
          public boolean onQueryTextSubmit(String query) {
            mCommandAdapter.filter(query);
            return true;
          }

          @Override
          public boolean onQueryTextChange(String newText) {
            mCommandAdapter.filter(newText);
            return true;
          }
        });

    searchView.setOnQueryTextFocusChangeListener(
        (v, hasFocus) -> {
          if (hasFocus) {
            View edit = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
            if (edit != null) {
              edit.requestFocus();
              Util.showKeyboard(mActivity, edit);
            }
          }
        });

    View btnClose = sheet.findViewById(R.id.btn_close);
    if (btnClose != null) {
      btnClose.setOnClickListener(v -> collapse());
    }

    mCommandPaletteBehavior = BottomSheetBehavior.from(sheetContainer);
    if (mActivity.isFixedTabletLayout()) {
      // No permanent grey command-palette handle overlapping the map.
      // Explicit palette requests still expand the same sheet normally.
      mCommandPaletteBehavior.setHideable(true);
      mCommandPaletteBehavior.setPeekHeight(0);
      mCommandPaletteBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    } else {
      mCommandPaletteBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    ViewGroup mapFrame = mActivity.findViewById(R.id.map_frame);
    SearchView searchViewRef = searchView;
    mCommandPaletteBehavior.addBottomSheetCallback(
        new BottomSheetBehavior.BottomSheetCallback() {
          @Override
          public void onStateChanged(@NonNull View bottomSheet, int newState) {
            if (newState == BottomSheetBehavior.STATE_EXPANDED) {
              if (mapFrame != null) {
                mapFrame.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
              }
            } else if (newState == BottomSheetBehavior.STATE_COLLAPSED
                || newState == BottomSheetBehavior.STATE_HIDDEN) {
              if (searchViewRef != null) {
                searchViewRef.clearFocus();
              }
              if (mapFrame != null) {
                mapFrame.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
              }
              mCommandPaletteListener = null;
            }
          }

          @Override
          public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
        });
  }

  public void expand(CmdRegistry.OnCommandListener listener) {
    mCommandPaletteListener = listener;
    if (mCommandAdapter != null) {
      mCommandAdapter.filter("");
    }
    if (mCommandPaletteBehavior != null) {
      mCommandPaletteBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }
  }

  public void collapse() {
    if (mCommandPaletteBehavior != null) {
      mCommandPaletteBehavior.setState(mActivity.isFixedTabletLayout()
          ? BottomSheetBehavior.STATE_HIDDEN : BottomSheetBehavior.STATE_COLLAPSED);
    }
  }

  public boolean isExpanded() {
    return mCommandPaletteBehavior != null
        && mCommandPaletteBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED;
  }
}
