package com.theprincelive.whatsthat;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

public class WhatsThatApp extends Application {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int startedActivities;

    private final Runnable lockWhenBackgrounded = () -> {
        if (startedActivities == 0) AppLock.setUnlocked(false);
    };

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(Activity activity) {
                startedActivities++;
                handler.removeCallbacks(lockWhenBackgrounded);
            }

            @Override
            public void onActivityStopped(Activity activity) {
                if (startedActivities > 0) startedActivities--;
                if (startedActivities == 0 && AppLock.isEnabled(activity)) {
                    handler.postDelayed(lockWhenBackgrounded, 700);
                }
            }

            @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }
            @Override public void onActivityResumed(Activity activity) { }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        });
    }
}
