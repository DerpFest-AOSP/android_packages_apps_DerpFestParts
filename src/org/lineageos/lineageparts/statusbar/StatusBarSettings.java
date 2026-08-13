/*
 * SPDX-FileCopyrightText: 2014-2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.lineageparts.statusbar;

import static org.lineageos.lineageparts.utils.ResourceUtils.isRtlMode;

import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.settingslib.fuelgauge.BatteryUtils;

import lineageos.preference.LineageSecureSettingListPreference;
import lineageos.preference.LineageSecureSettingSwitchPreference;
import lineageos.preference.LineageSystemSettingListPreference;

import org.derpfest.support.colorpicker.ColorPickerSystemPreference;
import org.derpfest.support.preferences.SystemSettingIntListPreference;
import org.lineageos.lineageparts.R;
import org.lineageos.lineageparts.SettingsPreferenceFragment;
import org.lineageos.lineageparts.utils.DeviceUtils;

public class StatusBarSettings extends SettingsPreferenceFragment {

    private static final String CATEGORY_BATTERY = "status_bar_battery_key";
    private static final String CATEGORY_CLOCK = "status_bar_clock_key";

    private static final String ICON_BLACKLIST = "icon_blacklist";

    private static final String QS_BRIGHTNESS_SLIDER_POSITION = "qs_brightness_slider_position";
    private static final String QS_SHOW_AUTO_BRIGHTNESS = "qs_show_auto_brightness";
    private static final String QS_SHOW_BRIGHTNESS_SLIDER = "qs_show_brightness_slider";

    private static final String STATUS_BAR_CLOCK_STYLE = "status_bar_clock";
    private static final String STATUS_BAR_AM_PM = "status_bar_am_pm";
    private static final String STATUS_BAR_BATTERY_STYLE = "status_bar_battery_style";
    private static final String STATUS_BAR_SHOW_BATTERY_PERCENT = "status_bar_show_battery_percent";
    private static final String STATUS_BAR_QUICK_QS_PULLDOWN = "qs_quick_pulldown";

    private static final String CARRIER_NAME = "lockscreen_show_carrier";
    private static final String CUSTOM_CARRIER_LABEL = "lockscreen_show_custom_carrier_text";

    private static final String STATUSBAR_ICON_TINT_MODE = "statusbar_icon_tint_mode";
    private static final String STATUSBAR_ICON_TINT_CUSTOM_COLOR = "statusbar_icon_tint_custom_color";
    /** @see android.provider.Settings.System#TINT_STATUSBAR_ICONS_WITH_ACCENT */
    private static final String TINT_STATUSBAR_ICONS_WITH_ACCENT = "tint_statusbar_icons_with_accent";

    private static final int STATUS_BAR_BATTERY_STYLE_TEXT = 2;

    private static final int QS_BRIGHTNESS_SLIDER_HIDDEN = 0;

    private static final int PULLDOWN_DIR_NONE = 0;
    private static final int PULLDOWN_DIR_RIGHT = 1;
    private static final int PULLDOWN_DIR_LEFT = 2;

    private LineageSecureSettingListPreference mQsBrightnessSliderPosition;
    private LineageSecureSettingSwitchPreference mQsShowAutoBrightness;
    private LineageSystemSettingListPreference mQuickPulldown;
    private LineageSystemSettingListPreference mStatusBarClock;
    private LineageSystemSettingListPreference mStatusBarAmPm;
    private LineageSystemSettingListPreference mStatusBarBatteryShowPercent;

    private SystemSettingIntListPreference mStatusBarIconTintMode;
    private ColorPickerSystemPreference mStatusBarIconTintCustomColor;

    private PreferenceCategory mStatusBarBatteryCategory;
    private PreferenceCategory mStatusBarClockCategory;

    private Preference mCustomCarrierTextPref;
    private String mCustomCarrierText;

    private boolean mBatteryPresent;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.status_bar_settings);

        maybeMigrateStatusBarIconTint();

        mStatusBarIconTintMode = findPreference(STATUSBAR_ICON_TINT_MODE);
        mStatusBarIconTintCustomColor = findPreference(STATUSBAR_ICON_TINT_CUSTOM_COLOR);
        if (mStatusBarIconTintMode != null) {
            mStatusBarIconTintMode.setOnPreferenceChangeListener((preference, newValue) -> {
                updateStatusBarIconTintCustomColorEnabled(Integer.parseInt((String) newValue));
                return true;
            });
        }
        updateStatusBarIconTintCustomColorEnabled();

        mStatusBarAmPm = findPreference(STATUS_BAR_AM_PM);
        mStatusBarClock = findPreference(STATUS_BAR_CLOCK_STYLE);

        mStatusBarClockCategory = getPreferenceScreen().findPreference(CATEGORY_CLOCK);

        mStatusBarBatteryShowPercent = findPreference(STATUS_BAR_SHOW_BATTERY_PERCENT);
        LineageSystemSettingListPreference statusBarBattery =
                findPreference(STATUS_BAR_BATTERY_STYLE);
        statusBarBattery.setOnPreferenceChangeListener((preference, newValue) -> {
            enableStatusBarBatteryDependents(Integer.parseInt((String) newValue));
            return true;
        });
        enableStatusBarBatteryDependents(statusBarBattery.getIntValue(2));

        Intent intent = BatteryUtils.getBatteryIntent(getContext());
        if (intent != null) {
            mBatteryPresent = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true);
        }
        mStatusBarBatteryCategory = getPreferenceScreen().findPreference(CATEGORY_BATTERY);

        mQsShowAutoBrightness = findPreference(QS_SHOW_AUTO_BRIGHTNESS);
        mQsBrightnessSliderPosition = findPreference(QS_BRIGHTNESS_SLIDER_POSITION);
        LineageSecureSettingListPreference qsShowBrightnessSlider =
                findPreference(QS_SHOW_BRIGHTNESS_SLIDER);
        qsShowBrightnessSlider.setOnPreferenceChangeListener((preference, newValue) -> {
            enableQuickSettingsBrightnessSliderDependents(Integer.parseInt((String) newValue));
            return true;
        });
        enableQuickSettingsBrightnessSliderDependents(qsShowBrightnessSlider.getIntValue(1));

        mQuickPulldown = findPreference(STATUS_BAR_QUICK_QS_PULLDOWN);
        mQuickPulldown.setSummaryProvider(preference -> {
            int value = Integer.parseInt(
                    ((LineageSystemSettingListPreference) preference).getValue());
            Resources res = preference.getContext().getResources();

            switch (value) {
                case PULLDOWN_DIR_NONE:
                    return res.getString(R.string.status_bar_quick_qs_pulldown_off);
                case PULLDOWN_DIR_LEFT:
                case PULLDOWN_DIR_RIGHT:
                    int side = (value == PULLDOWN_DIR_LEFT) ^ isRtlMode(res)
                            ? R.string.status_bar_quick_qs_pulldown_summary_left
                            : R.string.status_bar_quick_qs_pulldown_summary_right;

                    return res.getString(R.string.status_bar_quick_qs_pulldown_summary,
                            res.getString(side));
            }
            return "";
        });

        mCustomCarrierTextPref = findPreference(CUSTOM_CARRIER_LABEL);
        updateCustomCarrierTextSummary();

        Preference carrierPref = findPreference(CARRIER_NAME);
        if (carrierPref != null) {
            carrierPref.setOnPreferenceChangeListener((preference, newValue) -> {
                updateCustomCarrierLabelEnabled(Integer.parseInt((String) newValue));
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        updateStatusBarIconTintCustomColorEnabled();
        updateCustomCarrierTextSummary();

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
    public boolean onPreferenceTreeClick(Preference preference) {
        if (CUSTOM_CARRIER_LABEL.equals(preference.getKey())) {
            final ContentResolver resolver = requireActivity().getContentResolver();

            AlertDialog.Builder alert = new AlertDialog.Builder(requireActivity());
            alert.setTitle(R.string.custom_carrier_label_title);
            alert.setMessage(R.string.custom_carrier_label_dialog_message);

            LinearLayout container = new LinearLayout(requireActivity());
            container.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            int margin = (int) (24 * requireActivity().getResources()
                    .getDisplayMetrics().density);
            lp.setMargins(margin, margin / 2, margin, margin / 2);

            final EditText input = new EditText(requireActivity());
            input.setText(TextUtils.isEmpty(mCustomCarrierText) ? "" : mCustomCarrierText);
            input.setSelection(input.getText().length());
            input.setLayoutParams(lp);
            input.setGravity(Gravity.START | Gravity.TOP);
            container.addView(input);
            alert.setView(container);

            alert.setPositiveButton(getString(android.R.string.ok),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String value = input.getText().toString();
                            Settings.System.putStringForUser(resolver,
                                    CUSTOM_CARRIER_LABEL, value, UserHandle.USER_CURRENT);
                            updateCustomCarrierTextSummary();
                        }
                    });
            alert.setNegativeButton(getString(android.R.string.cancel), null);
            alert.show();
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void updateCustomCarrierTextSummary() {
        if (mCustomCarrierTextPref == null) return;
        mCustomCarrierText = Settings.System.getStringForUser(
                requireActivity().getContentResolver(),
                CUSTOM_CARRIER_LABEL, UserHandle.USER_CURRENT);
        if (TextUtils.isEmpty(mCustomCarrierText)) {
            mCustomCarrierTextPref.setSummary(R.string.custom_carrier_label_summary);
        } else {
            mCustomCarrierTextPref.setSummary(mCustomCarrierText);
        }
        int showCarrier = Settings.System.getIntForUser(
                requireActivity().getContentResolver(),
                CARRIER_NAME, 1, UserHandle.USER_CURRENT);
        updateCustomCarrierLabelEnabled(showCarrier);
    }

    private void updateCustomCarrierLabelEnabled(int showCarrier) {
        if (mCustomCarrierTextPref != null) {
            mCustomCarrierTextPref.setEnabled(showCarrier != 0);
        }
    }

    private void enableQuickSettingsBrightnessSliderDependents(int showBrightnessSlider) {
        boolean enabled = showBrightnessSlider != QS_BRIGHTNESS_SLIDER_HIDDEN;

        mQsBrightnessSliderPosition.setEnabled(enabled);
        mQsShowAutoBrightness.setEnabled(enabled);
    }

    private void enableStatusBarBatteryDependents(int batteryIconStyle) {
        mStatusBarBatteryShowPercent.setEnabled(batteryIconStyle != STATUS_BAR_BATTERY_STYLE_TEXT);
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
}
