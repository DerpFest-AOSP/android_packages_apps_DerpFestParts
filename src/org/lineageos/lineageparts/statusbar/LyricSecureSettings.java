/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.statusbar;

import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class LyricSecureSettings {
    static final int POSITION_OVERLAY = 0;
    static final int POSITION_CLOCK_RIGHT = 1;

    private LyricSecureSettings() {
    }

    static int getPosition(Context context, int defaultValue) {
        return Settings.Secure.getInt(context.getContentResolver(),
                Settings.Secure.STATUS_BAR_LYRIC_POSITION, defaultValue);
    }

    static void setAllowedPackages(Context context, List<String> packages) {
        Settings.Secure.putString(context.getContentResolver(),
                Settings.Secure.STATUS_BAR_LYRIC_ALLOWED_PACKAGES,
                joinFiltered(packages, ";"));
    }

    static List<String> getAllowedPackages(Context context) {
        return splitAndFilter(Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.STATUS_BAR_LYRIC_ALLOWED_PACKAGES), ";");
    }

    static void addAllowedPackage(Context context, String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return;
        }
        List<String> packages = getAllowedPackages(context);
        if (!packages.contains(packageName)) {
            packages.add(packageName);
            setAllowedPackages(context, packages);
        }
    }

    static void removeAllowedPackage(Context context, String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return;
        }
        List<String> packages = getAllowedPackages(context);
        if (packages.remove(packageName)) {
            setAllowedPackages(context, packages);
        }
    }

    private static String joinFiltered(List<String> values, String delimiter) {
        StringBuilder builder = new StringBuilder();
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(delimiter);
            }
            builder.append(trimmed);
        }
        return builder.toString();
    }

    private static List<String> splitAndFilter(String value, String delimiter) {
        ArrayList<String> values = new ArrayList<>();
        if (TextUtils.isEmpty(value)) {
            return values;
        }
        for (String part : value.split(Pattern.quote(delimiter))) {
            if (part == null) {
                continue;
            }
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }
}
