package android.net;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stub for android.net.Uri — 仅实现漫画源代码中用到的方法
 */
public class Uri {

    private final String uriString;

    private Uri(String uriString) {
        this.uriString = uriString != null ? uriString : "";
    }

    public static Uri parse(String uriString) {
        return new Uri(uriString);
    }

    public static Uri fromParts(String scheme, String ssp, String fragment) {
        return new Uri(scheme + ":" + ssp + (fragment != null ? "#" + fragment : ""));
    }

    public String getScheme() {
        int idx = uriString.indexOf(':');
        return idx > 0 ? uriString.substring(0, idx) : null;
    }

    public String getHost() {
        try {
            java.net.URI uri = new java.net.URI(uriString);
            return uri.getHost();
        } catch (Exception e) {
            return null;
        }
    }

    public String getPath() {
        try {
            java.net.URI uri = new java.net.URI(uriString);
            return uri.getPath();
        } catch (Exception e) {
            return null;
        }
    }

    public String getQuery() {
        try {
            java.net.URI uri = new java.net.URI(uriString);
            return uri.getQuery();
        } catch (Exception e) {
            return null;
        }
    }

    public String getLastPathSegment() {
        String path = getPath();
        if (path == null) return null;
        String[] segments = path.split("/");
        return segments.length > 0 ? segments[segments.length - 1] : null;
    }

    public String getEncodedAuthority() {
        try {
            java.net.URI uri = new java.net.URI(uriString);
            String auth = uri.getAuthority();
            return auth != null ? auth : "";
        } catch (Exception e) {
            return "";
        }
    }

    public String getQueryParameter(String key) {
        String query = getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2 && pair[0].equals(key)) return pair[1];
        }
        return null;
    }

    public String toString() {
        return uriString;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Uri)) return false;
        return uriString.equals(((Uri) o).uriString);
    }

    @Override
    public int hashCode() {
        return uriString.hashCode();
    }
}
