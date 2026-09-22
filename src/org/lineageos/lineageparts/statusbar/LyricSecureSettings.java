/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.statusbar;

import android.content.Context;
import android.os.UserHandle;
import android.provider.Settings;

final class LyricSecureSettings {
    static final int POSITION_OVERLAY = 0;
    static final int POSITION_CLOCK_RIGHT = 1;

    private LyricSecureSettings() {
    }

    static int getPosition(Context context, int defaultValue) {
        return Settings.Secure.getInt(context.getContentResolver(),
                Settings.Secure.STATUS_BAR_LYRIC_POSITION, defaultValue);
    }

    static void setSources(Context context, String sources) {
        Settings.Secure.putStringForUser(context.getContentResolver(),
                Settings.Secure.STATUS_BAR_LYRIC_SOURCES,
                sources == null ? "" : sources.trim(),
                UserHandle.USER_CURRENT);
    }

    static String getSources(Context context) {
        String sources = Settings.Secure.getStringForUser(context.getContentResolver(),
                Settings.Secure.STATUS_BAR_LYRIC_SOURCES, UserHandle.USER_CURRENT);
        return sources == null ? "" : sources;
    }
}
