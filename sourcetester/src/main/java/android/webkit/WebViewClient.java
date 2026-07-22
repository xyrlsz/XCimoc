package android.webkit;

public class WebViewClient {
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return false; }
    public void onPageFinished(WebView view, String url) {}
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {}
}
