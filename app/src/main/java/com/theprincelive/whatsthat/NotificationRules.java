package com.theprincelive.whatsthat;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class NotificationRules {
    private static final String PREFS = "whatsthat_prefs";
    private static final String PREF_HIDDEN_RULES = "hidden_notice_rules";
    private static final String SEP = "\u001f";

    public static void hideSimilar(Context context, String packageName, String sender, String body) {
        Set<String> rules = new HashSet<>(prefs(context).getStringSet(PREF_HIDDEN_RULES, new HashSet<>()));
        rules.add(key(packageName, sender, body));
        prefs(context).edit().putStringSet(PREF_HIDDEN_RULES, rules).apply();
    }

    public static boolean isHidden(Context context, String packageName, String sender, String body) {
        Set<String> rules = prefs(context).getStringSet(PREF_HIDDEN_RULES, new HashSet<>());
        return rules.contains(key(packageName, sender, body));
    }

    public static int count(Context context) {
        return prefs(context).getStringSet(PREF_HIDDEN_RULES, new HashSet<>()).size();
    }

    public static void clear(Context context) {
        prefs(context).edit().remove(PREF_HIDDEN_RULES).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(String packageName, String sender, String body) {
        return norm(packageName) + SEP + norm(sender) + SEP + norm(body);
    }

    private static String norm(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.US);
    }
}
