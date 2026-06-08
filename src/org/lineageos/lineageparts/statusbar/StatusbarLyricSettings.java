/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.statusbar;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArraySet;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import org.lineageos.lineageparts.R;
import org.lineageos.lineageparts.SettingsPreferenceFragment;
import org.lineageos.lineageparts.search.BaseSearchIndexProvider;
import org.lineageos.lineageparts.search.Searchable;

import java.util.List;
import java.util.Set;

public class StatusbarLyricSettings extends SettingsPreferenceFragment
        implements Searchable, Preference.OnPreferenceChangeListener {

    private static final String KEY_POSITION = "status_bar_lyric_position";
    private static final String KEY_HIDE_ICON_CLOCK_RIGHT =
            "status_bar_lyric_hide_icon_clock_right";
    private static final String KEY_NOTIFICATION_ACCESS = "lyric_notification_listener";
    private static final String KEY_ALLOWED_APPS = "status_bar_lyric_allowed_packages";
    private static final String LYRIC_FETCH_PACKAGE = "cn.binbin323.statuslyricext";

    private ListPreference mPositionPreference;
    private SwitchPreferenceCompat mHideClockRightIconPreference;
    private Preference mNotificationListenerPreference;
    private Preference mAllowedAppsPreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.status_bar_lyric_settings);

        mPositionPreference = findPreference(KEY_POSITION);
        mHideClockRightIconPreference = findPreference(KEY_HIDE_ICON_CLOCK_RIGHT);
        mNotificationListenerPreference = findPreference(KEY_NOTIFICATION_ACCESS);
        mAllowedAppsPreference = findPreference(KEY_ALLOWED_APPS);

        if (mPositionPreference != null) {
            mPositionPreference.setOnPreferenceChangeListener(this);
        }

        if (mNotificationListenerPreference != null) {
            mNotificationListenerPreference.setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                return true;
            });
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

    private void syncState() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        int position = LyricSecureSettings.getPosition(
                context, LyricSecureSettings.POSITION_OVERLAY);
        updatePositionState(position, true);
        updateNotificationAccessSummary(context);
        updateAllowedAppsSummary(context);
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

    private void updateNotificationAccessSummary(Context context) {
        if (mNotificationListenerPreference == null) {
            return;
        }
        mNotificationListenerPreference.setSummary(
                isNotificationListenerEnabled(context)
                        ? R.string.status_bar_lyric_notification_listener_summary_on
                        : R.string.status_bar_lyric_notification_listener_summary_off);
    }

    private void updateAllowedAppsSummary(Context context) {
        if (mAllowedAppsPreference == null) {
            return;
        }
        List<String> packages = LyricSecureSettings.getAllowedPackages(context);
        if (packages.isEmpty()) {
            mAllowedAppsPreference.setSummary(R.string.status_bar_lyric_whitelist_summary_empty);
            return;
        }
        mAllowedAppsPreference.setSummary(getResources().getQuantityString(
                R.plurals.status_bar_lyric_whitelist_summary_count,
                packages.size(), packages.size()));
    }

    private boolean isNotificationListenerEnabled(Context context) {
        String flat = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_NOTIFICATION_LISTENERS);
        if (TextUtils.isEmpty(flat)) {
            return false;
        }
        for (String name : flat.split(":")) {
            ComponentName componentName = ComponentName.unflattenFromString(name);
            if (componentName != null
                    && LYRIC_FETCH_PACKAGE.equals(componentName.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    public static final Searchable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {

        @Override
        public Set<String> getNonIndexableKeys(Context context) {
            return new ArraySet<>();
        }
    };
}
