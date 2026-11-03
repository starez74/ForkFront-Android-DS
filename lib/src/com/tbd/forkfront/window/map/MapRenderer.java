package com.tbd.forkfront.window.map;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint.Align;
import android.graphics.Paint.FontMetrics;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.TypedValue;
import androidx.preference.PreferenceManager;
import com.tbd.forkfront.R;
import com.tbd.forkfront.window.text.TextAttr;

class MapRenderer {
  private final NHW_Map mMap;
  private MapViewport mViewport;
  TextPaint mPaint;
  Typeface mTypeface;
  final float mBaseTextSize;

  // Cached TTY text metrics
  float mCachedTileWidth = -1;
  float mCachedTileHeight = -1;
  float mCachedScale = -1;

  // Preference change listener
  SharedPreferences.OnSharedPreferenceChangeListener mPrefListener;

  MapRenderer(NHW_Map map) {
    mMap = map;
    mPaint = new TextPaint();
    mTypeface = Typeface.createFromAsset(mMap.mContext.getAssets(), "fonts/monobold.ttf");
    mPaint.setTypeface(mTypeface);
    mPaint.setTextAlign(Align.LEFT);
    mBaseTextSize = 48.f;

    // Apply smooth tile scaling preference
    updateTileFiltering();

    // Create preference change listener (registered in surfaceCreated)
    mPrefListener =
        new SharedPreferences.OnSharedPreferenceChangeListener() {
          @Override
          public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if ("smoothTileScaling".equals(key)) {
              updateTileFiltering();
              if (mMap.mUI != null) mMap.mUI.requestRedraw();
            }
          }
        };
  }

  void setViewport(MapViewport viewport) {
    mViewport = viewport;
  }

  void updateTileFiltering() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mMap.mContext);
    boolean smoothScaling = prefs.getBoolean("smoothTileScaling", true);

    if (smoothScaling) {
      mPaint.setFilterBitmap(true);
      mPaint.setAntiAlias(true);
    } else {
      mPaint.setFilterBitmap(false);
      mPaint.setAntiAlias(false);
    }
  }

  void updateGameBackgroundColor() {
    TypedValue typedValue = new TypedValue();
    if (mMap.mContext.getTheme().resolveAttribute(R.attr.colorGameBackground, typedValue, true)) {
      mMap.mGameBackgroundColor = typedValue.data;
    } else {
      mMap.mGameBackgroundColor = 0xFF000000;
    }
  }

  void draw(Canvas canvas, boolean isTTY) {
    synchronized (mMap.mTiles) {
      drawBorder(canvas);
      if (isTTY) drawAscii(canvas);
      else drawTiles(canvas);
    }
  }

  void drawTiles(Canvas canvas) {
    Rect dst = new Rect();

    float tileW = getScaledTileWidth();
    float tileH = getScaledTileHeight();

    Rect clipRect = new Rect();
    if (!canvas.getClipBounds(clipRect)) {
      clipRect.set(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    int minTileX =
        NHW_Map.clamp(
            (int) ((clipRect.left - mViewport.mViewOffset.x) / tileW), 0, NHW_Map.TileCols - 1);
    int maxTileX =
        NHW_Map.clamp(
            (int) Math.ceil((clipRect.right - mViewport.mViewOffset.x) / tileW),
            minTileX,
            NHW_Map.TileCols - 1);
    int minTileY =
        NHW_Map.clamp(
            (int) ((clipRect.top - mViewport.mViewOffset.y) / tileH), 0, NHW_Map.TileRows - 1);
    int maxTileY =
        NHW_Map.clamp(
            (int) Math.ceil((clipRect.bottom - mViewport.mViewOffset.y) / tileH),
            minTileY,
            NHW_Map.TileRows - 1);

    float left = (float) Math.floor(mViewport.mViewOffset.x + minTileX * tileW);
    float x = left;
    float y = (float) Math.floor(mViewport.mViewOffset.y + minTileY * tileH);

    mPaint.setAntiAlias(false);

    for (int tileY = minTileY; tileY <= maxTileY; tileY++) {
      for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
        dst.set((int) x, (int) y, (int) (x + tileW), (int) (y + tileH));
        NHW_Map.Tile tile = mMap.mTiles[tileY][tileX];
        if (tile.glyph >= 0) {
          mPaint.setColor(0xffffffff);
          mMap.mTileset.drawTile(canvas, tile.glyph, dst, mPaint);
          Bitmap ovl = mMap.mTileset.getTileOverlay(tile.overlay);
          if (ovl != null) canvas.drawBitmap(ovl, mMap.mTileset.getOverlayRect(), dst, mPaint);
        } else {
          mPaint.setColor(mMap.mGameBackgroundColor);
          canvas.drawRect(dst, mPaint);
        }

        x += tileW;
      }
      x = left;
      y += tileH;
    }

    drawCursor(canvas, tileW, tileH);
  }

  void drawCursor(Canvas canvas, float tileW, float tileH) {
    float x = (float) Math.floor(mViewport.mViewOffset.x);
    float y = (float) Math.floor(mViewport.mViewOffset.y);

    if (mMap.mCursorPos.x >= 0
        && (mMap.mHealthColor != 0 || mMap.mGamepadCursorController.isActive())) {
      int[] palette = TextAttr.getPalette(mMap.mContext);
      int color = mMap.mHealthColor != 0 ? palette[mMap.mHealthColor & 0xF] : palette[15];
      float strokeWidth = 2;

      if (mMap.mGamepadCursorController.isActive()) {
        color = palette[15];
        strokeWidth = 4;

        // Pulse effect based on time
        long now = System.currentTimeMillis();
        int alpha = 128 + (int) (127 * Math.sin(now / 150.0));
        mPaint.setColor((alpha << 24) | 0xFFC855); // Amber pulse
        mPaint.setStyle(Style.STROKE);
        mPaint.setStrokeWidth(strokeWidth + 4);
        RectF pulseRect = new RectF();
        pulseRect.left = x + mMap.mCursorPos.x * tileW - 2.0f;
        pulseRect.top = y + mMap.mCursorPos.y * tileH - 2.0f;
        pulseRect.right = pulseRect.left + tileW + 4.0f;
        pulseRect.bottom = pulseRect.top + tileH + 4.0f;
        canvas.drawRect(pulseRect, mPaint);
      }

      mPaint.setColor(color);
      mPaint.setStyle(Style.STROKE);
      mPaint.setStrokeWidth(strokeWidth);
      mPaint.setAntiAlias(false);
      RectF dst = new RectF();
      dst.left = x + mMap.mCursorPos.x * tileW + 0.5f;
      dst.top = y + mMap.mCursorPos.y * tileH + 0.5f;
      dst.right = dst.left + tileW - 2.0f;
      dst.bottom = dst.top + tileH - 2.0f;
      canvas.drawRect(dst, mPaint);
      mPaint.setStyle(Style.FILL);

      if (mMap.mGamepadCursorController.isActive()) {
        if (mMap.mUI != null) mMap.mUI.requestRedraw(); // Keep pulsing
      }
    }
  }

  void drawAscii(Canvas canvas) {
    RectF dst = new RectF();

    float tileW = getScaledTileWidth();
    float tileH = getScaledTileHeight();

    Rect clipRect = new Rect();
    if (!canvas.getClipBounds(clipRect)) {
      clipRect.set(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    int minTileX =
        NHW_Map.clamp(
            (int) ((clipRect.left - mViewport.mViewOffset.x) / tileW), 0, NHW_Map.TileCols - 1);
    int maxTileX =
        NHW_Map.clamp(
            (int) Math.ceil((clipRect.right - mViewport.mViewOffset.x) / tileW),
            minTileX,
            NHW_Map.TileCols - 1);
    int minTileY =
        NHW_Map.clamp(
            (int) ((clipRect.top - mViewport.mViewOffset.y) / tileH), 0, NHW_Map.TileRows - 1);
    int maxTileY =
        NHW_Map.clamp(
            (int) Math.ceil((clipRect.bottom - mViewport.mViewOffset.y) / tileH),
            minTileY,
            NHW_Map.TileRows - 1);

    float x = (float) Math.floor(mViewport.mViewOffset.x + minTileX * tileW);
    float y = (float) Math.floor(mViewport.mViewOffset.y + minTileY * tileH);

    dst.set(x, y, x + tileW, y + tileH);

    // setup paint
    mPaint.setAntiAlias(true);

    for (int tileY = minTileY; tileY <= maxTileY; tileY++) {
      for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
        NHW_Map.Tile tile = mMap.mTiles[tileY][tileX];
        int fgColor = TextAttr.getPalette(mMap.mContext)[tile.color];
        int bgColor = 0; // Transparent

        if (tileX == mMap.mCursorPos.x && tileY == mMap.mCursorPos.y) {
          int[] palette = TextAttr.getPalette(mMap.mContext);
          if (mMap.mHealthColor != 0) {
            bgColor = palette[mMap.mHealthColor & 0xF];
            fgColor = mMap.mGameBackgroundColor;
          } else {
            bgColor = 0x00000000;
            fgColor = palette[15]; // Cursor white
          }
        } else if (tile.overlay != 0 && tile.glyph >= 0) {
          bgColor = fgColor;
          fgColor = mMap.mGameBackgroundColor;
        }

        if (bgColor != 0) {
          mPaint.setColor(bgColor);
          canvas.drawRect(dst, mPaint);
        }

        if (tile.glyph >= 0) {
          mPaint.setColor(fgColor);
          canvas.drawText(tile.ch, 0, 1, dst.left, dst.bottom - mPaint.descent(), mPaint);
        }

        dst.offset(tileW, 0);
      }
      dst.left = x;
      dst.right = x + tileW;
      dst.offset(0, tileH);
    }
  }

  void drawBorder(Canvas canvas) {
    if ((mMap.mBorderColor & 0xffffff) == 0) return;

    float tileW = getScaledTileWidth();
    float tileH = getScaledTileHeight();

    float borderSize = 2;

    Rect clipRect = new Rect();
    if (!canvas.getClipBounds(clipRect)) {
      clipRect.set(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    float x = (float) Math.floor(mViewport.mViewOffset.x - 2 * borderSize);
    float y = (float) Math.floor(mViewport.mViewOffset.y - 2 * borderSize);
    float w = (float) Math.ceil(tileW * NHW_Map.TileCols + 4 * borderSize);
    float h = (float) Math.ceil(tileH * NHW_Map.TileRows + 4 * borderSize);

    mPaint.setAntiAlias(false);
    mPaint.setColor(mMap.mBorderColor);
    mPaint.setStrokeWidth(borderSize);
    mPaint.setStyle(Style.STROKE);

    canvas.drawRect(x, y, x + w, y + h, mPaint);

    mPaint.setStyle(Style.FILL);
  }

  void drawGuides(Canvas canvas) {
    mPaint.setAntiAlias(true);

    float tileW = getScaledTileWidth();
    float tileH = getScaledTileHeight();

    float cx = tileToScreenX(mMap.mPlayerPos.x) + tileW * 0.5f;
    float cy = tileToScreenY(mMap.mPlayerPos.y) + tileH * 0.5f;

    float radius0 = mMap.mSelfRadius;
    float radius1 = 5 * radius0;

    mPaint.setColor(0x20ffffff);
    canvas.drawCircle(cx, cy, radius1, mPaint);

    mPaint.setColor(0xffffffff);
    mPaint.setStyle(Style.STROKE);
    mPaint.setStrokeWidth(2);
    canvas.drawCircle(cx, cy, radius0, mPaint);

    // r^2=x^2+y^2
    // PIE_SLICE=y/x
    // y = sqrt(r^2/(1+1/PIE_SLICE^2))
    float y0 =
        (float) Math.sqrt(radius0 * radius0 / (1 + 1 / (NHW_Map.PIE_SLICE * NHW_Map.PIE_SLICE)));
    float y1 =
        (float) Math.sqrt(radius1 * radius1 / (1 + 1 / (NHW_Map.PIE_SLICE * NHW_Map.PIE_SLICE)));
    float x0 = y0 / NHW_Map.PIE_SLICE;
    float x1 = y1 / NHW_Map.PIE_SLICE;
    canvas.drawLine(x0 + cx, y0 + cy, x1 + cx, y1 + cy, mPaint);
    canvas.drawLine(x0 + cx, -y0 + cy, x1 + cx, -y1 + cy, mPaint);
    canvas.drawLine(-x0 + cx, y0 + cy, -x1 + cx, y1 + cy, mPaint);
    canvas.drawLine(-x0 + cx, -y0 + cy, -x1 + cx, -y1 + cy, mPaint);

    canvas.drawLine(y0 + cx, -x0 + cy, y1 + cx, -x1 + cy, mPaint);
    canvas.drawLine(y0 + cx, x0 + cy, y1 + cx, x1 + cy, mPaint);
    canvas.drawLine(-y0 + cx, -x0 + cy, -y1 + cx, -x1 + cy, mPaint);
    canvas.drawLine(-y0 + cx, x0 + cy, -y1 + cx, x1 + cy, mPaint);

    mPaint.setStyle(Style.FILL);
  }

  float tileToScreenX(int tileX) {
    return mViewport.mViewOffset.x + tileX * getScaledTileWidth();
  }

  float tileToScreenY(int tileY) {
    return mViewport.mViewOffset.y + tileY * getScaledTileHeight();
  }

  int screenToTileX(float screenX) {
    return (int) ((screenX - mViewport.mViewOffset.x) / getScaledTileWidth());
  }

  int screenToTileY(float screenY) {
    return (int) ((screenY - mViewport.mViewOffset.y) / getScaledTileHeight());
  }

  private void updateTextMetrics() {
    if (mViewport.mScale != mCachedScale) {
      mPaint.setTextSize(mBaseTextSize * mViewport.mScale);
      float w = mPaint.measureText("\u2550");
      mCachedTileWidth = (float) Math.floor(w) - 1;
      FontMetrics metrics = mPaint.getFontMetrics();
      mCachedTileHeight = (float) Math.floor(metrics.descent - metrics.ascent);
      mCachedScale = mViewport.mScale;
    }
  }

  float getScaledTileWidth() {
    if (mMap.isTTY()) {
      updateTextMetrics();
      return mCachedTileWidth;
    }

    return mMap.mTileset.getTileWidth() * mViewport.mScale;
  }

  float getScaledTileHeight() {
    if (mMap.isTTY()) {
      updateTextMetrics();
      return mCachedTileHeight;
    }

    return mMap.mTileset.getTileHeight() * mViewport.mScale;
  }

  float getBaseTileHeight() {
    if (mMap.isTTY()) return mBaseTextSize;
    return mMap.mTileset.getTileHeight();
  }
}
