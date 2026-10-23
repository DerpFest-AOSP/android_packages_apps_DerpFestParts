/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.statusbar;

import android.content.Context;
import android.os.Bundle;
import android.util.ArraySet;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;
import androidx.preference.TwoStatePreference;

import org.lineageos.lineageparts.R;
import org.lineageos.lineageparts.SettingsPreferenceFragment;
import org.lineageos.lineageparts.search.BaseSearchIndexProvider;
import org.lineageos.lineageparts.search.Searchable;

import java.util.Set;

public class StatusbarTickerSettings extends SettingsPreferenceFragment
        implements Searchable, Preference.OnPreferenceChangeListener {

    private static final String KEY_SHOW_LYRIC = "status_bar_show_lyric";
    private static final String KEY_POSITION = "status_bar_lyric_position";
    private static final String KEY_HIDE_ICON_CLOCK_RIGHT =
            "status_bar_lyric_hide_icon_clock_right";

    private TwoStatePreference mShowLyricPreference;
    private ListPreference mPositionPreference;
    private SwitchPreferenceCompat mHideClockRightIconPreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.status_bar_ticker_settings);

        mShowLyricPreference = findPreference(KEY_SHOW_LYRIC);
        mPositionPreference = findPreference(KEY_POSITION);
        mHideClockRightIconPreference = findPreference(KEY_HIDE_ICON_CLOCK_RIGHT);

        if (mShowLyricPreference != null) {
            mShowLyricPreference.setOnPreferenceChangeListener(this);
        }
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
            updatePositionState(Integer.parseInt(String.valueOf(newValue)), isTickerEnabled());
            return true;
        }
        if (preference == mShowLyricPreference) {
            updatePositionState(currentPosition(), Boolean.TRUE.equals(newValue));
            return true;
        }
        return false;
    }

    private void syncState() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        int position = LyricSecureSettings.getPosition(
                context, LyricSecureSettings.POSITION_OVERLAY);
        updatePositionState(position, isTickerEnabled());
    }

    private boolean isTickerEnabled() {
        return mShowLyricPreference != null && mShowLyricPreference.isChecked();
    }

    private int currentPosition() {
        if (mPositionPreference == null || mPositionPreference.getValue() == null) {
            return LyricSecureSettings.POSITION_OVERLAY;
        }
        return Integer.parseInt(mPositionPreference.getValue());
    }

    private void updatePositionState(int position, boolean tickerEnabled) {
        if (mPositionPreference != null) {
            mPositionPreference.setValue(String.valueOf(position));
            mPositionPreference.setSummary(position == LyricSecureSettings.POSITION_CLOCK_RIGHT
                    ? R.string.status_bar_lyric_position_summary_clock_right
                    : R.string.status_bar_lyric_position_summary_overlay);
        }
        if (mHideClockRightIconPreference != null) {
            mHideClockRightIconPreference.setEnabled(
                    tickerEnabled && position == LyricSecureSettings.POSITION_CLOCK_RIGHT);
        }
    }

    public static final Searchable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {

        @Override
        public Set<String> getNonIndexableKeys(Context context) {
            return new ArraySet<>();
        }
    };
}
