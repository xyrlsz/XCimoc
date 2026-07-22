package com.xyrlsz.xcimocob.misc;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by Hiroshi on 2018/2/13.
 */

public class ActivityLifecycle implements Application.ActivityLifecycleCallbacks {

    private List<Activity> mActivityList;

    public ActivityLifecycle() {
        mActivityList = new LinkedList<>();
    }

    public void clear() {
        List<Activity> copy = new LinkedList<>(mActivityList);
        for (Activity activity : copy) {
            activity.finish();
        }
        mActivityList.clear();
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {
        mActivityList.add(activity);
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        mActivityList.remove(activity);
    }

}
