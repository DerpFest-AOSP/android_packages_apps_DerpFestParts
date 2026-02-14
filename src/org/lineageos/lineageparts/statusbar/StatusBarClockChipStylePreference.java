/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.statusbar;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.ColorUtils;
import androidx.preference.Preference;

import org.lineageos.lineageparts.R;

import java.util.function.Consumer;

public class StatusBarClockChipStylePreference extends Preference {

    private static final int BACKGROUND_BLUR_RADIUS = 80;
    private static final int WINDOW_BG_ALPHA_WITH_BLUR = 105;
    private static final int WINDOW_BG_ALPHA_NO_BLUR = 255;
    private static final float DIM_AMOUNT_WITH_BLUR = 0.1f;
    private static final float DIM_AMOUNT_NO_BLUR = 0.4f;

    private Drawable mWindowBackgroundDrawable;
    private Drawable mDecorBackgroundDrawable;
    private Consumer<Boolean> mBlurEnabledListener;
    private AlertDialog mDialog;

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
    private android.content.pm.PackageManager mPm;

    public StatusBarClockChipStylePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mEntries = context.getResources().getStringArray(R.array.statusbar_clock_chip_entries);
        mEntryValues = context.getResources().getStringArray(R.array.statusbar_clock_chip_values);
        mPm = context.getPackageManager();
    }

    private Drawable getChipDrawableForStyle(int styleIndex) {
        if (styleIndex < 1 || styleIndex > SYSTEMUI_CHIP_DRAWABLES.length) {
            return null;
        }
        try {
            android.content.res.Resources sysUiRes = mPm.getResourcesForApplication(SYSTEMUI_PACKAGE);
            String name = SYSTEMUI_CHIP_DRAWABLES[styleIndex - 1];
            int id = sysUiRes.getIdentifier(name, "drawable", SYSTEMUI_PACKAGE);
            if (id != 0) {
                return sysUiRes.getDrawable(id, getContext().getTheme());
            }
        } catch (Exception e) {
        }
        return null;
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

    /** Returns contrasting text color for use on accent/chip background (matches SystemUI logic). */
    private int getTextColorOnAccent() {
        TypedValue tv = new TypedValue();
        if (!getContext().getTheme().resolveAttribute(android.R.attr.colorAccent, tv, true)) {
            return Color.WHITE;
        }
        int accent = tv.resourceId != 0 ? getContext().getColor(tv.resourceId) : tv.data;
        double luminance = ColorUtils.calculateLuminance(accent);
        return luminance > 0.5 ? Color.BLACK : Color.WHITE;
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
        View listView = root.findViewById(R.id.logo_style_list);
        View ourContentRoot = (listView != null && listView.getParent() instanceof View
                && ((View) listView.getParent()).getParent() instanceof View)
                ? (View) ((View) listView.getParent()).getParent() : null;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                clearOpaqueBackgroundsRecursive(group.getChildAt(i), ourContentRoot);
            }
        }
    }

    private void clearOpaqueBackgroundsRecursive(View view, View excludeSubtree) {
        if (view == excludeSubtree) return;
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

    @Override
    protected void onClick() {
        View view = View.inflate(getContext(), R.layout.dialog_statusbar_logo_style, null);
        ListView listView = view.findViewById(R.id.logo_style_list);

        int mCurrentValue = getCurrentValue();
        int selectedIndex = indexOfValue(String.valueOf(mCurrentValue));
        if (selectedIndex < 0) selectedIndex = 0;

        listView.setAdapter(new ChipStyleAdapter(getContext(), mEntries, mEntryValues, this,
                selectedIndex));
        listView.setOnItemClickListener((parent, v, position, id) -> {
            String value = mEntryValues[position];
            Settings.System.putIntForUser(getContext().getContentResolver(),
                    SETTING_KEY, Integer.parseInt(value), UserHandle.USER_CURRENT);
            setSummary(mEntries[position]);
            if (mDialog != null) {
                mDialog.dismiss();
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.LogoStyleDialogTheme);
        builder.setTitle(getTitle());
        builder.setView(view);
        builder.setNegativeButton(android.R.string.cancel, null);

        mDialog = builder.create();
        Window window = mDialog.getWindow();
        if (window != null) {
            float density = getContext().getResources().getDisplayMetrics().density;
            int maxWidthPx = (int) (320 * density + 0.5f);
            int screenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = Math.min(maxWidthPx, (int) (screenWidth * 0.85f));
            window.setAttributes(lp);
            setupWindowBlur(window);
        }
        mDialog.setOnShowListener(dialog -> {
            TypedValue tv = new TypedValue();
            int accent = 0;
            if (getContext().getTheme().resolveAttribute(android.R.attr.colorAccent, tv, true)) {
                accent = tv.resourceId != 0 ? getContext().getColor(tv.resourceId) : tv.data;
            }
            if (accent != 0) {
                android.widget.Button negativeButton =
                        ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEGATIVE);
                if (negativeButton != null) negativeButton.setTextColor(accent);
                int titleId = getContext().getResources().getIdentifier("alertTitle", "id",
                        "android");
                TextView titleView = titleId != 0
                        ? (TextView) ((AlertDialog) dialog).getWindow()
                                .getDecorView().findViewById(titleId) : null;
                if (titleView != null) titleView.setTextColor(accent);
            }
            Window w = ((AlertDialog) dialog).getWindow();
            if (w != null) clearDialogSolidBackgrounds(w.getDecorView());
        });
        mDialog.setOnDismissListener(dialog -> {
            if (mBlurEnabledListener != null && mDialog != null) {
                Window w = mDialog.getWindow();
                if (w != null) {
                    w.getWindowManager().removeCrossWindowBlurEnabledListener(mBlurEnabledListener);
                }
            }
            mBlurEnabledListener = null;
            mWindowBackgroundDrawable = null;
            mDecorBackgroundDrawable = null;
            mDialog = null;
        });

        mDialog.show();
    }

    private void setupWindowBlur(Window window) {
        if (window == null) return;
        mWindowBackgroundDrawable = getContext().getDrawable(
                R.drawable.dialog_logo_style_window_background);
        if (mWindowBackgroundDrawable != null) {
            mWindowBackgroundDrawable = mWindowBackgroundDrawable.mutate();
            window.setBackgroundDrawable(mWindowBackgroundDrawable);
        }
        mDecorBackgroundDrawable = getContext().getDrawable(
                R.drawable.dialog_logo_style_window_background);
        if (mDecorBackgroundDrawable != null) {
            mDecorBackgroundDrawable = mDecorBackgroundDrawable.mutate();
            View decor = window.getDecorView();
            if (decor != null) {
                decor.setBackground(mDecorBackgroundDrawable);
                decor.setClipToOutline(true);
            }
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setBackgroundBlurRadius(BACKGROUND_BLUR_RADIUS);
        window.setDimAmount(DIM_AMOUNT_WITH_BLUR);
        if (mWindowBackgroundDrawable != null) {
            mWindowBackgroundDrawable.setAlpha(WINDOW_BG_ALPHA_WITH_BLUR);
        }
        if (mDecorBackgroundDrawable != null) {
            mDecorBackgroundDrawable.setAlpha(WINDOW_BG_ALPHA_WITH_BLUR);
        }
        mBlurEnabledListener = this::updateWindowForBlur;
        window.getWindowManager().addCrossWindowBlurEnabledListener(mBlurEnabledListener);
        boolean enabled = window.getWindowManager().isCrossWindowBlurEnabled();
        updateWindowForBlur(enabled);
    }

    private void updateWindowForBlur(boolean blursEnabled) {
        if (mDialog == null) return;
        Window window = mDialog.getWindow();
        if (window == null) return;
        int alpha = blursEnabled && BACKGROUND_BLUR_RADIUS > 0
                ? WINDOW_BG_ALPHA_WITH_BLUR : WINDOW_BG_ALPHA_NO_BLUR;
        if (mWindowBackgroundDrawable != null) {
            mWindowBackgroundDrawable.setAlpha(alpha);
        }
        if (mDecorBackgroundDrawable != null) {
            mDecorBackgroundDrawable.setAlpha(alpha);
        }
        window.setDimAmount(blursEnabled && BACKGROUND_BLUR_RADIUS > 0
                ? DIM_AMOUNT_WITH_BLUR : DIM_AMOUNT_NO_BLUR);
        window.setBackgroundBlurRadius(BACKGROUND_BLUR_RADIUS);
        window.setAttributes(window.getAttributes());
    }

    private static class ChipStyleAdapter extends android.widget.BaseAdapter {
        private final LayoutInflater mInflater;
        private final String[] mEntries;
        private final String[] mEntryValues;
        private final StatusBarClockChipStylePreference mPreference;
        private final int mSelectedIndex;
        private final Drawable mSelectedBackground;

        ChipStyleAdapter(Context context, String[] entries, String[] entryValues,
                StatusBarClockChipStylePreference preference, int selectedIndex) {
            mInflater = LayoutInflater.from(context);
            mEntries = entries;
            mEntryValues = entryValues;
            mPreference = preference;
            mSelectedIndex = selectedIndex;
            mSelectedBackground = context.getDrawable(R.drawable.logo_style_item_selected);
        }

        @Override
        public int getCount() {
            return mEntries.length;
        }

        @Override
        public Object getItem(int position) {
            return mEntries[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.m3_chip_style_list_item, parent, false);
            }
            convertView.setBackground(position == mSelectedIndex
                    ? mSelectedBackground : null);
            TextView text = convertView.findViewById(android.R.id.text1);
            View chipContainer = convertView.findViewById(R.id.chip_preview_container);
            TextView timePreview = convertView.findViewById(R.id.chip_time_preview);
            text.setText(mEntries[position]);
            int styleIndex = position < mEntryValues.length
                    ? Integer.parseInt(mEntryValues[position]) : 0;
            Drawable chipBg = mPreference.getChipDrawableForStyle(styleIndex);
            if (styleIndex == 0) {
                chipContainer.setBackgroundResource(R.drawable.chip_preview_disabled);
                TypedValue tv = new TypedValue();
                if (mPreference.getContext().getTheme().resolveAttribute(
                        android.R.attr.textColorSecondary, tv, true)) {
                    timePreview.setTextColor(tv.resourceId != 0
                            ? mPreference.getContext().getColor(tv.resourceId) : tv.data);
                }
            } else {
                chipContainer.setBackground(chipBg);
                if (OUTLINE_CHIP_STYLES.contains(styleIndex)) {
                    TypedValue tv = new TypedValue();
                    if (mPreference.getContext().getTheme().resolveAttribute(
                            android.R.attr.textColorPrimary, tv, true)) {
                        timePreview.setTextColor(tv.resourceId != 0
                                ? mPreference.getContext().getColor(tv.resourceId) : tv.data);
                    }
                } else {
                    timePreview.setTextColor(mPreference.getTextColorOnAccent());
                }
            }
            chipContainer.setVisibility(View.VISIBLE);
            return convertView;
        }
    }
}
