/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mirrors frameworks/base SystemUI
 * {@code com.android.systemui.statusbar.pipeline.shared.ui.binder.ClockChipWallpaperThumbnailHelper}
 * so Settings chip preview matches the status bar wallpaper strip.
 */

package org.lineageos.lineageparts.statusbar;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/**
 * Builds the same wallpaper thumbnail pipeline as SystemUI’s clock chip style 13.
 */
public final class ClockChipWallpaperThumbnailHelper {

    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final String IMAGE_WALLPAPER_SERVICE = "ImageWallpaper";

    /** Target width:height of the cropped region (must match SystemUI). */
    private static final float CROP_ASPECT_WIDTH_OVER_HEIGHT = 2.85f;

    private static final int DECODE_MAX_EDGE_PX = 1024;
    private static final float OUTPUT_MAX_EDGE_DP = 128f;

    private ClockChipWallpaperThumbnailHelper() {}

    /** Drawable + center sample for text contrast (same role as SystemUI ChipBackground). */
    public static final class ChipBackground {
        public final Drawable drawable;
        /** ARGB packed; use with {@link androidx.core.graphics.ColorUtils#calculateLuminance(int)}. */
        public final int contrastSampleArgb;

        ChipBackground(Drawable drawable, int contrastSampleArgb) {
            this.drawable = drawable;
            this.contrastSampleArgb = contrastSampleArgb;
        }
    }

    /**
     * Same pipeline as SystemUI. Safe to call on a background thread (recommended); may block
     * briefly if called on the main thread.
     */
    @NonNull
    public static ChipBackground loadChipBackground(@NonNull Context context) {
        WallpaperManager wm = WallpaperManager.getInstance(context);
        if (!wm.isWallpaperSupported()) {
            return blackChip();
        }
        if (!isHomeWallpaperBitmapBacked(wm)) {
            return blackChip();
        }
        try {
            Drawable full = wm.getDrawable(WallpaperManager.FLAG_SYSTEM);
            if (full == null) {
                return blackChip();
            }
            float density = context.getResources().getDisplayMetrics().density;
            int outMaxPx = Math.max(48, (int) (OUTPUT_MAX_EDGE_DP * density));

            Bitmap decoded = decodeWallpaperWorkBitmap(full);
            decoded = ensureArgb8888(decoded);

            Bitmap cropped = cropToChipAspect(decoded, CROP_ASPECT_WIDTH_OVER_HEIGHT, true);
            if (cropped != decoded && !decoded.isRecycled()) {
                decoded.recycle();
            }

            Bitmap finalBmp = scaleToMaxEdgePreservingAspect(cropped, outMaxPx);
            if (finalBmp != cropped && !cropped.isRecycled()) {
                cropped.recycle();
            }

            finalBmp = ensureArgb8888(finalBmp);

            int sample = sampleCenterArgbSafe(finalBmp);
            return new ChipBackground(
                    new NoIntrinsicSizeBitmapDrawable(context.getResources(), finalBmp), sample);
        } catch (Exception e) {
            return blackChip();
        }
    }

    private static ChipBackground blackChip() {
        return new ChipBackground(new ColorDrawable(Color.BLACK), Color.BLACK);
    }

    private static boolean isHomeWallpaperBitmapBacked(WallpaperManager wm) {
        WallpaperInfo info = wm.getWallpaperInfo(WallpaperManager.FLAG_SYSTEM);
        if (info == null) {
            return true;
        }
        ComponentName cn = info.getComponent();
        return SYSTEMUI_PACKAGE.equals(cn.getPackageName())
                && cn.getClassName().endsWith(IMAGE_WALLPAPER_SERVICE);
    }

    private static Bitmap ensureArgb8888(Bitmap bitmap) {
        if (bitmap.getConfig() == Bitmap.Config.HARDWARE) {
            Bitmap copy = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            if (copy != null) {
                if (copy != bitmap && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
                return copy;
            }
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            if (w > 0 && h > 0) {
                try {
                    Bitmap scaled = Bitmap.createScaledBitmap(bitmap, w, h, true);
                    if (scaled != bitmap && !bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                    return scaled;
                } catch (Exception ignored) {
                }
            }
        }
        return bitmap;
    }

    private static Bitmap decodeWallpaperWorkBitmap(Drawable full) {
        int w = full.getIntrinsicWidth();
        int h = full.getIntrinsicHeight();
        if (w <= 0 || h <= 0) {
            int tw = DECODE_MAX_EDGE_PX;
            int th = Math.max(8, (int) (tw / CROP_ASPECT_WIDTH_OVER_HEIGHT));
            return drawableToBitmap(full, tw, th);
        }
        float scale = Math.min((float) DECODE_MAX_EDGE_PX / w, (float) DECODE_MAX_EDGE_PX / h);
        int dw = Math.max(1, (int) (w * scale));
        int dh = Math.max(1, (int) (h * scale));
        return drawableToBitmap(full, dw, dh);
    }

    private static Bitmap drawableToBitmap(Drawable drawable, int width, int height) {
        Drawable d = drawable.mutate();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        d.setBounds(0, 0, width, height);
        d.draw(canvas);
        return bitmap;
    }

    private static Bitmap cropToChipAspect(Bitmap src, float aspectWidthOverHeight, boolean alignTop) {
        int W = src.getWidth();
        int H = src.getHeight();
        if (W <= 0 || H <= 0) {
            return src;
        }

        float srcAspect = (float) W / H;
        int cropW;
        int cropH;
        int left;
        int top;

        if (srcAspect > aspectWidthOverHeight) {
            cropH = H;
            cropW = Math.max(1, Math.min(W, (int) (H * aspectWidthOverHeight)));
            left = (W - cropW) / 2;
            top = 0;
        } else {
            cropW = W;
            cropH = Math.max(1, Math.min(H, (int) (W / aspectWidthOverHeight)));
            left = 0;
            top = alignTop ? 0 : Math.max(0, (H - cropH) / 2);
        }

        int safeW = Math.min(cropW, W - left);
        int safeH = Math.min(cropH, H - top);
        if (safeW <= 0 || safeH <= 0) {
            return src;
        }

        try {
            return Bitmap.createBitmap(src, left, top, safeW, safeH);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return src;
        }
    }

    private static Bitmap scaleToMaxEdgePreservingAspect(Bitmap bm, int maxEdgePx) {
        int w = bm.getWidth();
        int h = bm.getHeight();
        if (w <= 0 || h <= 0) {
            return bm;
        }
        int longEdge = Math.max(w, h);
        if (longEdge <= maxEdgePx) {
            return bm;
        }
        float scale = (float) maxEdgePx / longEdge;
        int nw = Math.max(1, (int) (w * scale));
        int nh = Math.max(1, (int) (h * scale));
        return Bitmap.createScaledBitmap(bm, nw, nh, true);
    }

    private static int sampleCenterArgbSafe(Bitmap bitmap) {
        try {
            int x = Math.max(0, Math.min(bitmap.getWidth() - 1, bitmap.getWidth() / 2));
            int y = Math.max(0, Math.min(bitmap.getHeight() - 1, bitmap.getHeight() / 2));
            return bitmap.getPixel(x, y);
        } catch (Exception e) {
            return Color.DKGRAY;
        }
    }

    private static final class NoIntrinsicSizeBitmapDrawable extends BitmapDrawable {
        NoIntrinsicSizeBitmapDrawable(Resources res, Bitmap bitmap) {
            super(res, bitmap);
        }

        @Override
        public int getIntrinsicWidth() {
            return -1;
        }

        @Override
        public int getIntrinsicHeight() {
            return -1;
        }
    }
}
