package com.theprincelive.whatsthat;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
        String targetKey = key(packageName, sender, body);
        if (rules.contains(targetKey)) return true;

        String targetPackage = norm(packageName);
        String targetSender = norm(sender);
        String targetBody = norm(body);
        for (String value : rules) {
            Rule rule = Rule.from(value, SEP);
            if (rule == null) continue;
            if (!norm(rule.packageName).equals(targetPackage)) continue;
            String ruleSender = norm(rule.sender);
            String ruleBody = norm(rule.body);
            if (ruleSender.equals(targetSender) && ruleBody.equals(targetBody)) return true;
            if (systemLike(ruleBody) || systemLike(targetBody)) {
                if (systemSignature(ruleBody).equals(systemSignature(targetBody))) return true;
            }
        }
        return false;
    }

    public static List<Rule> list(Context context) {
        ArrayList<Rule> out = new ArrayList<>();
        Set<String> rules = prefs(context).getStringSet(PREF_HIDDEN_RULES, new HashSet<>());
        for (String value : rules) {
            Rule rule = Rule.from(value, SEP);
            if (rule != null) out.add(rule);
        }
        Collections.sort(out, (a, b) -> a.sender.compareToIgnoreCase(b.sender));
        return out;
    }

    public static int count(Context context) {
        return prefs(context).getStringSet(PREF_HIDDEN_RULES, new HashSet<>()).size();
    }

    public static void remove(Context context, String value) {
        Set<String> rules = new HashSet<>(prefs(context).getStringSet(PREF_HIDDEN_RULES, new HashSet<>()));
        if (rules.remove(value)) prefs(context).edit().putStringSet(PREF_HIDDEN_RULES, rules).apply();
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

    private static boolean systemLike(String body) {
        if (body == null || body.isEmpty()) return true;
        if (body.contains("checking for new messages")) return true;
        if (body.contains("new message")) return true;
        if (body.contains("messages from")) return true;
        if (body.contains("charging")) return true;
        if (body.contains("downloading")) return true;
        if (body.contains("download")) return true;
        if (body.contains("backup")) return true;
        return body.contains("running in the background");
    }

    private static String systemSignature(String body) {
        String clean = norm(body);
        clean = clean.replaceAll("\\b\\d+\\s*%\\b", "#%");
        clean = clean.replaceAll("\\b\\d+(\\.\\d+)?\\s*(kb|mb|gb|b)/s\\b", "#speed");
        clean = clean.replaceAll("\\b\\d+(\\.\\d+)?\\s*(kb|mb|gb|b)\\b", "#size");
        clean = clean.replaceAll("\\b\\d+\\b", "#");
        return clean;
    }

    public static class Rule {
        public final String value;
        public final String packageName;
        public final String sender;
        public final String body;

        Rule(String value, String packageName, String sender, String body) {
            this.value = value;
            this.packageName = packageName;
            this.sender = sender;
            this.body = body;
        }

        static Rule from(String value, String sep) {
            if (value == null) return null;
            String[] parts = value.split(sep, -1);
            if (parts.length != 3) return null;
            return new Rule(value, display(parts[0]), display(parts[1]), display(parts[2]));
        }

        private static String display(String value) {
            return value == null || value.trim().isEmpty() ? "Unknown" : value.trim();
        }
    }
}
