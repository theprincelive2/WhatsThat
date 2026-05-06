package com.theprincelive.whatsthat;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

public class AppLabels {
    public static String label(Context context, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return "Unknown app";
        try {
            PackageManager manager = context.getPackageManager();
            ApplicationInfo info = manager.getApplicationInfo(packageName, 0);
            return manager.getApplicationLabel(info).toString();
        } catch (Exception e) {
            return packageName;
        }
    }
}
