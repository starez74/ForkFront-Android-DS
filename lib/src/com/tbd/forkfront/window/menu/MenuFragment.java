package com.tbd.forkfront.window.menu;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.tbd.forkfront.R;
import com.tbd.forkfront.engine.NetHackViewModel;
import com.tbd.forkfront.input.Input;
import com.tbd.forkfront.input.KeyEventResult;
import java.util.ArrayList;
import java.util.Set;

public class MenuFragment extends Fragment implements AmountSelector.Listener {
  private NHW_Menu mController;
  private View mRoot;
  private ListView mListView;
  private AmountSelector mAmountSelector;
  private View mSelectAllBtn;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    int wid = getArguments().getInt("wid");
    NetHackViewModel viewModel =
        new ViewModelProvider(requireActivity()).get(NetHackViewModel.class);
    mController = (NHW_Menu) viewModel.getState().getWindows().get(wid);
  }

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    MenuSelectMode how = mController.mHow;
    boolean docked = mController.mType == NHW_Menu.Type.Menu
        && requireActivity() instanceof com.tbd.forkfront.ForkFront
        && ((com.tbd.forkfront.ForkFront) requireActivity()).isFixedTabletLayout();
    if (how == MenuSelectMode.PickMany) {
      mRoot = inflater.inflate(docked ? R.layout.tablet_menu3 : R.layout.dialog_menu3,
          container, false);
    } else {
      mRoot = inflater.inflate(docked ? R.layout.tablet_menu1 : R.layout.dialog_menu1,
          container, false);
    }

    if (docked) {
      // NetHack inventory often supplies no title: keep the dock recognisable.
      android.widget.TextView title = mRoot.findViewById(R.id.title);
      if (title != null) {
        String menuTitle = mController.getTitle();
        if (menuTitle == null || menuTitle.trim().isEmpty()) {
          menuTitle = how == MenuSelectMode.PickNone ? "Inventory" : "Menu";
        }
        title.setText(menuTitle);
      }
    }

    mListView = mRoot.findViewById(R.id.menu_list);
    mSelectAllBtn = mRoot.findViewById(R.id.btn_all);
    View okBtn = mRoot.findViewById(R.id.btn_ok);
    View cancelBtn = mRoot.findViewById(R.id.btn_cancel);
    View closeBtn = mRoot.findViewById(R.id.btn_close);

    if (mSelectAllBtn instanceof Button) {
      mSelectAllBtn.setOnClickListener(
          v -> {
            if (((Button) mSelectAllBtn).getText().equals("Select all")) {
              dispatchSelectAll();
            } else {
              dispatchClearAll();
            }
          });
    }

    if (okBtn != null) {
      okBtn.setOnClickListener(v -> dispatchSelectChecked());
    }
    if (cancelBtn != null) {
      cancelBtn.setOnClickListener(v -> dispatchCancel());
    }
    if (closeBtn != null) {
      closeBtn.setOnClickListener(v -> dispatchCancel());
    }

    if (mListView != null) {
      mListView.setAdapter(
          new MenuItemAdapter(
              (AppCompatActivity) requireActivity(),
              docked ? R.layout.tablet_menu_item : R.layout.menu_item,
              (ArrayList<MenuItem>) mController.mItems,
              mController.mTileset,
              mController.mHow));
      mListView.setOnItemClickListener(
          (parent, view, position, id) -> {
            switch (mController.mHow) {
              case PickNone:
                mController.sendSelectNone();
                mController.close();
                break;
              case PickOne:
                MenuItem item = (MenuItem) mListView.getItemAtPosition(position);
                if (!item.isHeader() && item.isSelectable()) {
                  dispatchSelectOne(item, mController.mKeyboardCount);
                }
                break;
              case PickMany:
                toggleItemOrGroupAt(position);
                break;
            }
          });
      mListView.setOnItemLongClickListener(
          (parent, view, position, id) -> {
            MenuItem item = (MenuItem) mListView.getItemAtPosition(position);
            if (item.isSelectable() && item.getMaxCount() > 1) {
              dispatchShowAmount(item);
              return true;
            }
            return false;
          });
    }

    mRoot.requestFocus();
    updateSelectAllBtn();

    return mRoot;
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    mRoot = null;
    mListView = null;
    mSelectAllBtn = null;
  }

  public void refresh() {
    if (mListView != null) {
      ((MenuItemAdapter) mListView.getAdapter()).notifyDataSetChanged();
      updateSelectAllBtn();
    }
  }

  private void updateSelectAllBtn() {
    if (!(mSelectAllBtn instanceof Button)) return;
    boolean any = false;
    for (MenuItem item : mController.mItems) {
      if (item.isSelectable() && item.isSelected()) {
        any = true;
        break;
      }
    }
    ((Button) mSelectAllBtn).setText(any ? "Clear all" : "Select all");
  }

  private void toggleItemOrGroupAt(int pos) {
    MenuItem item = mController.mItems.get(pos);
    if (item.isHeader()) {
      char groupAcc = item.getGroupAcc();
      boolean anySelected = false;
      for (MenuItem i : mController.mItems) {
        if (i.getGroupAcc() == groupAcc && !i.isHeader() && i.isSelected()) {
          anySelected = true;
          break;
        }
      }
      for (MenuItem i : mController.mItems) {
        if (i.getGroupAcc() == groupAcc && !i.isHeader()) {
          i.setSelected(!anySelected);
          i.setCount(!anySelected ? i.getMaxCount() : 0);
        }
      }
    } else {
      item.setSelected(!item.isSelected());
      item.setCount(item.isSelected() ? item.getMaxCount() : 0);
    }
    refresh();
  }

  private void dispatchCancel() {
    mController.sendCancelSelect();
    mController.close();
  }

  private void dispatchSelectAll() {
    for (MenuItem item : mController.mItems) {
      if (item.isSelectable()) {
        item.setSelected(true);
        item.setCount(item.getMaxCount());
      }
    }
    refresh();
  }

  private void dispatchClearAll() {
    for (MenuItem item : mController.mItems) {
      if (item.isSelectable()) {
        item.setSelected(false);
        item.setCount(0);
      }
    }
    refresh();
  }

  private void dispatchSelectOne(MenuItem item, int count) {
    mController.sendSelectOne(item, count);
    mController.close();
  }

  private void dispatchShowAmount(MenuItem item) {
    mAmountSelector =
        new AmountSelector(this, (AppCompatActivity) requireActivity(), mController.mTileset, item);
  }

  private void dispatchSelectChecked() {
    ArrayList<MenuItem> items = new ArrayList<>();
    for (MenuItem item : mController.mItems) {
      if (item.isSelected()) {
        items.add(item);
      }
    }
    mController.sendSelectChecked(items);
    mController.close();
  }

  private KeyEventResult navigateList(int keyCode) {
    if (mListView == null) {
      return KeyEventResult.IGNORED;
    }

    if (!mListView.hasFocus()) {
      mListView.requestFocus();
    }

    int pos = mListView.getSelectedItemPosition();
    boolean wasInvalid = (pos == ListView.INVALID_POSITION);
    if (wasInvalid) {
      pos =
          (keyCode == KeyEvent.KEYCODE_DPAD_UP)
              ? mListView.getCount() - 1
              : mListView.getFirstVisiblePosition();
    }

    switch (keyCode) {
      case KeyEvent.KEYCODE_DPAD_UP:
        if (!wasInvalid) {
          pos--;
        }
        while (pos >= 0 && !mListView.getAdapter().isEnabled(pos)) {
          pos--;
        }
        if (pos >= 0) {
          mListView.setSelection(pos);
        }
        break;
      case KeyEvent.KEYCODE_DPAD_DOWN:
        if (!wasInvalid) {
          pos++;
        }
        while (pos < mListView.getCount() && !mListView.getAdapter().isEnabled(pos)) {
          pos++;
        }
        if (pos < mListView.getCount()) {
          mListView.setSelection(pos);
        }
        break;
      case KeyEvent.KEYCODE_DPAD_CENTER:
        if (pos != ListView.INVALID_POSITION) {
          MenuItem item = (MenuItem) mListView.getItemAtPosition(pos);
          if (mController.mHow == MenuSelectMode.PickOne) {
            mController.sendSelectOne(item, mController.mKeyboardCount);
          } else {
            toggleItemOrGroupAt(pos);
          }
        }
        break;
      case KeyEvent.KEYCODE_PAGE_UP:
        mListView.smoothScrollBy(-mListView.getHeight() * 3 / 4, 100);
        break;
      case KeyEvent.KEYCODE_PAGE_DOWN:
        mListView.smoothScrollBy(mListView.getHeight() * 3 / 4, 100);
        break;
      case KeyEvent.KEYCODE_MOVE_HOME:
        mListView.setSelection(0);
        break;
      case KeyEvent.KEYCODE_MOVE_END:
        mListView.setSelection(mListView.getCount() - 1);
        break;
      default:
        return KeyEventResult.IGNORED;
    }
    return KeyEventResult.HANDLED;
  }

  private int getAccelerator(char ch) {
    for (int i = 0; i < mController.mItems.size(); i++) {
      if (mController.mItems.get(i).getAcc() == ch) return i;
    }
    return -1;
  }

  public KeyEventResult handleKeyDown(
      char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount) {
    if (mAmountSelector != null)
      return mAmountSelector.handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount);

    NHW_Menu model = mController;

    if (ch == '<') {
      keyCode = KeyEvent.KEYCODE_PAGE_UP;
    } else if (ch == '>') {
      keyCode = KeyEvent.KEYCODE_PAGE_DOWN;
    }

    if (keyCode < 0) {
      return KeyEventResult.IGNORED;
    }

    switch (keyCode) {
      case KeyEvent.KEYCODE_DPAD_UP:
      case KeyEvent.KEYCODE_DPAD_DOWN:
      case KeyEvent.KEYCODE_DPAD_LEFT:
      case KeyEvent.KEYCODE_DPAD_RIGHT:
        return KeyEventResult.RETURN_TO_SYSTEM;

      case KeyEvent.KEYCODE_DPAD_CENTER:
        return navigateList(keyCode);

      case KeyEvent.KEYCODE_ESCAPE:
      case KeyEvent.KEYCODE_BACK:
        dispatchCancel();
        break;

      case KeyEvent.KEYCODE_PAGE_DOWN:
      case KeyEvent.KEYCODE_PAGE_UP:
        return navigateList(keyCode);

      case KeyEvent.KEYCODE_ENTER:
      case KeyEvent.KEYCODE_SPACE:
        return KeyEventResult.RETURN_TO_SYSTEM;

      default:
        if (model.mHow == MenuSelectMode.PickNone) {
          if (getAccelerator(ch) >= 0) {
            dispatchSelectChecked();
            return KeyEventResult.HANDLED;
          }
          return KeyEventResult.RETURN_TO_SYSTEM;
        } else if (ch >= '0' && ch <= '9') {
          if (model.mKeyboardCount < 0) {
            model.mKeyboardCount = 0;
          }
          model.mKeyboardCount = model.mKeyboardCount * 10 + ch - '0';
          return KeyEventResult.HANDLED;
        } else {
          int pos = getAccelerator(ch);
          if (pos >= 0) {
            MenuItem item = model.mItems.get(pos);
            if (!item.isHeader() && item.isSelectable()) {
              if (model.mHow == MenuSelectMode.PickOne) {
                dispatchSelectOne(item, model.mKeyboardCount);
              } else {
                toggleItemOrGroupAt(pos);
              }
              return KeyEventResult.HANDLED;
            }
          }
          if (model.mHow == MenuSelectMode.PickMany) {
            if (ch == '.' || keyCode == KeyEvent.KEYCODE_PERIOD) {
              dispatchSelectAll();
              return KeyEventResult.HANDLED;
            } else if (ch == '-' || keyCode == KeyEvent.KEYCODE_MINUS) {
              dispatchClearAll();
              return KeyEventResult.HANDLED;
            }
          }
        }
        return KeyEventResult.RETURN_TO_SYSTEM;
    }
    return KeyEventResult.HANDLED;
  }

  public boolean handleGamepadKey(KeyEvent ev) {
    if (mAmountSelector != null) return mAmountSelector.handleGamepadKey(ev);

    if (ev.getAction() != KeyEvent.ACTION_DOWN) {
      return false;
    }

    NHW_Menu model = mController;

    switch (ev.getKeyCode()) {
      case KeyEvent.KEYCODE_BUTTON_A:
        if (mListView == null || !mListView.hasFocus()) {
          return false;
        }
        int pos = mListView.getSelectedItemPosition();
        if (pos == -1) pos = mListView.getFirstVisiblePosition();
        if (pos != -1) {
          MenuItem item = (MenuItem) mListView.getItemAtPosition(pos);
          if (model.mHow == MenuSelectMode.PickOne) {
            dispatchSelectOne(item, model.mKeyboardCount);
          } else {
            toggleItemOrGroupAt(pos);
          }
          return true;
        }
        return false;

      case KeyEvent.KEYCODE_BUTTON_B:
      case KeyEvent.KEYCODE_BUTTON_SELECT:
        model.mKeyboardCount = -1;
        dispatchCancel();
        return true;

      case KeyEvent.KEYCODE_BUTTON_X:
        if (model.mHow != MenuSelectMode.PickNone) {
          int selPos = mListView != null ? mListView.getSelectedItemPosition() : -1;
          if (selPos == -1 && mListView != null) {
            selPos = mListView.getFirstVisiblePosition();
          }
          if (selPos != -1 && selPos < model.mItems.size()) {
            MenuItem item = model.mItems.get(selPos);
            if (item.isSelectable() && item.getMaxCount() > 1) {
              dispatchShowAmount(item);
              return true;
            }
          }
          if (model.mHow == MenuSelectMode.PickMany) {
            dispatchSelectAll();
          } else {
            model.mKeyboardCount = -1;
          }
          return true;
        }
        break;

      case KeyEvent.KEYCODE_BUTTON_L1:
        if (model.mHow == MenuSelectMode.PickMany) {
          dispatchSelectChecked();
          return true;
        }
        return navigateList(KeyEvent.KEYCODE_PAGE_UP) == KeyEventResult.HANDLED;

      case KeyEvent.KEYCODE_BUTTON_R1:
        return navigateList(KeyEvent.KEYCODE_PAGE_DOWN) == KeyEventResult.HANDLED;

      case KeyEvent.KEYCODE_BUTTON_L2:
        return navigateList(KeyEvent.KEYCODE_MOVE_HOME) == KeyEventResult.HANDLED;

      case KeyEvent.KEYCODE_BUTTON_R2:
        return navigateList(KeyEvent.KEYCODE_MOVE_END) == KeyEventResult.HANDLED;
    }
    return false;
  }

  public boolean handleGamepadMotion(MotionEvent ev) {
    return false;
  }

  @Override
  public void onDismissCount(MenuItem item, int amount) {
    mAmountSelector = null;
    if (amount != -1) {
      item.setCount(amount);
      item.setSelected(amount != 0);
    }
    refresh();
  }
}
