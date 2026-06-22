package com.theprincelive.whatsthat;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.MessageDigest;

public class AppLock {
    private static final String PREFS = "whatsthat_prefs";
    private static final String PREF_PIN_HASH = "app_lock_pin_hash";
    private static final String PREF_LOCK_ON_CLOSE = "app_lock_on_close";
    private static final String PREF_BIOMETRIC = "app_lock_biometric";
    private static final String PREF_DECOY_PIN_HASH = "app_lock_decoy_pin_hash";
    static boolean unlocked;
    static boolean decoySession;

    public static boolean isEnabled(Context context) {
        return prefs(context).contains(PREF_PIN_HASH);
    }

    public static boolean isUnlocked() {
        return unlocked;
    }

    public static void setUnlocked(boolean value) {
        unlocked = value;
        if (!value) {
            decoySession = false;
        }
    }

    public static boolean isDecoySession() {
        return decoySession;
    }

    public static void setDecoySession(boolean value) {
        decoySession = value;
    }

    public static boolean hasDecoyPin(Context context) {
        return prefs(context).contains(PREF_DECOY_PIN_HASH);
    }

    public static boolean setDecoyPin(Context context, String pin) {
        if (!validPin(pin)) return false;
        prefs(context).edit().putString(PREF_DECOY_PIN_HASH, hash(pin)).apply();
        return true;
    }

    public static boolean verifyDecoyPin(Context context, String pin) {
        String stored = prefs(context).getString(PREF_DECOY_PIN_HASH, "");
        return !stored.isEmpty() && stored.equals(hash(pin));
    }

    public static void disableDecoy(Context context) {
        prefs(context).edit().remove(PREF_DECOY_PIN_HASH).apply();
        decoySession = false;
    }

    public static boolean lockOnClose(Context context) {
        return prefs(context).getBoolean(PREF_LOCK_ON_CLOSE, true);
    }

    public static void setLockOnClose(Context context, boolean value) {
        prefs(context).edit().putBoolean(PREF_LOCK_ON_CLOSE, value).apply();
    }

    public static boolean biometricEnabled(Context context) {
        return prefs(context).getBoolean(PREF_BIOMETRIC, false);
    }

    public static void setBiometricEnabled(Context context, boolean value) {
        prefs(context).edit().putBoolean(PREF_BIOMETRIC, value).apply();
    }

    public static boolean setPin(Context context, String pin) {
        if (!validPin(pin)) return false;
        prefs(context).edit().putString(PREF_PIN_HASH, hash(pin)).apply();
        unlocked = true;
        return true;
    }

    public static boolean verifyPin(Context context, String pin) {
        String stored = prefs(context).getString(PREF_PIN_HASH, "");
        return !stored.isEmpty() && stored.equals(hash(pin));
    }

    public static void disable(Context context) {
        prefs(context).edit().remove(PREF_PIN_HASH).remove(PREF_BIOMETRIC).remove(PREF_DECOY_PIN_HASH).apply();
        unlocked = true;
        decoySession = false;
    }

    public static boolean validPin(String pin) {
        return pin != null && pin.matches("\\d{4}");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String hash(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(("WhatsThat:" + pin).getBytes("UTF-8"));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
