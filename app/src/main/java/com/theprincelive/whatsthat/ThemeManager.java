package com.theprincelive.whatsthat;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

public class ThemeManager {
    public static final String PREFS_NAME = "whatsthat_prefs";
    public static final String PREF_THEME = "theme_preference"; // "system", "light", "dark"

    public static Context wrapContext(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String theme = prefs.getString(PREF_THEME, "system");
        
        int nightMode = Configuration.UI_MODE_NIGHT_UNDEFINED;
        if ("light".equals(theme)) {
            nightMode = Configuration.UI_MODE_NIGHT_NO;
        } else if ("dark".equals(theme)) {
            nightMode = Configuration.UI_MODE_NIGHT_YES;
        }
        
        if (nightMode != Configuration.UI_MODE_NIGHT_UNDEFINED) {
            Configuration config = new Configuration(context.getResources().getConfiguration());
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                return context.createConfigurationContext(config);
            }
        }
        return context;
    }

    public static String getThemePref(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_THEME, "system");
    }

    public static void setThemePref(Context context, String theme) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_THEME, theme).apply();
    }
}
