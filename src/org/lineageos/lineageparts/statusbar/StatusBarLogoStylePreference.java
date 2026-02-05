/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.statusbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.util.TypedValue;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import org.lineageos.lineageparts.R;

import java.util.function.Consumer;

public class StatusBarLogoStylePreference extends Preference {

    private static final int BACKGROUND_BLUR_RADIUS = 80;
    private static final int WINDOW_BG_ALPHA_WITH_BLUR = 105;
    private static final int WINDOW_BG_ALPHA_NO_BLUR = 255;
    private static final float DIM_AMOUNT_WITH_BLUR = 0.1f;
    private static final float DIM_AMOUNT_NO_BLUR = 0.4f;

    private Drawable mWindowBackgroundDrawable;
    private Drawable mDecorBackgroundDrawable;
    private Consumer<Boolean> mBlurEnabledListener;
    private AlertDialog mDialog;

    private static final String SETTING_KEY = "status_bar_logo_style";
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    private static final String[] SYSTEMUI_LOGO_DRAWABLES = {
        "ic_derp_logo",
        "ic_android_logo",
        "ic_adidas",
        "ic_alien",
        "ic_apple_logo",
        "ic_avengers",
        "ic_batman",
        "ic_batman_tdk",
        "ic_beats",
        "ic_biohazard",
        "ic_blackberry",
        "ic_cannabis",
        "ic_emoticon_cool",
        "ic_emoticon_devil",
        "ic_fire",
        "ic_heart",
        "ic_nike",
        "ic_pac_man",
        "ic_puma",
        "ic_rog",
        "ic_spiderman",
        "ic_superman",
        "ic_windows",
        "ic_xbox",
        "ic_ghost",
        "ic_ninja",
        "ic_robot",
        "ic_ironman",
        "ic_captain_america",
        "ic_flash",
        "ic_tux_logo",
        "ic_ubuntu_logo",
        "ic_mint_logo",
    };

    private String[] mEntries;
    private String[] mEntryValues;
    private android.content.pm.PackageManager mPm;

    public StatusBarLogoStylePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mEntries = context.getResources().getStringArray(R.array.status_bar_logo_style_entries);
        mEntryValues = context.getResources().getStringArray(R.array.status_bar_logo_style_values);
        mPm = context.getPackageManager();
    }

    private int getThemeIconColor() {
        TypedValue tv = new TypedValue();
        if (getContext().getTheme().resolveAttribute(android.R.attr.colorControlNormal, tv, true)) {
            if (tv.resourceId != 0) {
                return getContext().getColor(tv.resourceId);
            }
            return tv.data;
        }
        if (getContext().getTheme().resolveAttribute(android.R.attr.textColorPrimary, tv, true)) {
            if (tv.resourceId != 0) {
                return getContext().getColor(tv.resourceId);
            }
            return tv.data;
        }
        return 0xff000000;
    }

    private Drawable getLogoDrawableForStyle(int styleIndex) {
        if (styleIndex < 0 || styleIndex >= SYSTEMUI_LOGO_DRAWABLES.length) {
            return null;
        }
        try {
            android.content.res.Resources sysUiRes = mPm.getResourcesForApplication(SYSTEMUI_PACKAGE);
            String name = SYSTEMUI_LOGO_DRAWABLES[styleIndex];
            int id = sysUiRes.getIdentifier(name, "drawable", SYSTEMUI_PACKAGE);
            if (id != 0) {
                Drawable d = sysUiRes.getDrawable(id, getContext().getTheme());
                if (d != null) {
                    d = d.mutate();
                    d.setTintList(ColorStateList.valueOf(getThemeIconColor()));
                }
                return d;
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
        View ourContentRoot = (listView != null && listView.getParent() instanceof View && ((View) listView.getParent()).getParent() instanceof View)
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

        listView.setAdapter(new LogoStyleAdapter(getContext(), mEntries, mEntryValues, this, selectedIndex));
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
                android.widget.Button negativeButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEGATIVE);
                if (negativeButton != null) negativeButton.setTextColor(accent);
                int titleId = getContext().getResources().getIdentifier("alertTitle", "id", "android");
                TextView titleView = titleId != 0 ? (TextView) ((AlertDialog) dialog).getWindow()
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
        mWindowBackgroundDrawable = getContext().getDrawable(R.drawable.dialog_logo_style_window_background);
        if (mWindowBackgroundDrawable != null) {
            mWindowBackgroundDrawable = mWindowBackgroundDrawable.mutate();
            window.setBackgroundDrawable(mWindowBackgroundDrawable);
        }
        mDecorBackgroundDrawable = getContext().getDrawable(R.drawable.dialog_logo_style_window_background);
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

    private static class LogoStyleAdapter extends android.widget.BaseAdapter {
        private final LayoutInflater mInflater;
        private final String[] mEntries;
        private final String[] mEntryValues;
        private final StatusBarLogoStylePreference mPreference;
        private final int mSelectedIndex;
        private final Drawable mSelectedBackground;

        LogoStyleAdapter(Context context, String[] entries, String[] entryValues,
                StatusBarLogoStylePreference preference, int selectedIndex) {
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
                convertView = mInflater.inflate(R.layout.m3_logo_style_list_item, parent, false);
            }
            convertView.setBackground(position == mSelectedIndex
                    ? mSelectedBackground : null);
            TextView text = convertView.findViewById(android.R.id.text1);
            android.widget.ImageView icon = convertView.findViewById(R.id.logo_preview);
            text.setText(mEntries[position]);
            int styleIndex = position < mEntryValues.length ? Integer.parseInt(mEntryValues[position]) : 0;
            Drawable d = mPreference.getLogoDrawableForStyle(styleIndex);
            icon.setImageDrawable(d);
            icon.setVisibility(d != null ? View.VISIBLE : View.GONE);
            return convertView;
        }
    }
}
