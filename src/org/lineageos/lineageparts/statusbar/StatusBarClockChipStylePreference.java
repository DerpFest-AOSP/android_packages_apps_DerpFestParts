/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.statusbar;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.ColorUtils;
import androidx.preference.Preference;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settingslib.Utils;

import org.lineageos.lineageparts.R;
import org.lineageos.lineageparts.widget.DialogSurfaceBlur;

public class StatusBarClockChipStylePreference extends Preference {

    private DialogSurfaceBlur mSurfaceBlur;
    private AlertDialog mDialog;
    private boolean mDialogConfirmed;
    private int mOriginalValue;

    private static final String SETTING_KEY = "statusbar_clock_chip";
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    /** Outline-only chip styles (transparent fill) - use primary text color for time preview */
    private static final java.util.Set<Integer> OUTLINE_CHIP_STYLES =
            java.util.Set.of(2, 8);

    private static final String[] SYSTEMUI_CHIP_DRAWABLES = {
        "sb_date_bg1",
        "sb_date_bg2",
        "sb_date_bg3",
        "sb_date_bg4",
        "sb_date_bg5",
        "sb_date_bg6",
        "sb_date_bg7",
        "sb_date_bg8",
        "sb_date_bg9",
        "sb_date_bg10",
        "sb_date_bg11",
        "sb_date_bg12",
    };

    private String[] mEntries;
    private String[] mEntryValues;
    private Context mSystemUiThemedContext;

    public StatusBarClockChipStylePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mEntries = context.getResources().getStringArray(R.array.statusbar_clock_chip_entries);
        mEntryValues = context.getResources().getStringArray(R.array.statusbar_clock_chip_values);
    }

    /**
     * SystemUI application theme ({@code Theme.SystemUI} / DeviceDefault.SystemUI), not the
     * Settings Material3 dialog theme. colorAccent there is what the status bar clock uses.
     */
    private Context getSystemUiThemedContext() {
        if (mSystemUiThemedContext != null) {
            return mSystemUiThemedContext;
        }
        try {
            Context sysUi = getContext().createPackageContext(SYSTEMUI_PACKAGE, 0);
            sysUi = sysUi.createConfigurationContext(
                    getContext().getResources().getConfiguration());
            int themeId = sysUi.getResources().getIdentifier(
                    "Theme.SystemUI", "style", SYSTEMUI_PACKAGE);
            mSystemUiThemedContext = new ContextThemeWrapper(sysUi,
                    themeId != 0 ? themeId : android.R.style.Theme_DeviceDefault);
            return mSystemUiThemedContext;
        } catch (PackageManager.NameNotFoundException e) {
            return getContext();
        }
    }

    private Resources getSystemUiResources() {
        return getSystemUiThemedContext().getResources();
    }

    private Drawable getChipDrawableForStyle(int styleIndex) {
        if (styleIndex < 1 || styleIndex > SYSTEMUI_CHIP_DRAWABLES.length) {
            return null;
        }
        try {
            Resources sysUiRes = getSystemUiResources();
            if (sysUiRes == null) {
                return null;
            }
            String name = SYSTEMUI_CHIP_DRAWABLES[styleIndex - 1];
            int id = sysUiRes.getIdentifier(name, "drawable", SYSTEMUI_PACKAGE);
            if (id != 0) {
                return sysUiRes.getDrawable(id, getSystemUiThemedContext().getTheme());
            }
        } catch (Exception e) {
        }
        return null;
    }

    private int getSystemUiColor(String name, int fallback) {
        try {
            Resources sysUiRes = getSystemUiResources();
            if (sysUiRes == null) {
                return fallback;
            }
            int id = sysUiRes.getIdentifier(name, "color", SYSTEMUI_PACKAGE);
            if (id != 0) {
                return sysUiRes.getColor(id, getSystemUiThemedContext().getTheme());
            }
        } catch (Exception e) {
        }
        return fallback;
    }

    private boolean useHighEndBatteryContrast() {
        try {
            Resources sysUiRes = getSystemUiResources();
            if (sysUiRes == null) {
                return true;
            }
            int id = sysUiRes.getIdentifier(
                    "config_useHighEndBatteryContrast", "bool", SYSTEMUI_PACKAGE);
            if (id != 0) {
                return sysUiRes.getBoolean(id);
            }
        } catch (Exception e) {
        }
        return true;
    }

    /**
     * Same background SystemUI uses for chip text contrast: colorAccent like the battery
     * glyph, with style 5 on neumorph paper and style 9 compositing the scrim.
     */
    private int getChipContrastBackground(int styleIndex) {
        int accent = Utils.getColorAccentDefaultColor(getSystemUiThemedContext());
        if (styleIndex == 5) {
            return ColorUtils.blendARGB(
                    getSystemUiColor("neumorph_outline_start", 0xFFBDBDBD),
                    getSystemUiColor("neumorph_outline_end", 0xFFF0F0F0),
                    0.5f);
        }
        if (styleIndex == 9) {
            return ColorUtils.compositeColors(
                    getSystemUiColor("clock_chip_overlay", 0x40000000), accent);
        }
        return accent;
    }

    /**
     * Matches {@code BatteryColors.textColorOnBackground}: blend toward black or white
     * until contrast is met (or 80% blend on low-end).
     */
    private int getTextColorOnBackground(int backgroundArgb) {
        boolean isBgLight = ColorUtils.calculateLuminance(backgroundArgb) > 0.5;
        int targetColor = isBgLight ? Color.BLACK : Color.WHITE;
        if (!useHighEndBatteryContrast()) {
            return ColorUtils.blendARGB(backgroundArgb, targetColor, 0.8f);
        }
        final double minContrast = 6.5;
        float blendRatio = 0f;
        while (blendRatio <= 1.0f) {
            int newColor = ColorUtils.blendARGB(backgroundArgb, targetColor, blendRatio);
            if (ColorUtils.calculateContrast(newColor, backgroundArgb) >= minContrast) {
                return newColor;
            }
            blendRatio += 0.05f;
        }
        return targetColor;
    }

    private int getChipPreviewTextColor(int styleIndex) {
        return getTextColorOnBackground(getChipContrastBackground(styleIndex));
    }

    private int getCurrentValue() {
        return Settings.System.getIntForUser(getContext().getContentResolver(),
                SETTING_KEY, 0, UserHandle.USER_CURRENT);
    }

    private void updateSummary() {
        int value = getCurrentValue();
        int index = indexOfValue(String.valueOf(value));
        setSummary(index >= 0 ? mEntries[index] : mEntries[0]);
    }

    private int indexOfValue(String value) {
        for (int i = 0; i < mEntryValues.length; i++) {
            if (mEntryValues[i].equals(value)) {
                return i;
            }
        }
        return -1;
    }

    private void clearDialogSolidBackgrounds(View root) {
        View grid = root.findViewById(R.id.style_picker_grid);
        View ourContentRoot = (grid != null && grid.getParent() instanceof View)
                ? (View) grid.getParent() : null;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                clearOpaqueBackgroundsRecursive(group.getChildAt(i), ourContentRoot);
            }
        }
    }

    private void clearOpaqueBackgroundsRecursive(View view, View excludeSubtree) {
        if (view == excludeSubtree) return;
        if (view instanceof android.widget.Button) return;
        view.setBackgroundResource(android.R.color.transparent);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                clearOpaqueBackgroundsRecursive(group.getChildAt(i), excludeSubtree);
            }
        }
    }

    @Override
    protected void onAttachedToHierarchy(androidx.preference.PreferenceManager pm) {
        super.onAttachedToHierarchy(pm);
        updateSummary();
    }

    private void applyStyleValue(int position) {
        Settings.System.putIntForUser(getContext().getContentResolver(),
                SETTING_KEY, Integer.parseInt(mEntryValues[position]), UserHandle.USER_CURRENT);
    }

    @Override
    protected void onClick() {
        View view = View.inflate(getContext(), R.layout.dialog_statusbar_logo_style, null);
        RecyclerView grid = view.findViewById(R.id.style_picker_grid);

        mOriginalValue = getCurrentValue();
        mDialogConfirmed = false;
        int selectedIndex = indexOfValue(String.valueOf(mOriginalValue));
        if (selectedIndex < 0) selectedIndex = 0;

        grid.setLayoutManager(new GridLayoutManager(getContext(), 2));
        grid.setHasFixedSize(true);
        int gap = getContext().getResources().getDimensionPixelSize(
                R.dimen.settingslib_expressive_space_extrasmall4);
        grid.addItemDecoration(new GridSpacingDecoration(gap));
        ChipStyleAdapter adapter = new ChipStyleAdapter(getContext(), mEntries, mEntryValues, this,
                selectedIndex);
        adapter.setOnStyleClickListener(position -> {
            adapter.setSelectedIndex(position);
            applyStyleValue(position);
        });
        grid.setAdapter(adapter);
        grid.scrollToPosition(selectedIndex);

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.LogoStyleDialogTheme);
        builder.setTitle(getTitle());
        builder.setView(view);
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            mDialogConfirmed = true;
            updateSummary();
        });

        mDialog = builder.create();
        Window window = mDialog.getWindow();
        if (window != null) {
            float density = getContext().getResources().getDisplayMetrics().density;
            int maxWidthPx = (int) (400 * density + 0.5f);
            int screenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = Math.min(maxWidthPx, (int) (screenWidth * 0.92f));
            window.setAttributes(lp);
        }
        mSurfaceBlur = new DialogSurfaceBlur(getContext());
        mDialog.setOnShowListener(dialog -> {
            if (mSurfaceBlur != null) {
                mSurfaceBlur.attach(mDialog);
            }
            Window w = ((AlertDialog) dialog).getWindow();
            if (w != null) clearDialogSolidBackgrounds(w.getDecorView());
        });
        mDialog.setOnDismissListener(dialog -> {
            if (!mDialogConfirmed) {
                Settings.System.putIntForUser(getContext().getContentResolver(),
                        SETTING_KEY, mOriginalValue, UserHandle.USER_CURRENT);
            }
            if (mSurfaceBlur != null) {
                mSurfaceBlur.detach();
                mSurfaceBlur = null;
            }
            mDialog = null;
        });

        mDialog.show();
    }

    private static class GridSpacingDecoration extends RecyclerView.ItemDecoration {
        private final int mSpacing;

        GridSpacingDecoration(int spacingPx) {
            mSpacing = spacingPx;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            outRect.set(mSpacing, mSpacing, mSpacing, mSpacing);
        }
    }

    private interface OnStyleClickListener {
        void onStyleClicked(int position);
    }

    private static class ChipStyleAdapter extends RecyclerView.Adapter<ChipStyleAdapter.Holder> {
        private final LayoutInflater mInflater;
        private final String[] mEntries;
        private final String[] mEntryValues;
        private final StatusBarClockChipStylePreference mPreference;
        private int mSelectedIndex;
        private OnStyleClickListener mListener;

        ChipStyleAdapter(Context context, String[] entries, String[] entryValues,
                StatusBarClockChipStylePreference preference, int selectedIndex) {
            mInflater = LayoutInflater.from(context);
            mEntries = entries;
            mEntryValues = entryValues;
            mPreference = preference;
            mSelectedIndex = selectedIndex;
        }

        void setOnStyleClickListener(OnStyleClickListener listener) {
            mListener = listener;
        }

        void setSelectedIndex(int index) {
            if (index == mSelectedIndex) {
                return;
            }
            int oldIndex = mSelectedIndex;
            mSelectedIndex = index;
            if (oldIndex >= 0) {
                notifyItemChanged(oldIndex);
            }
            notifyItemChanged(mSelectedIndex);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(mInflater.inflate(R.layout.m3_chip_style_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.itemView.setSelected(position == mSelectedIndex);
            holder.itemView.setContentDescription(mEntries[position]);
            holder.text.setText(mEntries[position]);
            int styleIndex = position < mEntryValues.length
                    ? Integer.parseInt(mEntryValues[position]) : 0;
            Drawable chipBg = mPreference.getChipDrawableForStyle(styleIndex);
            if (styleIndex == 0) {
                holder.chipContainer.setBackgroundResource(R.drawable.chip_preview_disabled);
                TypedValue tv = new TypedValue();
                if (mPreference.getContext().getTheme().resolveAttribute(
                        android.R.attr.textColorSecondary, tv, true)) {
                    holder.timePreview.setTextColor(tv.resourceId != 0
                            ? mPreference.getContext().getColor(tv.resourceId) : tv.data);
                }
            } else {
                holder.chipContainer.setBackground(chipBg);
                if (OUTLINE_CHIP_STYLES.contains(styleIndex)) {
                    TypedValue tv = new TypedValue();
                    if (mPreference.getContext().getTheme().resolveAttribute(
                            android.R.attr.textColorPrimary, tv, true)) {
                        holder.timePreview.setTextColor(tv.resourceId != 0
                                ? mPreference.getContext().getColor(tv.resourceId) : tv.data);
                    }
                } else {
                    holder.timePreview.setTextColor(mPreference.getChipPreviewTextColor(styleIndex));
                }
            }
            holder.itemView.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && mListener != null) {
                    mListener.onStyleClicked(pos);
                }
            });
        }

        @Override
        public int getItemCount() {
            return mEntries.length;
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView text;
            final View chipContainer;
            final TextView timePreview;

            Holder(@NonNull View itemView) {
                super(itemView);
                text = itemView.findViewById(android.R.id.text1);
                chipContainer = itemView.findViewById(R.id.chip_preview_container);
                timePreview = itemView.findViewById(R.id.chip_time_preview);
            }
        }
    }
}
