package android.webkit;

public class WebSettings {
    public static final int LOAD_NO_CACHE = 2;
    public static final int LOAD_CACHE_ELSE_NETWORK = 1;
    public static final int LOAD_NORMAL = 0;

    public void setJavaScriptEnabled(boolean enabled) {}
    public void setUserAgentString(String ua) {}
    public void setCacheMode(int mode) {}
    public void setDomStorageEnabled(boolean enabled) {}
    public void setAllowFileAccess(boolean allow) {}
    public void setAllowContentAccess(boolean allow) {}
    public void setAllowFileAccessFromFileURLs(boolean allow) {}
    public void setAllowUniversalAccessFromFileURLs(boolean allow) {}
    public void setLoadWithOverviewMode(boolean mode) {}
    public void setUseWideViewPort(boolean use) {}
    public void setBuiltInZoomControls(boolean enabled) {}
    public void setDisplayZoomControls(boolean enabled) {}
    public void setSupportZoom(boolean support) {}
    public void setLayoutAlgorithm(Object algo) {}
    public void setAppCacheEnabled(boolean enabled) {}
    public void setAppCachePath(String path) {}
    public void setDatabaseEnabled(boolean enabled) {}
    public void setGeolocationEnabled(boolean enabled) {}
    public void setRenderPriority(int priority) {}
    public void setMixedContentMode(int mode) {}
}
