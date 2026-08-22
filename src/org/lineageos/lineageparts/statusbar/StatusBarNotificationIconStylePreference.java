/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.statusbar;

import android.app.INotificationManager;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.lineageos.lineageparts.R;
import org.lineageos.lineageparts.widget.DialogSurfaceBlur;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class StatusBarNotificationIconStylePreference extends Preference {

    static final String SETTING_KEY = "statusbar_notification_icon_mode";
    static final String COMBINED_COUNT_KEY = "statusbar_combined_notif_count";
    static final String COLORED_ICONS_KEY = "statusbar_colored_icons";
    static final String PINKBEAN_ICONS_KEY = "statusbar_pinkbean_notification_icons";

    static final int MODE_ICONS = 0;
    static final int MODE_COUNT = 1;
    static final int MODE_HIDDEN = 2;
    static final int MODE_PINK_BEAN = 3;
    static final int MODE_APP_ICONS = 4;

    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final String PINKBEAN_ASSET_DIR = "pinkbean_icons";
    private static final int PREVIEW_COUNT = 3;

    private static final String[] PREFERRED_PACKAGES = {
        "com.google.android.googlequicksearchbox",
        "com.google.android.gm",
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.whatsapp",
        "org.telegram.messenger",
        "com.instagram.android",
        "com.google.android.dialer",
        "com.android.dialer",
        "com.google.android.youtube",
        "com.android.chrome",
        "com.google.android.calendar",
        "com.spotify.music",
        "com.google.android.apps.maps",
    };

    private static final String[] EXCLUDED_PACKAGES = {
        "com.android.settings",
        "com.android.settings.intelligence",
        "com.android.providers.downloads",
        "com.android.providers.downloads.ui",
        "com.android.documentsui",
        "com.google.android.apps.nbu.files",
        "com.android.systemui",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.android.vending",
        "org.lineageos.lineageparts",
        "android",
    };

    private DialogSurfaceBlur mSurfaceBlur;
    private AlertDialog mDialog;
    private boolean mDialogConfirmed;
    private int mOriginalValue;

    private String[] mEntries;
    private String[] mEntryValues;

    public StatusBarNotificationIconStylePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mEntries = context.getResources().getStringArray(
                R.array.status_bar_notification_icon_mode_entries);
        mEntryValues = context.getResources().getStringArray(
                R.array.status_bar_notification_icon_mode_values);
    }

    static int readMode(ContentResolver cr) {
        int mode = Settings.System.getIntForUser(cr, SETTING_KEY, -1, UserHandle.USER_CURRENT);
        if (mode >= 0) {
            return mode;
        }
        if (Settings.System.getIntForUser(cr, COMBINED_COUNT_KEY, 0, UserHandle.USER_CURRENT) == 1) {
            return MODE_COUNT;
        }
        if (Settings.System.getIntForUser(cr, PINKBEAN_ICONS_KEY, 0, UserHandle.USER_CURRENT) == 1) {
            return MODE_PINK_BEAN;
        }
        if (Settings.System.getIntForUser(cr, COLORED_ICONS_KEY, 0, UserHandle.USER_CURRENT) == 1) {
            return MODE_APP_ICONS;
        }
        return MODE_ICONS;
    }

    static void writeMode(ContentResolver cr, int mode) {
        Settings.System.putIntForUser(cr, SETTING_KEY, mode, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(cr, COMBINED_COUNT_KEY,
                mode == MODE_COUNT ? 1 : 0, UserHandle.USER_CURRENT);
        boolean pinkBean = mode == MODE_PINK_BEAN;
        boolean appIcons = pinkBean || mode == MODE_APP_ICONS;
        Settings.System.putIntForUser(cr, COLORED_ICONS_KEY,
                appIcons ? 1 : 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(cr, PINKBEAN_ICONS_KEY,
                pinkBean ? 1 : 0, UserHandle.USER_CURRENT);
    }

    private int getCurrentValue() {
        return readMode(getContext().getContentResolver());
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
        writeMode(getContext().getContentResolver(), Integer.parseInt(mEntryValues[position]));
    }

    @Override
    protected void onClick() {
        View view = View.inflate(getContext(), R.layout.dialog_statusbar_notif_icon_style, null);
        RecyclerView grid = view.findViewById(R.id.style_picker_grid);

        mOriginalValue = getCurrentValue();
        mDialogConfirmed = false;
        int selectedIndex = indexOfValue(String.valueOf(mOriginalValue));
        if (selectedIndex < 0) selectedIndex = 0;

        grid.setLayoutManager(new LinearLayoutManager(getContext()));
        grid.setHasFixedSize(true);
        int gap = getContext().getResources().getDimensionPixelSize(
                R.dimen.settingslib_expressive_space_extrasmall4);
        grid.addItemDecoration(new ListSpacingDecoration(gap));
        NotifIconStyleAdapter adapter = new NotifIconStyleAdapter(
                getContext(), mEntries, mEntryValues, selectedIndex, loadPreviewSet());
        adapter.setOnStyleClickListener(position -> {
            adapter.setSelectedIndex(position);
            applyStyleValue(position);
        });
        grid.setAdapter(adapter);

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
                writeMode(getContext().getContentResolver(), mOriginalValue);
            }
            if (mSurfaceBlur != null) {
                mSurfaceBlur.detach();
                mSurfaceBlur = null;
            }
            mDialog = null;
        });

        mDialog.show();
    }

    private PreviewSet loadPreviewSet() {
        Context context = getContext();
        PackageManager pm = context.getPackageManager();
        Context sysUi = getSystemUiContext();
        Map<String, Icon> postedSmallIcons = loadPostedSmallIcons(pm);
        List<String> packages = pickPreviewPackages(pm, sysUi);
        int size = Math.round(26f * context.getResources().getDisplayMetrics().density);
        int tint = resolvePreviewTint(context);

        PreviewSet set = new PreviewSet();
        for (int i = 0; i < PREVIEW_COUNT; i++) {
            String pkg = i < packages.size() ? packages.get(i) : null;
            Drawable appIcon = pkg != null ? loadAppIcon(pm, pkg) : null;
            Bitmap appBmp = appIcon != null
                    ? toCircularBitmap(independentCopy(appIcon), size)
                    : toCircularBitmap(fallbackDefaultIcon(context, i), size);
            Drawable beanSrc = pkg != null ? loadPinkBeanIcon(sysUi, pkg) : null;
            Bitmap beanBmp = beanSrc != null
                    ? toCircularBitmap(beanSrc, size)
                    : copyBitmap(appBmp);
            Bitmap glyphBmp = pkg != null
                    ? loadShowIconBitmap(pm, pkg, postedSmallIcons.get(pkg), size, tint)
                    : null;
            if (glyphBmp == null) {
                glyphBmp = bakeStatusBarTint(
                        renderGlyph(fallbackDefaultIcon(context, i), size, false), tint);
            }
            glyphBmp = toCircularBitmap(glyphBmp);
            set.appIcons[i] = new BitmapDrawable(context.getResources(), appBmp);
            set.beanIcons[i] = new BitmapDrawable(context.getResources(), beanBmp);
            set.defaultIcons[i] = new BitmapDrawable(context.getResources(), glyphBmp);
        }
        return set;
    }

    private List<String> pickPreviewPackages(PackageManager pm, Context sysUi) {
        Set<String> ordered = new LinkedHashSet<>();
        for (String pkg : PREFERRED_PACKAGES) {
            if (isUsablePreviewPackage(pm, pkg)) {
                ordered.add(pkg);
            }
        }
        if (sysUi != null) {
            try {
                String[] assets = sysUi.getAssets().list(PINKBEAN_ASSET_DIR);
                if (assets != null) {
                    for (String asset : assets) {
                        if (asset != null && asset.endsWith(".png")) {
                            String pkg = asset.substring(0, asset.length() - 4);
                            if (isUsablePreviewPackage(pm, pkg)) {
                                ordered.add(pkg);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launchers = pm.queryIntentActivities(launcher, 0);
        for (ResolveInfo info : launchers) {
            if (info.activityInfo == null) {
                continue;
            }
            String pkg = info.activityInfo.packageName;
            if (isUsablePreviewPackage(pm, pkg)) {
                ordered.add(pkg);
            }
        }
        List<String> picked = new ArrayList<>(PREVIEW_COUNT);
        for (String pkg : ordered) {
            if (loadAppIcon(pm, pkg) == null) {
                continue;
            }
            picked.add(pkg);
            if (picked.size() == PREVIEW_COUNT) {
                break;
            }
        }
        return picked;
    }

    private boolean isUsablePreviewPackage(PackageManager pm, String pkg) {
        if (pkg == null || contextIsSelf(pkg) || isExcludedPackage(pkg)) {
            return false;
        }
        return isPackageInstalled(pm, pkg);
    }

    private static boolean isExcludedPackage(String pkg) {
        for (String excluded : EXCLUDED_PACKAGES) {
            if (excluded.equals(pkg)) {
                return true;
            }
        }
        return pkg.contains("providers.downloads") || pkg.endsWith(".settings");
    }

    private boolean contextIsSelf(String pkg) {
        return getContext().getPackageName().equals(pkg);
    }

    private static boolean isPackageInstalled(PackageManager pm, String pkg) {
        try {
            pm.getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private Context getSystemUiContext() {
        try {
            return getContext().createPackageContext(SYSTEMUI_PACKAGE,
                    Context.CONTEXT_IGNORE_SECURITY);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static Drawable loadAppIcon(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationIcon(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    private Drawable loadPinkBeanIcon(Context sysUi, String pkg) {
        if (sysUi == null || pkg == null) {
            return null;
        }
        try (InputStream is = sysUi.getAssets().open(PINKBEAN_ASSET_DIR + "/" + pkg + ".png")) {
            Bitmap bmp = BitmapFactory.decodeStream(is);
            if (bmp == null) {
                return null;
            }
            return new BitmapDrawable(getContext().getResources(), bmp);
        } catch (Exception e) {
            return null;
        }
    }

    private static Drawable independentCopy(Drawable src) {
        if (src == null) {
            return null;
        }
        Drawable.ConstantState state = src.getConstantState();
        if (state != null) {
            return state.newDrawable().mutate();
        }
        return src.mutate();
    }

    private static Drawable fallbackDefaultIcon(Context context, int index) {
        int[] fallbacks = {
            R.drawable.ic_notif_preview_mail,
            R.drawable.ic_notif_preview_chat,
            R.drawable.ic_notif_preview_weather,
        };
        return context.getDrawable(fallbacks[index % fallbacks.length]);
    }

    private static int resolvePreviewTint(Context context) {
        TypedValue tv = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.textColorPrimary, tv, true)) {
            return tv.resourceId != 0 ? context.getColor(tv.resourceId) : tv.data;
        }
        return Color.WHITE;
    }

    /**
     * Posted notification smallIcons, in the same order SystemUI would draw them.
     * Active first, then recently cleared, one icon per package.
     */
    private Map<String, Icon> loadPostedSmallIcons(PackageManager pm) {
        LinkedHashMap<String, Icon> byPkg = new LinkedHashMap<>();
        try {
            INotificationManager nm = NotificationManager.getService();
            if (nm == null) {
                return byPkg;
            }
            String callingPkg = getContext().getPackageName();
            collectPostedSmallIcons(byPkg, pm, nm.getActiveNotifications(callingPkg));
            if (byPkg.size() < PREVIEW_COUNT) {
                collectPostedSmallIcons(byPkg, pm,
                        nm.getHistoricalNotifications(callingPkg, 40, false));
            }
        } catch (Throwable ignored) {
        }
        return byPkg;
    }

    private void collectPostedSmallIcons(Map<String, Icon> byPkg, PackageManager pm,
            StatusBarNotification[] notifications) {
        if (notifications == null) {
            return;
        }
        for (StatusBarNotification sbn : notifications) {
            if (sbn == null || sbn.getNotification() == null) {
                continue;
            }
            if (sbn.getNotification().isGroupSummary()) {
                continue;
            }
            String pkg = sbn.getPackageName();
            if (!isUsablePreviewPackage(pm, pkg) || byPkg.containsKey(pkg)) {
                continue;
            }
            Icon icon = sbn.getNotification().getSmallIcon();
            if (icon == null) {
                continue;
            }
            byPkg.put(pkg, icon);
        }
    }

    private Bitmap loadShowIconBitmap(PackageManager pm, String pkg, Icon postedSmallIcon,
            int size, int tint) {
        Drawable glyph = findAppNotificationSmallIcon(pm, pkg);
        if (glyph == null && postedSmallIcon != null) {
            glyph = loadIconDrawable(pkg, postedSmallIcon);
        }
        if (glyph != null) {
            return bakeStatusBarTint(renderGlyph(glyph, size, false), tint);
        }
        return loadStatusBarGlyphBitmap(pm, pkg, size, tint);
    }

    /**
     * The notification smallIcon this app ships — available on first boot, no post required.
     * Prefers the FCM/manifest default, then notification drawables in the APK.
     */
    private static Drawable findAppNotificationSmallIcon(PackageManager pm, String pkg) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA);
            Resources res = pm.getResourcesForApplication(info);
            Drawable fromMeta = loadMetaNotificationIcon(info, res);
            if (fromMeta != null) {
                return fromMeta;
            }

            LinkedHashSet<String> names = new LinkedHashSet<>();
            for (String name : KNOWN_SMALL_ICON_NAMES) {
                names.add(name);
            }
            collectApkNotificationIconNames(info, names);

            List<String> ranked = new ArrayList<>(names);
            ranked.sort((a, b) -> {
                int score = Integer.compare(
                        scoreNotificationIconName(b), scoreNotificationIconName(a));
                return score != 0 ? score : Integer.compare(a.length(), b.length());
            });
            for (String name : ranked) {
                if (scoreNotificationIconName(name) <= 0) {
                    continue;
                }
                Drawable d = loadNamedDrawable(res, pkg, name);
                if (d != null && looksLikeGlyph(d)) {
                    return d;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static final String[] META_NOTIFICATION_ICON_KEYS = {
        "com.google.firebase.messaging.default_notification_icon",
        "com.google.android.gms.cloudmessaging.notification_icon",
        "default_notification_icon",
    };

    private static Drawable loadMetaNotificationIcon(ApplicationInfo info, Resources res) {
        if (info.metaData == null) {
            return null;
        }
        for (String key : META_NOTIFICATION_ICON_KEYS) {
            int id = info.metaData.getInt(key, 0);
            if (id == 0) {
                continue;
            }
            try {
                Drawable d = independentCopy(res.getDrawable(id, null));
                if (d != null && looksLikeGlyph(d)) {
                    return d;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static void collectApkNotificationIconNames(ApplicationInfo info, Set<String> names) {
        List<String> apks = new ArrayList<>();
        if (info.sourceDir != null) {
            apks.add(info.sourceDir);
        }
        if (info.splitSourceDirs != null) {
            for (String split : info.splitSourceDirs) {
                apks.add(split);
            }
        }
        for (String apk : apks) {
            try (ZipFile zip = new ZipFile(apk)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    String path = entries.nextElement().getName();
                    if (!path.startsWith("res/")) {
                        continue;
                    }
                    if (!path.contains("/drawable") && !path.contains("/mipmap")) {
                        continue;
                    }
                    int slash = path.lastIndexOf('/');
                    int dot = path.lastIndexOf('.');
                    if (slash < 0 || dot <= slash) {
                        continue;
                    }
                    String name = path.substring(slash + 1, dot);
                    if (scoreNotificationIconName(name) > 0) {
                        names.add(name);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static Drawable loadNamedDrawable(Resources res, String pkg, String name) {
        int id = res.getIdentifier(name, "drawable", pkg);
        if (id == 0) {
            id = res.getIdentifier(name, "mipmap", pkg);
        }
        if (id == 0) {
            return null;
        }
        try {
            return independentCopy(res.getDrawable(id, null));
        } catch (Exception e) {
            return null;
        }
    }

    private static int scoreNotificationIconName(String name) {
        if (name == null) {
            return -1;
        }
        String n = name.toLowerCase(Locale.US);
        if (n.contains("background") || n.contains("bg_") || n.contains("template")
                || n.contains("action") || n.contains("overlay") || n.contains("badge")
                || n.contains("large") || n.contains("number") || n.contains("dot")) {
            return -1;
        }
        if (n.equals("ic_stat_notify") || n.equals("ic_stat_notification")
                || n.equals("notification_icon") || n.equals("ic_notification")) {
            return 110;
        }
        if (n.startsWith("ic_stat_")) {
            return 100;
        }
        if (n.startsWith("stat_notify")) {
            return 95;
        }
        if (n.equals("ic_notify") || n.equals("notifybar") || n.equals("notifybar_message")) {
            return 85;
        }
        if (n.contains("stat_notify") || n.contains("ic_stat")) {
            return 75;
        }
        if (n.contains("notification") && n.contains("icon")) {
            return 60;
        }
        return -1;
    }

    private Drawable loadIconDrawable(String pkg, Icon icon) {
        try {
            Context pkgContext = getContext().createPackageContext(pkg,
                    Context.CONTEXT_IGNORE_SECURITY);
            Drawable d = icon.loadDrawable(pkgContext);
            if (d != null) {
                return independentCopy(d);
            }
        } catch (Exception ignored) {
        }
        try {
            Drawable d = icon.loadDrawable(getContext());
            if (d != null) {
                return independentCopy(d);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Same-app mark if the APK has no notification smallIcon resource. */
    private Bitmap loadStatusBarGlyphBitmap(PackageManager pm, String pkg, int size, int tint) {
        Drawable appIcon = loadAppIcon(pm, pkg);
        if (appIcon instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable adaptive = (AdaptiveIconDrawable) independentCopy(appIcon);
            Drawable mono = adaptive.getMonochrome();
            if (mono != null) {
                return bakeStatusBarTint(renderGlyph(independentCopy(mono), size, true), tint);
            }
            Drawable foreground = adaptive.getForeground();
            if (foreground != null) {
                return bakeStatusBarTint(
                        renderGlyph(independentCopy(foreground), size, true), tint);
            }
        }
        return null;
    }

    private static final String[] KNOWN_SMALL_ICON_NAMES = {
        "ic_stat_notify",
        "ic_stat_notification",
        "notification_icon",
        "ic_notification",
        "ic_stat_notify_msg",
        "ic_stat_notify_chat",
        "ic_stat_notify_mail",
        "ic_stat_notify_email",
        "ic_stat_notify_alert",
        "ic_stat_gcm",
        "stat_notify_email",
        "stat_notify_chat",
        "stat_notify_msg",
        "ic_notify",
        "notifybar",
        "notifybar_message",
        "ic_notification_small",
        "notification_small_icon",
        "fcm_notification_icon",
        "ic_stat_fcm",
    };

    /** Reject opaque color plates that would bake into a solid tinted square. */
    private static boolean looksLikeGlyph(Drawable d) {
        int w = Math.max(d.getIntrinsicWidth(), 48);
        int h = Math.max(d.getIntrinsicHeight(), 48);
        Bitmap probe = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(probe);
        d.setBounds(0, 0, w, h);
        d.draw(canvas);
        int transparent = 0;
        int samples = 0;
        for (int y = 0; y < h; y += Math.max(1, h / 8)) {
            for (int x = 0; x < w; x += Math.max(1, w / 8)) {
                samples++;
                if (Color.alpha(probe.getPixel(x, y)) < 32) {
                    transparent++;
                }
            }
        }
        probe.recycle();
        return samples > 0 && transparent > samples / 4;
    }

    private static Bitmap renderGlyph(Drawable src, int size, boolean adaptiveLayer) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        if (adaptiveLayer) {
            int extra = Math.round(size * AdaptiveIconDrawable.getExtraInsetFraction());
            src.setBounds(-extra, -extra, size + extra, size + extra);
        } else {
            int pad = Math.round(size * 0.12f);
            src.setBounds(pad, pad, size - pad, size - pad);
        }
        src.draw(canvas);
        return bmp;
    }

    /**
     * Same matrix StatusBarIconView uses: replace RGB with the status-bar tint, keep alpha.
     */
    private static Bitmap bakeStatusBarTint(Bitmap src, int color) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float[] matrix = new float[20];
        matrix[4] = Color.red(color);
        matrix[9] = Color.green(color);
        matrix[14] = Color.blue(color);
        matrix[18] = Color.alpha(color) / 255f;
        paint.setColorFilter(new ColorMatrixColorFilter(matrix));
        canvas.drawBitmap(src, 0, 0, paint);
        if (src != out) {
            src.recycle();
        }
        return out;
    }

    private static Bitmap toCircularBitmap(Drawable src, int size) {
        Bitmap square = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas squareCanvas = new Canvas(square);
        src.setBounds(0, 0, size, size);
        src.draw(squareCanvas);
        return toCircularBitmap(square);
    }

    private static Bitmap toCircularBitmap(Bitmap src) {
        int size = Math.min(src.getWidth(), src.getHeight());
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Path circle = new Path();
        circle.addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW);
        canvas.clipPath(circle);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setShader(new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, size, size, paint);
        if (src != out) {
            src.recycle();
        }
        return out;
    }

    private static Bitmap copyBitmap(Bitmap src) {
        return src.copy(src.getConfig() != null ? src.getConfig() : Bitmap.Config.ARGB_8888, false);
    }

    private static class PreviewSet {
        final Drawable[] defaultIcons = new Drawable[PREVIEW_COUNT];
        final Drawable[] appIcons = new Drawable[PREVIEW_COUNT];
        final Drawable[] beanIcons = new Drawable[PREVIEW_COUNT];
    }

    private static class ListSpacingDecoration extends RecyclerView.ItemDecoration {
        private final int mSpacing;

        ListSpacingDecoration(int spacingPx) {
            mSpacing = spacingPx;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            outRect.set(0, mSpacing, 0, mSpacing);
        }
    }

    private interface OnStyleClickListener {
        void onStyleClicked(int position);
    }

    private static class NotifIconStyleAdapter
            extends RecyclerView.Adapter<NotifIconStyleAdapter.Holder> {
        private final LayoutInflater mInflater;
        private final String[] mEntries;
        private final String[] mEntryValues;
        private final PreviewSet mPreviewSet;
        private int mSelectedIndex;
        private OnStyleClickListener mListener;

        NotifIconStyleAdapter(Context context, String[] entries, String[] entryValues,
                int selectedIndex, PreviewSet previewSet) {
            mInflater = LayoutInflater.from(context);
            mEntries = entries;
            mEntryValues = entryValues;
            mSelectedIndex = selectedIndex;
            mPreviewSet = previewSet;
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
            return new Holder(mInflater.inflate(
                    R.layout.m3_notif_icon_style_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.itemView.setSelected(position == mSelectedIndex);
            holder.itemView.setContentDescription(mEntries[position]);
            holder.text.setText(mEntries[position]);

            int mode = position < mEntryValues.length
                    ? Integer.parseInt(mEntryValues[position]) : MODE_ICONS;
            holder.previewIcons.setVisibility(View.GONE);
            holder.previewCount.setVisibility(View.GONE);
            holder.previewHidden.setVisibility(View.GONE);

            switch (mode) {
                case MODE_COUNT:
                    holder.previewCount.setVisibility(View.VISIBLE);
                    break;
                case MODE_HIDDEN:
                    holder.previewHidden.setVisibility(View.VISIBLE);
                    break;
                case MODE_PINK_BEAN:
                    bindIconRow(holder, mPreviewSet.beanIcons, ImageView.ScaleType.CENTER_CROP);
                    break;
                case MODE_APP_ICONS:
                    bindIconRow(holder, mPreviewSet.appIcons, ImageView.ScaleType.CENTER_CROP);
                    break;
                case MODE_ICONS:
                default:
                    bindIconRow(holder, mPreviewSet.defaultIcons, ImageView.ScaleType.CENTER_CROP);
                    break;
            }

            holder.itemView.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && mListener != null) {
                    mListener.onStyleClicked(pos);
                }
            });
        }

        private void bindIconRow(Holder holder, Drawable[] icons, ImageView.ScaleType scaleType) {
            holder.previewIcons.setVisibility(View.VISIBLE);
            ImageView[] views = { holder.icon1, holder.icon2, holder.icon3 };
            for (int i = 0; i < views.length; i++) {
                views[i].setColorFilter(null);
                views[i].setImageTintList(null);
                views[i].setScaleType(scaleType);
                Drawable d = icons[i];
                if (d instanceof BitmapDrawable
                        && ((BitmapDrawable) d).getBitmap() != null) {
                    views[i].setVisibility(View.VISIBLE);
                    views[i].setImageBitmap(((BitmapDrawable) d).getBitmap());
                } else if (d != null) {
                    views[i].setVisibility(View.VISIBLE);
                    views[i].setImageDrawable(d);
                } else {
                    views[i].setImageDrawable(null);
                    views[i].setVisibility(View.GONE);
                }
            }
        }

        @Override
        public int getItemCount() {
            return mEntries.length;
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView text;
            final View previewIcons;
            final ImageView icon1;
            final ImageView icon2;
            final ImageView icon3;
            final TextView previewCount;
            final ImageView previewHidden;

            Holder(@NonNull View itemView) {
                super(itemView);
                text = itemView.findViewById(android.R.id.text1);
                previewIcons = itemView.findViewById(R.id.notif_preview_icons);
                icon1 = itemView.findViewById(R.id.notif_preview_icon_1);
                icon2 = itemView.findViewById(R.id.notif_preview_icon_2);
                icon3 = itemView.findViewById(R.id.notif_preview_icon_3);
                previewCount = itemView.findViewById(R.id.notif_preview_count);
                previewHidden = itemView.findViewById(R.id.notif_preview_hidden);
            }
        }
    }
}
