package android.webkit;

import android.content.Context;
import android.view.ViewGroup;

import java.util.Map;

public class WebView {

    public WebView(Context context) {}
    public void loadUrl(String url) {}
    public void loadUrl(String url, Map<String, String> headers) {}
    public void setWebViewClient(WebViewClient client) {}
    public void setWebChromeClient(WebChromeClient client) {}
    public android.webkit.WebSettings getSettings() { return new android.webkit.WebSettings(); }
    public void evaluateJavascript(String script, ValueCallback<String> callback) {}
    public void destroy() {}
    public void setLayoutParams(ViewGroup.LayoutParams params) {}
    public void addJavascriptInterface(Object obj, String name) {}
    public void setTag(Object tag) {}
    public Object getTag() { return null; }

    public interface ValueCallback<T> {
        void onReceiveValue(T value);
    }
}
