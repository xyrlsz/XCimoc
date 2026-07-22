package com.xyrlsz.xcimocob;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.util.Log;

import com.xyrlsz.xcimocob.component.AppGetter;

import okhttp3.OkHttpClient;

/**
 * Mock App class for testing — replaces the real Android Application
 */
public class App implements AppGetter {

    private static App sInstance;

    public App() {
        sInstance = this;
    }

    public static App getApp() {
        if (sInstance == null) {
            sInstance = new App();
        }
        return sInstance;
    }

    @Override
    public App getAppInstance() {
        return this;
    }

    // Context-like methods
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return new SharedPreferences();
    }

    public Context getApplicationContext() {
        return new Context();
    }

    public static Context getAppContext() {
        return getApp().getApplicationContext();
    }

    public Resources getResources() {
        return new Resources();
    }

    public static Resources getAppResources() {
        return getApp().getResources();
    }

    // OkHttpClient stub
    private static OkHttpClient mHttpClient;

    public static OkHttpClient getHttpClient() {
        if (mHttpClient == null) {
            mHttpClient = new OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
        }
        return mHttpClient;
    }

    public static void setHttpClient(OkHttpClient client) {
        mHttpClient = client;
    }

    // BoxStore stub — parsers don't actually need this at test time
    public Object getBoxStore() {
        return null;
    }

}
