/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.statusbar;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.ArraySet;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import org.lineageos.lineageparts.R;
import org.lineageos.lineageparts.SettingsPreferenceFragment;
import org.lineageos.lineageparts.search.BaseSearchIndexProvider;
import org.lineageos.lineageparts.search.Searchable;

import java.util.Set;

public class StatusbarLyricSettings extends SettingsPreferenceFragment
        implements Searchable, Preference.OnPreferenceChangeListener {

    private static final String KEY_POSITION = "status_bar_lyric_position";
    private static final String KEY_HIDE_ICON_CLOCK_RIGHT =
            "status_bar_lyric_hide_icon_clock_right";
    private static final String KEY_SOURCES = "status_bar_lyric_sources";

    private ListPreference mPositionPreference;
    private SwitchPreferenceCompat mHideClockRightIconPreference;
    private Preference mSourcesPreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.status_bar_lyric_settings);

        mPositionPreference = findPreference(KEY_POSITION);
        mHideClockRightIconPreference = findPreference(KEY_HIDE_ICON_CLOCK_RIGHT);
        mSourcesPreference = findPreference(KEY_SOURCES);

        if (mPositionPreference != null) {
            mPositionPreference.setOnPreferenceChangeListener(this);
        }

        syncState();
    }

    @Override
    public void onResume() {
        super.onResume();
        syncState();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mPositionPreference) {
            updatePositionState(Integer.parseInt(String.valueOf(newValue)), true);
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (KEY_SOURCES.equals(preference.getKey())) {
            showSourcesDialog();
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void syncState() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        int position = LyricSecureSettings.getPosition(
                context, LyricSecureSettings.POSITION_OVERLAY);
        updatePositionState(position, true);
        updateSourcesSummary(context);
    }

    private void updatePositionState(int position, boolean enabled) {
        if (mPositionPreference != null) {
            mPositionPreference.setValue(String.valueOf(position));
            mPositionPreference.setSummary(position == LyricSecureSettings.POSITION_CLOCK_RIGHT
                    ? R.string.status_bar_lyric_position_summary_clock_right
                    : R.string.status_bar_lyric_position_summary_overlay);
        }
        if (mHideClockRightIconPreference != null) {
            mHideClockRightIconPreference.setEnabled(
                    enabled && position == LyricSecureSettings.POSITION_CLOCK_RIGHT);
        }
    }

    private void updateSourcesSummary(Context context) {
        if (mSourcesPreference == null) {
            return;
        }
        String sources = LyricSecureSettings.getSources(context);
        mSourcesPreference.setSummary(TextUtils.isEmpty(sources)
                ? R.string.status_bar_lyric_sources_summary_default
                : R.string.status_bar_lyric_sources_summary_configured);
    }

    private void showSourcesDialog() {
        Context context = requireContext();
        String current = LyricSecureSettings.getSources(context);

        AlertDialog.Builder alert = new AlertDialog.Builder(context);
        alert.setTitle(R.string.status_bar_lyric_sources_dialog_title);
        alert.setMessage(R.string.status_bar_lyric_sources_dialog_message);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        int margin = (int) (24 * context.getResources().getDisplayMetrics().density);
        lp.setMargins(margin, margin / 2, margin, margin / 2);

        final EditText input = new EditText(context);
        input.setText(current);
        input.setHint(R.string.status_bar_lyric_sources_dialog_hint);
        input.setSelection(input.getText().length());
        input.setMinLines(3);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_VARIATION_URI);
        input.setLayoutParams(lp);
        input.setGravity(Gravity.START | Gravity.TOP);
        container.addView(input);
        alert.setView(container);

        alert.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                LyricSecureSettings.setSources(context, input.getText().toString());
                updateSourcesSummary(context);
            }
        });
        alert.setNegativeButton(android.R.string.cancel, null);
        alert.show();
    }

    public static final Searchable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {

        @Override
        public Set<String> getNonIndexableKeys(Context context) {
            return new ArraySet<>();
        }
    };
}
