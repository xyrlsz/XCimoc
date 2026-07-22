package com.xyrlsz.xcimocob;

import static com.xyrlsz.xcimocob.utils.TrustAllSslUtils.createSSLSocketFactory;
import static com.xyrlsz.xcimocob.utils.TrustAllSslUtils.getTrustAllCerts;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

import androidx.recyclerview.widget.RecyclerView;

import com.xyrlsz.opencc.android.lib.ChineseConverter;
import com.xyrlsz.xcimocob.component.AppGetter;
import com.xyrlsz.xcimocob.core.DisabledOkHttpClient;
import com.xyrlsz.xcimocob.core.Storage;
import com.xyrlsz.xcimocob.core.WebDavConf;
import com.xyrlsz.xcimocob.fresco.ControllerBuilderProvider;
import com.xyrlsz.xcimocob.helper.UpdateHelper;
import com.xyrlsz.xcimocob.manager.PreferenceManager;
import com.xyrlsz.xcimocob.manager.SourceManager;
import com.xyrlsz.xcimocob.misc.ActivityLifecycle;
import com.xyrlsz.xcimocob.model.MyObjectBox;
import com.xyrlsz.xcimocob.network.sync.DataSyncManager;
import com.xyrlsz.xcimocob.saf.CimocDocumentFile;
import com.xyrlsz.xcimocob.ui.activity.MainActivity;
import com.xyrlsz.xcimocob.ui.adapter.GridAdapter;
import com.xyrlsz.xcimocob.utils.DocumentUtils;
import com.xyrlsz.xcimocob.utils.FrescoUtils;
import com.xyrlsz.xcimocob.utils.HintUtils;
import com.xyrlsz.xcimocob.utils.KomiicUtils;
import com.xyrlsz.xcimocob.utils.StringUtils;
import com.xyrlsz.xcimocob.utils.ThemeUtils;
import com.xyrlsz.xcimocob.utils.TrustAllSslUtils;
import com.xyrlsz.xcimocob.utils.ZaiManhuaSignUtils;

import java.io.File;
import java.util.Objects;

import io.objectbox.BoxStore;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Response;

/**
 * Created by Hiroshi on 2016/7/5.
 */
public class App extends Application implements AppGetter, Thread.UncaughtExceptionHandler {
    public static int mWidthPixels;
    public static int mHeightPixels;
    public static int mCoverWidthPixels;
    public static int mCoverHeightPixels;
    public static int mLargePixels;
    private static volatile OkHttpClient mHttpClient;
    private static PreferenceManager mPreferenceManager;
    private static WifiManager manager_wifi;
    private static App mApp;
    // 默认Github源
    private static String UPDATE_CURRENT_URL = Constants.UPDATE_GITHUB_URL;
    private static boolean isNormalExited = false;
    private CimocDocumentFile mCimocDocumentFile;
    private ControllerBuilderProvider mBuilderProvider;
    private RecyclerView.RecycledViewPool mRecycledPool;
    private BoxStore mBoxStore;
    private ActivityLifecycle mActivityLifecycle;

    public static Context getAppContext() {
        return mApp.getApplicationContext();
    }

    public static App getApp() {
        return mApp;
    }

    public static Resources getAppResources() {
        return mApp.getResources();
    }

    public static WifiManager getManager_wifi() {
        return manager_wifi;
    }

    public static PreferenceManager getPreferenceManager() {
        return mPreferenceManager;
    }

    public static String getUpdateCurrentUrl() {
        return UPDATE_CURRENT_URL;
    }

    public static void setUpdateCurrentUrl(String updateCurrentUrl) {
        UPDATE_CURRENT_URL = updateCurrentUrl;
    }

    public static OkHttpClient getHttpClient() {
        // 仅WiFi联网
        boolean onlyWifi =
                mPreferenceManager.getBoolean(PreferenceManager.PREF_OTHER_CONNECT_ONLY_WIFI, false);
        if (!manager_wifi.isWifiEnabled() && onlyWifi) {
            if (mHttpClient == null || mHttpClient.getClass() != DisabledOkHttpClient.class) {
                mHttpClient = new DisabledOkHttpClient();
            }
            return mHttpClient;
        }
        // 双重检查锁定，避免竞态条件
        OkHttpClient client = mHttpClient;
        if (client == null || client.getClass() == DisabledOkHttpClient.class) {
            synchronized (App.class) {
                client = mHttpClient;
                if (client == null || client.getClass() == DisabledOkHttpClient.class) {
                    // 3.OkHttp访问https的Client实例（带HTTP缓存）
                    File cacheDir = new File(mApp.getCacheDir(), "http");
                    Cache httpCache = new Cache(cacheDir, 20 * 1024 * 1024); // 20MB缓存
                    client = new OkHttpClient()
                            .newBuilder()
                            .cache(httpCache)
                            .addNetworkInterceptor(chain -> {
                                Response response = chain.proceed(chain.request());
                                // 有缓存头（Cache-Control/ETag/Last-Modified/Expires）→ 走标准缓存（含条件更新）
                                // 无任何缓存头且响应成功（2xx）→ 视内容类型决定是否缓存
                                // 非成功响应（4xx/5xx 等）→ 不缓存，避免解析失败后一直取到缓存的错误页面
                                if (response.isSuccessful()
                                        && response.header("Cache-Control") == null
                                        && response.header("ETag") == null
                                        && response.header("Last-Modified") == null
                                        && response.header("Expires") == null) {
                                    // 图片已有 Fresco 自己的缓存（image_cache/），OkHttp 再缓存是双重缓存，只浪费空间
                                    // 只缓存 HTML/文本/JSON 等页面内容，不缓存图片/视频/音频
                                    String contentType = response.header("Content-Type", "");
                                    if (!contentType.startsWith("image/")
                                            && !contentType.startsWith("video/")
                                            && !contentType.startsWith("audio/")) {
                                        return response.newBuilder()
                                                .header("Cache-Control", "max-age=300, stale-while-revalidate=300")
                                                .build();
                                    }
                                }
                                return response;
                            })
                            .sslSocketFactory(createSSLSocketFactory(), getTrustAllCerts())
                            .hostnameVerifier(new TrustAllSslUtils.TrustAllHostnameVerifier())
                            .followRedirects(true)
                            .followSslRedirects(true)
                            .retryOnConnectionFailure(true)
                            .build();
                    mHttpClient = client;
                }
            }
        }

        return client;
    }

    public static void goActivity(Class<?> cls) {
        Intent intent = new Intent(mApp.getApplicationContext(), cls);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mApp.startActivity(intent);
    }

    public static void restartApp() {
        Context context = getAppContext();
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        if (context instanceof Activity) {
            ((Activity) context).finish();
        }
        System.exit(0);
    }

    public static void exitApp() {
        setIsNormalExited(true);
        Context context = getAppContext();
        if (context instanceof Activity) {
            ((Activity) context).finish();
        }
        System.exit(0);
    }

    public static boolean isNormalExited() {
        return isNormalExited;
    }

    public static void setIsNormalExited(boolean isNormalExited) {
        App.isNormalExited = isNormalExited;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        // initXCrash();
        Thread.setDefaultUncaughtExceptionHandler(this);
        mActivityLifecycle = new ActivityLifecycle();
        registerActivityLifecycleCallbacks(mActivityLifecycle);
        mPreferenceManager = new PreferenceManager(this);

        // RxJava3 全局错误处理 - 防止 UndeliverableException 崩溃
        io.reactivex.rxjava3.plugins.RxJavaPlugins.setErrorHandler(e -> {
            if (e instanceof io.reactivex.rxjava3.exceptions.UndeliverableException) {
                e = (Throwable) e.getCause();
            }
            // SecurityException: Android 14+ SystemUI NotificationProviderPublic bug (MIUI/HyperOS)
            // 此异常由系统 Bug 引发，应用层无法根治，忽略即可
            if (!(e instanceof java.net.UnknownHostException
                    || e instanceof java.net.ConnectException
                    || e instanceof java.net.SocketTimeoutException
                    || e instanceof javax.net.ssl.SSLException
                    || e instanceof java.io.InterruptedIOException
                    || e instanceof java.lang.SecurityException)) {
                Thread.currentThread().getUncaughtExceptionHandler()
                        .uncaughtException(Thread.currentThread(), e);
            }
        });

        // 初始化ObjectBox
        mBoxStore = MyObjectBox.builder().androidContext(this).build();

        UpdateHelper.update(mPreferenceManager, mBoxStore, getApplicationContext());
        initPixels();

        manager_wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        // 获取栈顶Activity以及当前App上下文
        mApp = this;

        // 初始化自动数据同步管理器
        DataSyncManager dataSyncManager = DataSyncManager.getInstance();
        dataSyncManager.init();

        // 冷启动完成，尝试首次同步（此时 Activity 尚未创建，延迟到主线程执行）
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            dataSyncManager.onAppStart();
        });

        // 注册前后台切换监听，用于自动同步
        registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {
            private int mActiveCount = 0;

            @Override
            public void onActivityStarted(@NonNull android.app.Activity activity) {
                if (mActiveCount == 0) {
                    // 应用从后台切到前台
                    dataSyncManager.onForeground();
                }
                mActiveCount++;
            }

            @Override
            public void onActivityStopped(@NonNull android.app.Activity activity) {
                mActiveCount--;
                if (mActiveCount == 0) {
                    // 应用进入后台
                    dataSyncManager.onBackground();
                }
            }

            @Override
            public void onActivityCreated(@NonNull android.app.Activity activity, android.os.Bundle savedInstanceState) {
            }

            @Override
            public void onActivityResumed(@NonNull android.app.Activity activity) {
            }

            @Override
            public void onActivityPaused(@NonNull android.app.Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull android.app.Activity activity, @NonNull android.os.Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(@NonNull android.app.Activity activity) {
            }
        });

        // 检测并且关闭TestMode
        SharedPreferences testShared = getSharedPreferences(Constants.APP_SHARED, MODE_PRIVATE);
        boolean isTestMode = testShared.getBoolean(Constants.APP_SHARED_TEST_MODE, false);
        if (isTestMode) {
            testShared.edit().putBoolean(Constants.APP_SHARED_TEST_MODE, false).apply();
        }

        // 深色模式设置
        int darkMode = mPreferenceManager
                .getNumber(PreferenceManager.PREF_OTHER_DARK_MOD,
                        PreferenceManager.DARK_MODE_FALLOW_SYSTEM)
                .intValue();
        switch (darkMode) {
            case PreferenceManager.DARK_MODE_FALLOW_SYSTEM:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            case PreferenceManager.DARK_MODE_ALWAYS_DARK:
                if (!ThemeUtils.getSysIsDarkMode(getAppContext())) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                }
                break;
            case PreferenceManager.DARK_MODE_ALWAYS_LIGHT:
                if (ThemeUtils.getSysIsDarkMode(getAppContext())) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                }
                break;
        }

        // 初始化WebDAV配置
        WebDavConf.init(getApplicationContext());

        // 初始化OpenCC
        ChineseConverter.init(getApplicationContext());

        // 再漫画检查登录与自动签到
        SharedPreferences zaiSharedPreferences = getApplicationContext().getSharedPreferences(
                Constants.ZAI_SHARED, Context.MODE_PRIVATE);
        long timestamp = System.currentTimeMillis() / 1000;
        long exp = zaiSharedPreferences.getLong(Constants.ZAI_SHARED_EXP, 0);
        boolean autoSign = zaiSharedPreferences.getBoolean(Constants.ZAI_SHARED_AUTO_SIGN, false);
        String username = zaiSharedPreferences.getString(Constants.ZAI_SHARED_USERNAME, "");
        String passwordMd5 = zaiSharedPreferences.getString(Constants.ZAI_SHARED_PASSWD_MD5, "");
        if (timestamp > exp && !username.isEmpty() && !passwordMd5.isEmpty()) {
            ZaiManhuaSignUtils.LoginWithPasswdMd5(this, new ZaiManhuaSignUtils.LoginCallback() {
                @Override
                public void onSuccess() {
                    if (autoSign) {
                        ZaiManhuaSignUtils.CheckSigned(getApplicationContext(), isSigned -> {
                            if (!isSigned) {
                                ZaiManhuaSignUtils.SignIn(getApplicationContext());
                            }
                        });
                    }
                }

                @Override
                public void onFail() {
                    HintUtils.showToast(getApplicationContext(), "再漫画登录失败");
                }
            }, username, passwordMd5);
        } else if (timestamp > exp && !username.isEmpty()) {
            // 有用户名但没有密码MD5（旧版本数据），提示重新登录
            HintUtils.showToast(getApplicationContext(), "再漫画登录过期，请重新登录");
        } else if (autoSign) {
            ZaiManhuaSignUtils.CheckSigned(getApplicationContext(), isSigned -> {
                if (!isSigned) {
                    ZaiManhuaSignUtils.SignIn(getApplicationContext());
                }
            });
        }

        // komiic 自动刷新token
        if (KomiicUtils.checkExpired()) {
            KomiicUtils.refresh(this);
        }

        FrescoUtils.init(this, 512);
    }

    @Override
    public void uncaughtException(@NonNull Thread t, Throwable e) {
        // Android 14+ 系统 Bug: SecurityException "not owned by uid"
        // 由 SystemUI NotificationProviderPublic 误判引发，应用层无法根治
        // 直接忽略，不要触发崩溃退出流程
        if (e instanceof SecurityException
                && e.getMessage() != null
                && e.getMessage().contains("not owned by uid")) {
            Log.w("UncaughtException", "Ignoring known SystemUI SecurityException (not owned by uid)", e);
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("MODEL: ").append(Build.MODEL).append('\n');
        sb.append("SDK: ").append(Build.VERSION.SDK_INT).append('\n');
        sb.append("RELEASE: ").append(Build.VERSION.RELEASE).append('\n');
        sb.append('\n').append(e.getLocalizedMessage()).append('\n');
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append('\n');
            sb.append(element.toString());
        }
        try {
            CimocDocumentFile doc = getDocumentFile();
            CimocDocumentFile dir = DocumentUtils.getOrCreateSubDirectory(doc, "log");
            CimocDocumentFile file = DocumentUtils.getOrCreateFile(
                    Objects.requireNonNull(dir), StringUtils.getDateStringWithSuffix("log"));
            DocumentUtils.writeStringToFile(
                    getContentResolver(), Objects.requireNonNull(file), sb.toString());
        } catch (Exception ex) {
            Log.e("UncaughtException", "Error while saving crash log", ex);
        }
        mActivityLifecycle.clear();
        System.exit(1);
    }

    @Override
    public App getAppInstance() {
        return this;
    }

    private void initPixels() {
        DisplayMetrics metrics = new DisplayMetrics();
        ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getMetrics(metrics);
        mWidthPixels = metrics.widthPixels;
        mHeightPixels = metrics.heightPixels;
        mCoverWidthPixels = mWidthPixels / 3;
        mCoverHeightPixels = mHeightPixels * mCoverWidthPixels / mWidthPixels;
        mLargePixels = 3 * metrics.widthPixels * metrics.heightPixels;
    }

    public void initRootDocumentFile() {
        String uri = mPreferenceManager.getString(PreferenceManager.PREF_OTHER_STORAGE);
        mCimocDocumentFile = Storage.initRoot(this, uri);
    }

    public CimocDocumentFile getDocumentFile() {
        if (mCimocDocumentFile == null) {
            initRootDocumentFile();
        }
        return mCimocDocumentFile;
    }

    public BoxStore getBoxStore() {
        return mBoxStore;
    }

    public RecyclerView.RecycledViewPool getGridRecycledPool() {
        if (mRecycledPool == null) {
            mRecycledPool = new RecyclerView.RecycledViewPool();
            mRecycledPool.setMaxRecycledViews(GridAdapter.TYPE_GRID, 20);
        }
        return mRecycledPool;
    }

    public ControllerBuilderProvider getBuilderProvider() {
        if (mBuilderProvider == null) {
            mBuilderProvider = new ControllerBuilderProvider(
                    getApplicationContext(), SourceManager.getInstance(this).new HeaderGetter(), true);
        }
        return mBuilderProvider;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
//        MultiDex.install(this);
    }
}
