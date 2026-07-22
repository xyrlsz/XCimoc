package android.util;

import java.nio.charset.StandardCharsets;

/**
 * Stub for android.util.Base64 — pure Java implementation
 */
public class Base64 {
    public static final int DEFAULT = 0;
    public static final int NO_WRAP = 1;
    public static final int URL_SAFE = 2;

    private static final String BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    private static final String BASE64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    public static String encodeToString(byte[] input, int flags) {
        boolean urlSafe = (flags & URL_SAFE) != 0;
        String alphabet = urlSafe ? BASE64_URL_ALPHABET : BASE64_ALPHABET;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < input.length) {
            int b0 = input[i++] & 0xff;
            int b1 = i < input.length ? input[i++] & 0xff : 0;
            int b2 = i < input.length ? input[i++] & 0xff : 0;
            sb.append(alphabet.charAt(b0 >>> 2));
            sb.append(alphabet.charAt(((b0 << 4) | (b1 >>> 4)) & 0x3f));
            sb.append(i > input.length - (input.length % 3 == 0 ? 3 : input.length % 3) + 1 ? '=' : alphabet.charAt(((b1 << 2) | (b2 >>> 6)) & 0x3f));
            sb.append(i > input.length - (input.length % 3 == 0 ? 3 : input.length % 3) ? '=' : alphabet.charAt(b2 & 0x3f));
        }
        boolean noWrap = (flags & NO_WRAP) != 0;
        if (!noWrap) {
            for (int j = 76; j < sb.length(); j += 77) sb.insert(j, '\n');
        }
        return sb.toString();
    }

    public static byte[] decode(String str, int flags) {
        boolean urlSafe = (flags & URL_SAFE) != 0;
        String clean = str.replaceAll("[^A-Za-z0-9" + (urlSafe ? "-_" : "+/") + "=]", "");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int i = 0;
        while (i < clean.length()) {
            int c = 0;
            int bits = 0;
            for (int j = 0; j < 4; j++) {
                if (i < clean.length()) {
                    char ch = clean.charAt(i++);
                    int val = urlSafe ? BASE64_URL_ALPHABET.indexOf(ch) : BASE64_ALPHABET.indexOf(ch);
                    if (val >= 0) { c = (c << 6) | val; bits += 6; }
                }
            }
            if (bits >= 8) out.write((c >> 16) & 0xff);
            if (bits >= 16) out.write((c >> 8) & 0xff);
            if (bits >= 24) out.write(c & 0xff);
        }
        return out.toByteArray();
    }
}
