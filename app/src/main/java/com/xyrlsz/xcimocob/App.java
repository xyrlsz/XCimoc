package com.xyrlsz.xcimocob;

import static com.xyrlsz.xcimocob.utils.TrustAllSslUtils.createSSLSocketFactory;
import static com.xyrlsz.xcimocob.utils.TrustAllSslUtils.getTrustAllCerts;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Rect;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.xyrlsz.xcimocob.manager.JsSourceManager;
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
import com.xyrlsz.xcimocob.utils.StringUtils;
import com.xyrlsz.xcimocob.utils.ThemeUtils;
import com.xyrlsz.xcimocob.utils.ThreadPoolManager;
import com.xyrlsz.xcimocob.utils.TrustAllSslUtils;

import java.io.File;
import java.util.concurrent.TimeUnit;

import io.objectbox.BoxStore;
import okhttp3.Cache;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
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
    /**
     * 应用层设置自定义 UncaughtExceptionHandler 前的系统/第三方旧处理器；处理完自定义日志后必须委托回去
     */
    private Thread.UncaughtExceptionHandler mOldUncaughtExceptionHandler;

    public static Context getAppContext() {
        return mApp.getApplicationContext();
    }

    public static App getApp() {
        return mApp;
    }

    /**
     * 当前可见（resumed）的 Activity，可能为 null（无前台 Activity 时）。
     */
    public static Activity getCurrentActivity() {
        return mApp == null || mApp.mActivityLifecycle == null ? null : mApp.mActivityLifecycle.getCurrentActivity();
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

                    // 优化连接池：增大最大空闲连接数和每个路由的连接数，提高并发吞吐
                    ConnectionPool connectionPool = new ConnectionPool(20, 10, TimeUnit.MINUTES);

                    // 优化 Dispatcher：提高最大并发请求数和每主机并发数
                    Dispatcher dispatcher = new Dispatcher();
                    dispatcher.setMaxRequests(64);
                    dispatcher.setMaxRequestsPerHost(8);

                    client = new OkHttpClient()
                            .newBuilder()
                            .connectionPool(connectionPool)
                            .dispatcher(dispatcher)
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
                                    assert contentType != null;
                                    if (!contentType.startsWith("image/")
                                            && !contentType.startsWith("video/")
                                            && !contentType.startsWith("audio/")) {
                                        Response.Builder b = response.newBuilder()
                                                .header("Cache-Control", "max-age=300, stale-while-revalidate=300");
                                        // 加固：如果上游没声明 Vary: Accept-Encoding，主动补一条。
                                        // 场景：同一 URL 可能以 gzip/identity 两种编码返回；
                                        //       缺少 Vary: Accept-Encoding 时，OkHttp Cache 不会按编码分流，
                                        //       会把「请求了 gzip 的那次响应」缓存下来，下次命中请求若没带
                                        //       Accept-Encoding: gzip 也直接回 gzip 字节，导致解析器看到
                                        //       \u001f (gzip 魔数) 却直接按 HTML/JSON 解析出错。
                                        String vary = response.header("Vary");
                                        boolean needAdd = true;

                                        if (vary != null && !vary.isEmpty()) {
                                            // 1. 检查是否包含 "*"（通配符）
                                            if (vary.contains("*")) {
                                                needAdd = false; // 已隐含所有依赖，无需再追加
                                            } else {
                                                // 2. 正常检查 Accept-Encoding
                                                String[] parts = vary.split(",");
                                                for (String p : parts) {
                                                    if (p.trim().equalsIgnoreCase("Accept-Encoding")) {
                                                        needAdd = false;
                                                        break;
                                                    }
                                                }
                                            }
                                        }

                                        if (needAdd) {
                                            // 建议使用 setHeader 而非 addHeader，避免重复头
                                            b.addHeader("Vary", "Accept-Encoding");
                                        }
                                        return b.build();
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
        // 先拿到系统默认 handler 再替换，保证最终仍会触发 Android 的崩溃上报/ANR 对话流程
        mOldUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
        mActivityLifecycle = new ActivityLifecycle();
        registerActivityLifecycleCallbacks(mActivityLifecycle);
        mPreferenceManager = new PreferenceManager(this);

        // RxJava3 全局错误处理 - 防止 UndeliverableException 崩溃
        io.reactivex.rxjava3.plugins.RxJavaPlugins.setErrorHandler(e -> {
            if (e instanceof io.reactivex.rxjava3.exceptions.UndeliverableException) {
                e = e.getCause();
            }
            // 所有未送达异常只记录日志，不触发崩溃
            // 常见原因：Rx 链下游已取消订阅，但上游仍在执行
            Log.w("RxJavaError", "Undeliverable exception ignored: " + e.getMessage(), e);
        });

        // 初始化ObjectBox
        mBoxStore = MyObjectBox.builder().androidContext(this).build();

        UpdateHelper.update(mPreferenceManager, mBoxStore, getApplicationContext());
        initPixels();

        manager_wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        // 获取栈顶Activity以及当前App上下文
        mApp = this;

        // 首次启动播种内置 JS 源（幂等，仅当 JsSource 表为空时写入）。
        // 播种和解析器初始化必须保持顺序，避免解析器先缓存 Null 实现。
        ThreadPoolManager.getInstance().getIoExecutor().execute(() -> {
            try {
                JsSourceManager.getInstance(this).seedFromAssets();
            } catch (Throwable ignore) {
            }
            SourceManager.getInstance(this).init();
            try {
                JsSourceManager.getInstance(this).autoSignIn();
            } catch (Throwable ignore) {
            }
            try {
                JsSourceManager.getInstance(this).autoUpdateIfNeeded();
            } catch (Throwable ignore) {
            }
        });

        // 为存量 JS 源回填登录/设置元信息（metaReady=false 的源），供漫画源设置列表直接读取，
        // 避免列表加载时逐源跑 JS。在后台线程执行，不阻塞启动。
        ThreadPoolManager.getInstance().getIoExecutor().execute(() -> {
            try {
                JsSourceManager.getInstance(this).backfillMeta();
            } catch (Throwable ignore) {
            }
        });

        // 初始化自动数据同步管理器
        DataSyncManager dataSyncManager = DataSyncManager.getInstance();
        dataSyncManager.init();

        // 冷启动完成，尝试首次同步（此时 Activity 尚未创建，延迟到主线程执行）
        new Handler(Looper.getMainLooper()).post(dataSyncManager::onAppStart);

        // 注册前后台切换监听，用于自动同步
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            private int mActiveCount = 0;

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                if (mActiveCount == 0) {
                    // 应用从后台切到前台
                    dataSyncManager.onForeground();
                }
                mActiveCount++;
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                mActiveCount--;
                if (mActiveCount == 0) {
                    // 应用进入后台
                    dataSyncManager.onBackground();
                }
            }

            @Override
            public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
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

        FrescoUtils.init(this, 2048);
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
            if (doc == null) {
                Log.e("UncaughtException", "Storage root is null, skip saving crash log");
            } else {
                CimocDocumentFile dir = DocumentUtils.getOrCreateSubDirectory(doc, "log");
                if (dir == null) {
                    Log.e("UncaughtException", "Failed to get/create log directory, skip saving crash log");
                } else {
                    CimocDocumentFile file = DocumentUtils.getOrCreateFile(
                            dir, StringUtils.getDateStringWithSuffix("log"));
                    if (file == null) {
                        Log.e("UncaughtException", "Failed to get/create crash log file, skip saving crash log");
                    } else {
                        DocumentUtils.writeStringToFile(getContentResolver(), file, sb.toString());
                    }
                }
            }
        } catch (Exception ex) {
            Log.e("UncaughtException", "Error while saving crash log", ex);
        }
        if (mActivityLifecycle != null) {
            mActivityLifecycle.clear();
        }
        // 关键：自定义日志保存完成后，必须将异常回传给原先的 UncaughtExceptionHandler，
        // 否则 Android 系统默认的崩溃收集/弹窗流程（包括 ACRA/XCrash 等嵌入 SDK）会被吞掉，
        // 导致应用「僵死」而不是崩溃，用户体验更差且难以定位问题。
        if (mOldUncaughtExceptionHandler != null && mOldUncaughtExceptionHandler != this) {
            mOldUncaughtExceptionHandler.uncaughtException(t, e);
        } else {
            System.exit(1);
        }
    }

    @Override
    public App getAppInstance() {
        return this;
    }

    @SuppressWarnings("deprecation")
    private void initPixels() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+：使用新的 WindowManager#getCurrentWindowMetrics
            Rect bounds = windowManager.getCurrentWindowMetrics().getBounds();
            metrics.widthPixels = bounds.width();
            metrics.heightPixels = bounds.height();
        } else {
            // API 29 及以下：旧的 getDefaultDisplay 仍可用（已弃用，见 @SuppressWarnings）
            windowManager.getDefaultDisplay().getMetrics(metrics);
        }
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

}
