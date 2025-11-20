package org.bepass.oblivion;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;

import org.bepass.oblivion.utils.FileManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BypassListAppsAdapter extends RecyclerView.Adapter<BypassListAppsAdapter.ViewHolder> {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LoadListener loadListener;
    private List<AppInfo> appList = new ArrayList<>();
    private OnAppSelectListener onAppSelectListener;

    public BypassListAppsAdapter(Context context, LoadListener loadListener) {
        this.loadListener = loadListener;
        loadApps(context, false);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void loadApps(Context context, boolean shouldShowSystemApps) {
        if (loadListener != null) loadListener.onLoad(true);
        executor.submit(() -> {
            appList = getInstalledApps(context, shouldShowSystemApps);
            handler.post(() -> {
                notifyDataSetChanged();
                if (loadListener != null) loadListener.onLoad(false);
            });
        });
    }

    private List<AppInfo> getInstalledApps(Context context, boolean shouldShowSystemApps) {
        Set<String> selectedApps = FileManager.getStringSet("splitTunnelApps", new HashSet<>());
        PackageManager packageManager = context.getPackageManager();
        @SuppressLint("QueryPermissionsNeeded") 
        List<android.content.pm.PackageInfo> packages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS | PackageManager.GET_META_DATA);
        List<AppInfo> tempAppList = new ArrayList<>();

        for (android.content.pm.PackageInfo packageInfo : packages) {
            // Hide self
            if (packageInfo.packageName.equals(context.getPackageName())) continue;

            boolean isSelected = selectedApps.contains(packageInfo.packageName);

            // Filter System Apps: Show if (ShowSystemApps is ON) OR (App is Selected)
            boolean isSystem = (packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (isSystem && !shouldShowSystemApps && !isSelected) continue;

            // Filter Internet Permission
            boolean hasInternet = false;
            if (packageInfo.requestedPermissions != null) {
                for (String p : packageInfo.requestedPermissions) {
                    if (android.Manifest.permission.INTERNET.equals(p)) {
                        hasInternet = true;
                        break;
                    }
                }
            }
            // If it doesn't request internet, skip it (unless it's already selected, just in case)
            if (!hasInternet && !isSelected) continue;

            tempAppList.add(new AppInfo(
                    packageInfo.applicationInfo.loadLabel(packageManager).toString(),
                    () -> packageInfo.applicationInfo.loadIcon(packageManager),
                    packageInfo.packageName,
                    isSelected
            ));
        }

        // Sort: Selected first, then Alphabetical
        tempAppList.sort((o1, o2) -> {
            if (o1.isSelected != o2.isSelected) {
                return o1.isSelected ? -1 : 1;
            }
            return o1.appName.compareToIgnoreCase(o2.appName);
        });

        return tempAppList;
    }

    public void setShouldShowSystemApps(Context context, boolean shouldShowSystemApps) {
        loadApps(context, shouldShowSystemApps);
    }

    public void setOnAppSelectListener(OnAppSelectListener onAppSelectListener) {
        this.onAppSelectListener = onAppSelectListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.installed_app_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo appInfo = appList.get(position);
        holder.appNameTextView.setText(appInfo.appName);
        holder.checkBox.setChecked(appInfo.isSelected);
        Glide.with(holder.itemView).load(appInfo.iconLoader.load()).into(holder.icon);

        holder.itemView.setOnClickListener(v -> {
            appInfo.isSelected = !appInfo.isSelected;
            notifyItemChanged(position);

            Set<String> newSet = new HashSet<>(FileManager.getStringSet("splitTunnelApps", new HashSet<>()));
            if (appInfo.isSelected) {
                newSet.add(appInfo.packageName);
            } else {
                newSet.remove(appInfo.packageName);
            }
            FileManager.set("splitTunnelApps", newSet);

            if (onAppSelectListener != null)
                onAppSelectListener.onSelect(appInfo.packageName, appInfo.isSelected);
        });
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    public interface LoadListener {
        void onLoad(boolean loading);
    }

    public interface OnAppSelectListener {
        void onSelect(String packageName, boolean selected);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView appNameTextView;
        CheckBox checkBox;
        ShapeableImageView icon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            appNameTextView = itemView.findViewById(R.id.appNameTextView);
            checkBox = itemView.findViewById(R.id.checkBox);
            icon = itemView.findViewById(R.id.icon);
        }
    }

    public static class AppInfo {
        String appName;
        String packageName;
        IconLoader iconLoader;
        boolean isSelected;

        AppInfo(String appName, IconLoader iconLoader, String packageName, boolean isSelected) {
            this.appName = appName;
            this.packageName = packageName;
            this.iconLoader = iconLoader;
            this.isSelected = isSelected;
        }

        interface IconLoader {
            Drawable load();
        }
    }
}
