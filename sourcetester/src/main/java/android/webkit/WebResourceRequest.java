package android.webkit;

import android.net.Uri;

public interface WebResourceRequest {
    Uri getUrl();
    boolean isForMainFrame();
    boolean isRedirect();
}
