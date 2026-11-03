package com.tbd.forkfront.window.menu;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import com.tbd.forkfront.R;
import com.tbd.forkfront.window.map.TileDrawable;
import com.tbd.forkfront.window.map.Tileset;
import java.util.ArrayList;

public class MenuItemAdapter extends ArrayAdapter<MenuItem> {
  private ArrayList<MenuItem> mItems;
  private Context mContext;
  private Tileset mTileset;
  private MenuSelectMode mHow;
  private boolean mMenuHasTiles;
  private boolean mIsMonospaceMode;
  private final int mRowLayoutRes;
  private final boolean mCompactTabletRow;

  public MenuItemAdapter(
      AppCompatActivity context,
      int textViewResourceId,
      ArrayList<MenuItem> items,
      Tileset tileset,
      MenuSelectMode how) {
    super(context, textViewResourceId, items);
    mItems = items;
    mContext = context;
    mTileset = tileset;
    mHow = how;
    mRowLayoutRes = textViewResourceId;
    mCompactTabletRow = textViewResourceId == R.layout.tablet_menu_item;
    mMenuHasTiles = menuHasTiles(items);
    updateMonospaceFlag();
  }

  private void updateMonospaceFlag() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    mIsMonospaceMode = prefs.getBoolean("monospace", false);
  }

  private static boolean menuHasTiles(ArrayList<MenuItem> items) {
    for (MenuItem item : items) {
      if (item.hasTile()) return true;
    }
    return false;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    final float density = mContext.getResources().getDisplayMetrics().density;
    final int clickableItemMinH = (int) ((mCompactTabletRow ? 32 : 35) * density + 0.5f);
    final int clickableHeaderMinH = (int) ((mCompactTabletRow ? 23 : 25) * density + 0.5f);

    View v = convertView;
    if (v == null) {
      LayoutInflater vi =
          (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      v = vi.inflate(mRowLayoutRes, parent, false);
    }
    MenuItem item = mItems.get(position);
    if (item != null) {
      if (item.isHeader()) {
        TypedValue typedValue = new TypedValue();
        if (mContext.getTheme().resolveAttribute(R.attr.colorSurfaceVariant, typedValue, true)) {
          v.setBackgroundColor(typedValue.data);
        } else {
          v.setBackgroundResource(R.color.md_theme_surfaceVariant);
        }
        if (mCompactTabletRow || mHow == MenuSelectMode.PickMany)
          v.setMinimumHeight(clickableHeaderMinH);
        else v.setMinimumHeight(0);
      } else {
        v.setBackgroundResource(0);
        if (mHow == MenuSelectMode.PickNone && !mCompactTabletRow)
          v.setMinimumHeight(0);
        else v.setMinimumHeight(clickableItemMinH);
      }

      TextView tt = (TextView) v.findViewById(R.id.item_text);
      tt.setText(item.getText());

      TextView at = (TextView) v.findViewById(R.id.item_acc);
      if (item.isHeader()) {
        at.setVisibility(View.GONE);
      } else {
        at.setVisibility(View.VISIBLE);
        at.setText(mCompactTabletRow
            ? (item.hasAcc() ? Character.toString(item.getAcc()) : "")
            : item.getAccText());
        if (mCompactTabletRow) {
          at.getLayoutParams().width = (int) (22 * density + 0.5f);
        } else if (mIsMonospaceMode) {
          at.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
          at.requestLayout();
          at.invalidate();
        } else {
          int fixedW = (int) ((mMenuHasTiles && mTileset.hasTiles() ? 20 : 40) * density);
          at.setWidth(fixedW);
        }
      }

      TextView st = (TextView) v.findViewById(R.id.item_sub);
      st.setText(item.getSubText());
      if (item.hasSubText()) st.setVisibility(View.VISIBLE);
      else st.setVisibility(View.GONE);

      TextView ic = (TextView) v.findViewById(R.id.item_count);
      if (item.getCount() > 0) ic.setText(Integer.toString(item.getCount()));
      else ic.setText("");

      ImageView tile = (ImageView) v.findViewById(R.id.item_tile);
      if (mMenuHasTiles && mTileset.hasTiles() && !item.isHeader()) {
        if (item.hasTile()) {
          tile.setVisibility(View.VISIBLE);
          tile.setImageDrawable(new TileDrawable(mTileset, item.getTile()));
          v.findViewById(R.id.item_sub).setVisibility(View.VISIBLE);
        } else {
          // Don't show it but reserve its space for alignment
          tile.setVisibility(View.INVISIBLE);
        }
      } else {
        tile.setVisibility(View.GONE);
      }

      CheckBox cb = (CheckBox) v.findViewById(R.id.item_check);
      if (mHow == MenuSelectMode.PickMany && !item.isHeader()) {
        cb.setVisibility(View.VISIBLE);
        cb.setChecked(item.isSelected());
      } else cb.setVisibility(View.GONE);

      boolean enabled = isEnabled(position);
      // We don't call setEnabled(enabled) on the text views/tiles here because it causes
      // them to be rendered with 'disabled' alpha which is invisible in light mode.
      // The ListView itself handles the interaction semantics via isEnabled(position).
      cb.setEnabled(enabled);

      // Propagate multi-select state to the row background selector so
      // state_activated fires (see nh_gamepad_list_selector.xml). Must run
      // on EVERY getView call, not just on inflate, so recycled rows pick
      // up the correct activation state.
      if (mHow == MenuSelectMode.PickMany && !item.isHeader()) {
        v.setActivated(item.isSelected());
      } else {
        v.setActivated(false);
      }
    }
    return v;
  }

  @Override
  public void notifyDataSetChanged() {
    super.notifyDataSetChanged();
    updateMonospaceFlag();
  }

  @Override
  public boolean areAllItemsEnabled() {
    return false;
  }

  @Override
  public boolean isEnabled(int position) {
    MenuItem item = mItems.get(position);
    if (item.isHeader()) return mHow == MenuSelectMode.PickMany;
    return item.isSelectable();
  }
}
