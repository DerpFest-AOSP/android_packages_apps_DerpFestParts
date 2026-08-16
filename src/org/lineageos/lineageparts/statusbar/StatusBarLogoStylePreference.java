/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.statusbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.lineageos.lineageparts.R;
import org.lineageos.lineageparts.widget.DialogSurfaceBlur;

public class StatusBarLogoStylePreference extends Preference {

    private DialogSurfaceBlur mSurfaceBlur;
    private AlertDialog mDialog;
    private boolean mDialogConfirmed;
    private int mOriginalValue;

    private static final String SETTING_KEY = "status_bar_logo_style";
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    private static final String[] SYSTEMUI_LOGO_DRAWABLES = {
        "ic_derp_logo",
        "ic_khloe_logo",
        "ic_kronic_logo",
        "ic_kronic2_logo",
        "ic_kronic3_logo",
        "ic_nest_logo",
        "ic_derp_alt_logo",
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
                return d != null ? d.mutate() : null;
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

        grid.setLayoutManager(new GridLayoutManager(getContext(), 4));
        grid.setHasFixedSize(true);
        int gap = getContext().getResources().getDimensionPixelSize(
                R.dimen.settingslib_expressive_space_extrasmall2);
        grid.addItemDecoration(new GridSpacingDecoration(gap));
        LogoStyleAdapter adapter = new LogoStyleAdapter(getContext(), mEntries, mEntryValues, this,
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

    private static class LogoStyleAdapter extends RecyclerView.Adapter<LogoStyleAdapter.Holder> {
        private final LayoutInflater mInflater;
        private final String[] mEntries;
        private final String[] mEntryValues;
        private final StatusBarLogoStylePreference mPreference;
        private int mSelectedIndex;
        private final ColorStateList mIconColors;
        private OnStyleClickListener mListener;

        LogoStyleAdapter(Context context, String[] entries, String[] entryValues,
                StatusBarLogoStylePreference preference, int selectedIndex) {
            mInflater = LayoutInflater.from(context);
            mEntries = entries;
            mEntryValues = entryValues;
            mPreference = preference;
            mSelectedIndex = selectedIndex;
            mIconColors = context.getColorStateList(R.color.style_picker_tile_icon);
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
            return new Holder(mInflater.inflate(R.layout.m3_logo_style_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            boolean selected = position == mSelectedIndex;
            holder.itemView.setSelected(selected);
            holder.itemView.setContentDescription(mEntries[position]);
            int styleIndex = position < mEntryValues.length
                    ? Integer.parseInt(mEntryValues[position]) : 0;
            Drawable d = mPreference.getLogoDrawableForStyle(styleIndex);
            holder.icon.setImageDrawable(d);
            holder.icon.setImageTintList(mIconColors);
            holder.icon.setVisibility(d != null ? View.VISIBLE : View.GONE);
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
            final ImageView icon;

            Holder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.logo_preview);
            }
        }
    }
}
