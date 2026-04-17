/*
 * SPDX-FileCopyrightText: 2014-2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.lineageparts.statusbar;

import static org.lineageos.lineageparts.utils.ResourceUtils.isRtlMode;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.widget.EditText;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.settingslib.fuelgauge.BatteryUtils;

import lineageos.preference.LineageSystemSettingListPreference;
import lineageos.providers.LineageSettings;

import org.derpfest.support.colorpicker.ColorPickerSystemPreference;
import org.derpfest.support.preferences.SystemSettingIntListPreference;
import org.derpfest.support.preferences.SystemSettingListPreference;
import org.lineageos.lineageparts.R;
import org.lineageos.lineageparts.SettingsPreferenceFragment;
import org.lineageos.lineageparts.utils.DeviceUtils;

import java.util.Date;

public class StatusBarSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String CATEGORY_BATTERY = "status_bar_battery_key";
    private static final String CATEGORY_CLOCK = "status_bar_clock_key";

    private static final String ICON_BLACKLIST = "icon_blacklist";

    private static final String STATUS_BAR_CLOCK_STYLE = "status_bar_clock";
    private static final String STATUS_BAR_AM_PM = "status_bar_am_pm";
    private static final String STATUS_BAR_BATTERY_STYLE = "status_bar_battery_style";
    private static final String STATUS_BAR_SHOW_BATTERY_PERCENT = "status_bar_show_battery_percent";
    private static final String STATUS_BAR_QUICK_QS_PULLDOWN = "qs_quick_pulldown";
    private static final String CLOCK_DATE_DISPLAY = "status_bar_clock_date_display";
    private static final String CLOCK_DATE_POSITION = "status_bar_clock_date_position";
    private static final String CLOCK_DATE_STYLE = "status_bar_clock_date_style";
    private static final String CLOCK_DATE_FORMAT = "status_bar_clock_date_format";

    private static final String STATUSBAR_ICON_TINT_MODE = "statusbar_icon_tint_mode";
    private static final String STATUSBAR_ICON_TINT_CUSTOM_COLOR = "statusbar_icon_tint_custom_color";
    /** @see android.provider.Settings.System#TINT_STATUSBAR_ICONS_WITH_ACCENT */
    private static final String TINT_STATUSBAR_ICONS_WITH_ACCENT = "tint_statusbar_icons_with_accent";

    private static final int STATUS_BAR_BATTERY_STYLE_TEXT = 2;
    private static final int CLOCK_DATE_STYLE_LOWERCASE = 1;
    private static final int CLOCK_DATE_STYLE_UPPERCASE = 2;
    private static final int CUSTOM_CLOCK_DATE_FORMAT_INDEX = 18;

    private static final int PULLDOWN_DIR_NONE = 0;
    private static final int PULLDOWN_DIR_RIGHT = 1;
    private static final int PULLDOWN_DIR_LEFT = 2;

    private LineageSystemSettingListPreference mQuickPulldown;
    private LineageSystemSettingListPreference mStatusBarClock;
    private LineageSystemSettingListPreference mStatusBarAmPm;
    private LineageSystemSettingListPreference mStatusBarBatteryShowPercent;
    private SystemSettingListPreference mClockDateDisplay;
    private SystemSettingListPreference mClockDatePosition;
    private SystemSettingListPreference mClockDateStyle;
    private ListPreference mClockDateFormat;

    private SystemSettingIntListPreference mStatusBarIconTintMode;
    private ColorPickerSystemPreference mStatusBarIconTintCustomColor;

    private PreferenceCategory mStatusBarBatteryCategory;
    private PreferenceCategory mStatusBarClockCategory;

    private boolean mBatteryPresent;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.status_bar_settings);

        maybeMigrateStatusBarIconTint();

        mStatusBarIconTintMode = findPreference(STATUSBAR_ICON_TINT_MODE);
        mStatusBarIconTintCustomColor = findPreference(STATUSBAR_ICON_TINT_CUSTOM_COLOR);
        if (mStatusBarIconTintMode != null) {
            mStatusBarIconTintMode.setOnPreferenceChangeListener(this);
        }
        updateStatusBarIconTintCustomColorEnabled();

        mStatusBarAmPm = findPreference(STATUS_BAR_AM_PM);
        mStatusBarClock = findPreference(STATUS_BAR_CLOCK_STYLE);

        mStatusBarClockCategory = getPreferenceScreen().findPreference(CATEGORY_CLOCK);

        ContentResolver resolver = getActivity().getContentResolver();
        int dateDisplay = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_CLOCK_DATE_DISPLAY, 0, UserHandle.USER_CURRENT);

        mClockDateDisplay = findPreference(CLOCK_DATE_DISPLAY);
        mClockDateDisplay.setOnPreferenceChangeListener(this);

        mClockDatePosition = findPreference(CLOCK_DATE_POSITION);
        mClockDatePosition.setEnabled(dateDisplay > 0);
        mClockDatePosition.setOnPreferenceChangeListener(this);

        mClockDateStyle = findPreference(CLOCK_DATE_STYLE);
        mClockDateStyle.setEnabled(dateDisplay > 0);
        mClockDateStyle.setOnPreferenceChangeListener(this);

        mClockDateFormat = findPreference(CLOCK_DATE_FORMAT);
        if (mClockDateFormat.getValue() == null) {
            mClockDateFormat.setValue("EEE");
        }
        parseClockDateFormats();
        mClockDateFormat.setEnabled(dateDisplay > 0);
        mClockDateFormat.setOnPreferenceChangeListener(this);

        mStatusBarBatteryShowPercent = findPreference(STATUS_BAR_SHOW_BATTERY_PERCENT);
        LineageSystemSettingListPreference statusBarBattery =
                findPreference(STATUS_BAR_BATTERY_STYLE);
        statusBarBattery.setOnPreferenceChangeListener(this);
        enableStatusBarBatteryDependents(statusBarBattery.getIntValue(2));

        Intent intent = BatteryUtils.getBatteryIntent(getContext());
        if (intent != null) {
            mBatteryPresent = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true);
        }
        mStatusBarBatteryCategory = getPreferenceScreen().findPreference(CATEGORY_BATTERY);

        mQuickPulldown = findPreference(STATUS_BAR_QUICK_QS_PULLDOWN);
        mQuickPulldown.setOnPreferenceChangeListener(this);
        updateQuickPulldownSummary(mQuickPulldown.getIntValue(0));
    }

    @Override
    public void onResume() {
        super.onResume();

        updateStatusBarIconTintCustomColorEnabled();

        final String curIconBlacklist = Settings.Secure.getString(getContext().getContentResolver(),
                ICON_BLACKLIST);

        if (TextUtils.delimitedStringContains(curIconBlacklist, ',', "clock")) {
            getPreferenceScreen().removePreference(mStatusBarClockCategory);
        } else {
            getPreferenceScreen().addPreference(mStatusBarClockCategory);
        }

        if (!mBatteryPresent ||
                TextUtils.delimitedStringContains(curIconBlacklist, ',', "battery")) {
            getPreferenceScreen().removePreference(mStatusBarBatteryCategory);
        } else {
            getPreferenceScreen().addPreference(mStatusBarBatteryCategory);
        }

        if (DateFormat.is24HourFormat(getActivity())) {
            mStatusBarAmPm.setEnabled(false);
            mStatusBarAmPm.setSummaryProvider(preference -> preference.getContext()
                    .getString(R.string.status_bar_am_pm_info));
        }

        int dateDisplay = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.STATUS_BAR_CLOCK_DATE_DISPLAY, 0, UserHandle.USER_CURRENT);
        if (mClockDatePosition != null) {
            mClockDatePosition.setEnabled(dateDisplay > 0);
        }
        if (mClockDateStyle != null) {
            mClockDateStyle.setEnabled(dateDisplay > 0);
        }
        if (mClockDateFormat != null) {
            mClockDateFormat.setEnabled(dateDisplay > 0);
            parseClockDateFormats();
        }

        final boolean disallowCenteredClock = DeviceUtils.hasCenteredCutout(getActivity());

        // Adjust status bar preferences for RTL
        if (isRtlMode(getResources())) {
            if (disallowCenteredClock) {
                mStatusBarClock.setEntries(R.array.status_bar_clock_position_entries_notch_rtl);
                mStatusBarClock.setEntryValues(R.array.status_bar_clock_position_values_notch);
            } else {
                mStatusBarClock.setEntries(R.array.status_bar_clock_position_entries_rtl);
                mStatusBarClock.setEntryValues(R.array.status_bar_clock_position_values);
            }
            mQuickPulldown.setEntries(R.array.status_bar_quick_qs_pulldown_entries_rtl);
        } else {
            if (disallowCenteredClock) {
                mStatusBarClock.setEntries(R.array.status_bar_clock_position_entries_notch);
                mStatusBarClock.setEntryValues(R.array.status_bar_clock_position_values_notch);
            } else {
                mStatusBarClock.setEntries(R.array.status_bar_clock_position_entries);
                mStatusBarClock.setEntryValues(R.array.status_bar_clock_position_values);
            }
            mQuickPulldown.setEntries(R.array.status_bar_quick_qs_pulldown_entries);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (preference == mStatusBarIconTintMode) {
            updateStatusBarIconTintCustomColorEnabled(Integer.parseInt((String) newValue));
            return true;
        }
        if (preference == mClockDateDisplay) {
            int val = Integer.parseInt((String) newValue);
            if (val == 0) {
                mClockDatePosition.setEnabled(false);
                mClockDateStyle.setEnabled(false);
                mClockDateFormat.setEnabled(false);
            } else {
                mClockDatePosition.setEnabled(true);
                mClockDateStyle.setEnabled(true);
                mClockDateFormat.setEnabled(true);
            }
            return true;
        } else if (preference == mClockDatePosition) {
            parseClockDateFormats();
            return true;
        } else if (preference == mClockDateStyle) {
            parseClockDateFormats();
            return true;
        } else if (preference == mClockDateFormat) {
            int index = mClockDateFormat.findIndexOfValue((String) newValue);

            if (index == CUSTOM_CLOCK_DATE_FORMAT_INDEX) {
                AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
                alert.setTitle(R.string.status_bar_date_string_edittext_title);
                alert.setMessage(R.string.status_bar_date_string_edittext_summary);

                final EditText input = new EditText(getActivity());
                String oldText = Settings.System.getString(
                    getActivity().getContentResolver(),
                    Settings.System.STATUS_BAR_CLOCK_DATE_FORMAT);
                if (oldText != null) {
                    input.setText(oldText);
                }
                alert.setView(input);

                alert.setPositiveButton(R.string.menu_save, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int whichButton) {
                        String value = input.getText().toString();
                        if (value.equals("")) {
                            return;
                        }
                        Settings.System.putString(getActivity().getContentResolver(),
                            Settings.System.STATUS_BAR_CLOCK_DATE_FORMAT, value);
                    }
                });

                alert.setNegativeButton(R.string.cancel,
                    new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int which) {
                        return;
                    }
                });
                alert.create().show();
            } else {
                if ((String) newValue != null) {
                    Settings.System.putString(getActivity().getContentResolver(),
                        Settings.System.STATUS_BAR_CLOCK_DATE_FORMAT, (String) newValue);
                }
            }
            return true;
        }

        int value = Integer.parseInt((String) newValue);
        switch (key) {
            case STATUS_BAR_QUICK_QS_PULLDOWN:
                updateQuickPulldownSummary(value);
                break;
            case STATUS_BAR_BATTERY_STYLE:
                enableStatusBarBatteryDependents(value);
                break;
        }
        return true;
    }

    private void enableStatusBarBatteryDependents(int batteryIconStyle) {
        mStatusBarBatteryShowPercent.setEnabled(batteryIconStyle != STATUS_BAR_BATTERY_STYLE_TEXT);
    }

    private void updateQuickPulldownSummary(int value) {
        String summary="";
        switch (value) {
            case PULLDOWN_DIR_NONE:
                summary = getResources().getString(
                    R.string.status_bar_quick_qs_pulldown_off);
                break;

            case PULLDOWN_DIR_LEFT:
            case PULLDOWN_DIR_RIGHT:
                summary = getResources().getString(
                    R.string.status_bar_quick_qs_pulldown_summary,
                    getResources().getString(
                        (value == PULLDOWN_DIR_LEFT) ^ isRtlMode(getResources())
                        ? R.string.status_bar_quick_qs_pulldown_summary_left
                        : R.string.status_bar_quick_qs_pulldown_summary_right));
                break;
        }
        mQuickPulldown.setSummary(summary);
    }

    /**
     * One-time migration from the legacy accent-only toggle to {@link #STATUSBAR_ICON_TINT_MODE}.
     */
    private void maybeMigrateStatusBarIconTint() {
        ContentResolver cr = getActivity().getContentResolver();
        final int unset = -1;
        int mode = Settings.System.getIntForUser(cr, STATUSBAR_ICON_TINT_MODE, unset,
                UserHandle.USER_CURRENT);
        if (mode != unset) {
            return;
        }
        boolean legacyAccent = Settings.System.getIntForUser(cr,
                TINT_STATUSBAR_ICONS_WITH_ACCENT, 0, UserHandle.USER_CURRENT) == 1;
        Settings.System.putIntForUser(cr, STATUSBAR_ICON_TINT_MODE,
                legacyAccent ? 1 : 0, UserHandle.USER_CURRENT);
    }

    private void updateStatusBarIconTintCustomColorEnabled() {
        if (mStatusBarIconTintMode == null || mStatusBarIconTintCustomColor == null) {
            return;
        }
        String v = mStatusBarIconTintMode.getValue();
        int mode = v != null ? Integer.parseInt(v) : 0;
        updateStatusBarIconTintCustomColorEnabled(mode);
    }

    private void updateStatusBarIconTintCustomColorEnabled(int mode) {
        if (mStatusBarIconTintCustomColor != null) {
            mStatusBarIconTintCustomColor.setEnabled(mode == 2);
        }
    }

    private void parseClockDateFormats() {
        String[] dateEntries = getResources().getStringArray(
                R.array.status_bar_date_format_entries_values);
        CharSequence parsedDateEntries[];
        parsedDateEntries = new String[dateEntries.length];
        Date now = new Date();

        int lastEntry = dateEntries.length - 1;
        int dateFormat = Settings.System.getIntForUser(getActivity()
                .getContentResolver(), Settings.System.STATUS_BAR_CLOCK_DATE_STYLE, 0, UserHandle.USER_CURRENT);
        for (int i = 0; i < dateEntries.length; i++) {
            if (i == lastEntry) {
                parsedDateEntries[i] = dateEntries[i];
            } else {
                String newDate;
                CharSequence dateString = DateFormat.format(dateEntries[i], now);
                if (dateFormat == CLOCK_DATE_STYLE_LOWERCASE) {
                    newDate = dateString.toString().toLowerCase();
                } else if (dateFormat == CLOCK_DATE_STYLE_UPPERCASE) {
                    newDate = dateString.toString().toUpperCase();
                } else {
                    newDate = dateString.toString();
                }

                parsedDateEntries[i] = newDate;
            }
        }
        mClockDateFormat.setEntries(parsedDateEntries);
    }

}
