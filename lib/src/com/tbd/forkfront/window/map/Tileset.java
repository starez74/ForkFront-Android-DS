package com.tbd.forkfront.window.map;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.text.TextPaint;
import android.util.LruCache;
import android.widget.Toast;
import androidx.preference.PreferenceManager;
import com.tbd.forkfront.R;
import com.tbd.forkfront.ui.Util;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class Tileset {
  public final int OVERLAY_DETECT = 0x04;
  public final int OVERLAY_PET = 0x08;
  public final int OVERLAY_OBJPILE = 0x40;

  private static final String LOCAL_TILESET_NAME = "custom_tileset";

  private Bitmap mBitmap;
  private Bitmap mOverlay;
  private int mTileW;
  private int mTileH;
  private String mTilesetName = "";
  private int mnCols;
  private Context mContext;
  private boolean mFallbackRenderer;
  private final LruCache<Integer, Bitmap> mTileCache;
  private final String mNamespace;

  public Tileset(Context context) {
    mContext = context;
    mNamespace = context.getResources().getString(R.string.namespace);
    // Cache up to ~1MB of tiles (typical tile is small)
    final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
    final int cacheSize = maxMemory / 32;
    mTileCache =
        new LruCache<Integer, Bitmap>(cacheSize) {
          @Override
          protected int sizeOf(Integer key, Bitmap bitmap) {
            return bitmap.getByteCount() / 1024;
          }
        };
  }

  public void setContext(Context context) {
    mContext = context;
  }

  public void updateTileset(SharedPreferences prefs, Resources r) {
    mFallbackRenderer = prefs.getBoolean("fallbackRenderer", false);

    String tilesetName = "TTY";
    boolean customTiles = false;

    boolean TTY = tilesetName.equals("TTY");
    int tileW, tileH;
    if (customTiles) {
      try {
        tileW = Integer.parseInt(prefs.getString("customTileW", "32"));
        tileH = Integer.parseInt(prefs.getString("customTileH", "32"));
      } catch (NumberFormatException e) {
        tileW = 32;
        tileH = 32;
      }
    } else {
      tileW = prefs.getInt("tileW", 32);
      tileH = prefs.getInt("tileH", 32);
    }

    if (mTilesetName.equals(tilesetName) && tileW == mTileW && tileH == mTileH) return;
    mTilesetName = tilesetName;

    if (!TTY && (tileW <= 0 || tileH <= 0)) {
      Toast.makeText(
              mContext, "Invalid tile dimensions (" + tileW + "x" + tileH + ")", Toast.LENGTH_LONG)
          .show();
      TTY = true;
    }

    if (!TTY) {
      mTileW = tileW;
      mTileH = tileH;

      if (customTiles) loadCustomTileset(tilesetName);
      else loadFromResources(tilesetName, r);

      int id = mContext.getResources().getIdentifier("overlays", "drawable", mNamespace);
      if (id > 0) {
        BitmapDrawable bmpDrawable = (BitmapDrawable) r.getDrawable(id);
        mOverlay = bmpDrawable.getBitmap();
      } else mOverlay = null;

      if (mBitmap == null || mOverlay == null) TTY = true;
      else mnCols = mBitmap.getWidth() / mTileW;

      if (mnCols <= 0) {
        Toast.makeText(
                mContext,
                "Invalid tileset settings '" + tilesetName + "' (" + mTileW + "x" + mTileH + ")",
                Toast.LENGTH_LONG)
            .show();
        TTY = true;
      }
    }

    if (TTY) {
      clearBitmap();
      mTileW = 0;
      mTileH = 0;
      mnCols = 0;
    }
  }

  private void clearBitmap() {
    mTileCache.evictAll();
    mBitmap = null;
  }

  private void loadCustomTileset(String tilesetName) {
    clearBitmap();
    mBitmap = tryLoadBitmap(getLocalTilesetFile(mContext).getPath(), false);
    // Fallback if coming from an old version
    if (mBitmap == null) mBitmap = tryLoadBitmap(tilesetName, true);
    if (mBitmap == null)
      Toast.makeText(mContext, "Error loading custom tileset", Toast.LENGTH_LONG).show();
  }

  private Bitmap tryLoadBitmap(String path, boolean logFailure) {
    Bitmap bitmap = null;
    try {
      bitmap = BitmapFactory.decodeFile(path);
    } catch (Exception e) {
      if (logFailure)
        Toast.makeText(mContext, "Error loading custom tileset: " + e.toString(), Toast.LENGTH_LONG)
            .show();
    } catch (OutOfMemoryError e) {
      if (logFailure)
        Toast.makeText(mContext, "Error loading custom tileset: Out of memory", Toast.LENGTH_LONG)
            .show();
    }
    return bitmap;
  }

  private void loadFromResources(String tilesetName, Resources r) {
    int id = r.getIdentifier(tilesetName, "drawable", mNamespace);

    clearBitmap();
    if (id > 0) {
      BitmapDrawable bmpDrawable = (BitmapDrawable) r.getDrawable(id);
      mBitmap = bmpDrawable.getBitmap();
    }
  }

  private Point getTileBitmapOffset(int iTile) {
    if (mBitmap == null) return new Point(0, 0);

    int iRow = iTile / mnCols;
    int iCol = iTile - iRow * mnCols;

    int x = iCol * mTileW;
    int y = iRow * mTileH;

    return new Point(x, y);
  }

  private Bitmap getTile(int iTile) {
    if (mBitmap == null) return null;
    Bitmap bitmap = mTileCache.get(iTile);
    if (bitmap == null) {
      Point ofs = getTileBitmapOffset(iTile);

      try {
        bitmap = Bitmap.createBitmap(mBitmap, ofs.x, ofs.y, mTileW, mTileH);
        mTileCache.put(iTile, bitmap);
      } catch (Exception e) {
        // Invalid glyph; return null so caller can handle fallback
        return null;
      }
    }
    return bitmap;
  }

  public int getTileWidth() {
    return mTileW;
  }

  public int getTileHeight() {
    return mTileH;
  }

  public Rect getOverlayRect() {
    return new Rect(0, 0, mTileW, mTileH);
  }

  public Bitmap getTileOverlay(short overlay) {
    if ((overlay & (OVERLAY_PET | OVERLAY_DETECT | OVERLAY_OBJPILE)) != 0) return mOverlay;
    return null;
  }

  public boolean hasTiles() {
    return mBitmap != null;
  }

  public void drawTile(Canvas canvas, int glyph, Rect dst, TextPaint paint) {
    if (mBitmap == null) return;
    Rect src = new Rect();
    if (mFallbackRenderer) {
      Bitmap bitmap = getTile(glyph);
      if (bitmap == null) return;
      src.left = 0;
      src.top = 0;
      src.right = getTileWidth();
      src.bottom = getTileHeight();
      canvas.drawBitmap(bitmap, src, dst, paint);
    } else {
      Point ofs = getTileBitmapOffset(glyph);
      src.left = ofs.x;
      src.top = ofs.y;
      src.right = ofs.x + getTileWidth();
      src.bottom = ofs.y + getTileHeight();
      canvas.drawBitmap(mBitmap, src, dst, paint);
    }
  }

  public static boolean createCustomTilesetLocalCopy(Context context, android.net.Uri from) {
    try (InputStream inputStream = context.getContentResolver().openInputStream(from);
        OutputStream outputStream = new FileOutputStream(getLocalTilesetFile(context), false)) {
      if (inputStream == null) return false;
      Util.copy(inputStream, outputStream);
      return true;
    } catch (Exception e) {
      Toast.makeText(context, "Error loading tileset: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
    return false;
  }

  public static File getLocalTilesetFile(Context context) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    File dir = new File(prefs.getString("datadir", ""));
    File file = new File(dir, LOCAL_TILESET_NAME);
    return file;
  }
}
