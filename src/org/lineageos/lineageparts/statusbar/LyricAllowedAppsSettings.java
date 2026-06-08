/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.statusbar;

import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.android.settingslib.applications.ApplicationsState;

import com.google.android.material.materialswitch.MaterialSwitch;

import org.lineageos.lineageparts.R;
import org.lineageos.lineageparts.SettingsPreferenceFragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LyricAllowedAppsSettings extends SettingsPreferenceFragment
        implements ApplicationsState.Callbacks {

    private AllPackagesAdapter mAllPackagesAdapter;
    private ApplicationsState mApplicationsState;
    private ApplicationsState.Session mSession;
    private ActivityFilter mActivityFilter;
    private final Set<String> mAllowedPackages = new HashSet<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mApplicationsState = ApplicationsState.getInstance(requireActivity().getApplication());
        mSession = mApplicationsState.newSession(this);
        mSession.onResume();
        mActivityFilter = new ActivityFilter(requireActivity().getPackageManager());
        mAllPackagesAdapter = new AllPackagesAdapter(getActivity());
        mAllowedPackages.addAll(LyricSecureSettings.getAllowedPackages(requireContext()));
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.long_screen_layout, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ListView userListView = view.findViewById(R.id.user_list_view);
        userListView.setAdapter(mAllPackagesAdapter);
        TextView emptyView = view.findViewById(R.id.user_list_empty_view);
        emptyView.setText(R.string.status_bar_lyric_whitelist_no_apps);
        userListView.setEmptyView(emptyView);
    }

    @Override
    public void onResume() {
        super.onResume();
        rebuild();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mSession.onPause();
        mSession.onDestroy();
    }

    @Override
    public void onPackageListChanged() {
        mActivityFilter.updateLauncherInfoList();
        rebuild();
    }

    @Override
    public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> entries) {
        if (entries != null) {
            handleAppEntries(entries);
            mAllPackagesAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onLoadEntriesCompleted() {
        rebuild();
    }

    @Override
    public void onAllSizesComputed() {}

    @Override
    public void onLauncherInfoChanged() {}

    @Override
    public void onPackageIconChanged() {}

    @Override
    public void onPackageSizeChanged(String packageName) {}

    @Override
    public void onRunningStateChanged(boolean running) {}

    private void handleAppEntries(List<ApplicationsState.AppEntry> entries) {
        final ArrayList<String> sections = new ArrayList<>();
        final ArrayList<Integer> positions = new ArrayList<>();
        final PackageManager pm = getPackageManager();
        String lastSectionIndex = null;
        int offset = 0;

        for (int i = 0; i < entries.size(); i++) {
            final ApplicationInfo info = entries.get(i).info;
            final String label = (String) info.loadLabel(pm);
            final String sectionIndex;

            if (!info.enabled) {
                sectionIndex = "--";
            } else if (TextUtils.isEmpty(label)) {
                sectionIndex = "";
            } else {
                sectionIndex = label.substring(0, 1).toUpperCase();
            }

            if (lastSectionIndex == null ||
                    !TextUtils.equals(sectionIndex, lastSectionIndex)) {
                sections.add(sectionIndex);
                positions.add(offset);
                lastSectionIndex = sectionIndex;
            }

            offset++;
        }

        mAllPackagesAdapter.setEntries(entries, sections, positions);
    }

    private void rebuild() {
        mSession.rebuild(mActivityFilter, ApplicationsState.ALPHA_COMPARATOR);
    }

    private void setPackageAllowed(String packageName, boolean allowed) {
        if (allowed) {
            if (mAllowedPackages.add(packageName)) {
                LyricSecureSettings.addAllowedPackage(requireContext(), packageName);
            }
        } else if (mAllowedPackages.remove(packageName)) {
            LyricSecureSettings.removeAllowedPackage(requireContext(), packageName);
        }
    }

    private class AllPackagesAdapter extends BaseAdapter implements SectionIndexer {

        private final LayoutInflater mInflater;
        private List<ApplicationsState.AppEntry> mEntries = new ArrayList<>();
        private String[] mSections;
        private int[] mPositions;

        AllPackagesAdapter(Context context) {
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return mEntries.size();
        }

        @Override
        public Object getItem(int position) {
            return mEntries.get(position);
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public long getItemId(int position) {
            return mEntries.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ApplicationsState.AppEntry entry = mEntries.get(position);
            ViewHolder holder;

            if (convertView == null) {
                holder = new ViewHolder(mInflater.inflate(
                        R.layout.long_screen_list_item, parent, false));
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            if (entry == null) {
                return holder.rootView;
            }

            holder.title.setText(entry.label);
            mApplicationsState.ensureIcon(entry);
            holder.icon.setImageDrawable(entry.icon);
            holder.state.setOnCheckedChangeListener(null);
            holder.state.setTag(entry);
            holder.state.setChecked(mAllowedPackages.contains(entry.info.packageName));
            holder.state.setOnCheckedChangeListener((buttonView, isChecked) -> {
                final ApplicationsState.AppEntry appEntry =
                        (ApplicationsState.AppEntry) buttonView.getTag();
                setPackageAllowed(appEntry.info.packageName, isChecked);
            });
            return holder.rootView;
        }

        private void setEntries(List<ApplicationsState.AppEntry> entries,
                List<String> sections, List<Integer> positions) {
            mEntries = entries;
            mSections = sections.toArray(new String[0]);
            mPositions = new int[positions.size()];
            for (int i = 0; i < positions.size(); i++) {
                mPositions[i] = positions.get(i);
            }
            notifyDataSetChanged();
        }

        @Override
        public int getPositionForSection(int section) {
            if (section < 0 || section >= mSections.length) {
                return -1;
            }
            return mPositions[section];
        }

        @Override
        public int getSectionForPosition(int position) {
            if (position < 0 || position >= getCount()) {
                return -1;
            }
            final int index = Arrays.binarySearch(mPositions, position);
            return index >= 0 ? index : -index - 2;
        }

        @Override
        public Object[] getSections() {
            return mSections;
        }
    }

    private static class ViewHolder {
        private final TextView title;
        private final ImageView icon;
        private final MaterialSwitch state;
        private final View rootView;

        private ViewHolder(View view) {
            this.title = view.findViewById(R.id.app_name);
            this.icon = view.findViewById(R.id.app_icon);
            this.state = view.findViewById(R.id.state);
            this.rootView = view;
            view.setTag(this);
        }
    }

    private class ActivityFilter implements ApplicationsState.AppFilter {

        private final PackageManager mPackageManager;
        private final List<String> mLauncherResolveInfoList = new ArrayList<>();

        private ActivityFilter(PackageManager packageManager) {
            this.mPackageManager = packageManager;
            updateLauncherInfoList();
        }

        void updateLauncherInfoList() {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolveInfoList = mPackageManager.queryIntentActivities(intent,
                    PackageManager.ResolveInfoFlags.of(0));

            synchronized (mLauncherResolveInfoList) {
                mLauncherResolveInfoList.clear();
                for (ResolveInfo ri : resolveInfoList) {
                    mLauncherResolveInfoList.add(ri.activityInfo.packageName);
                }
            }
        }

        @Override
        public void init() {}

        @Override
        public boolean filterApp(ApplicationsState.AppEntry entry) {
            synchronized (mLauncherResolveInfoList) {
                return mLauncherResolveInfoList.contains(entry.info.packageName);
            }
        }
    }
}
