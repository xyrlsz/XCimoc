package android.util;

/**
 * Stub for android.util.Log — 输出到 stderr
 */
public class Log {
    public static final int VERBOSE = 2;
    public static final int DEBUG = 3;
    public static final int INFO = 4;
    public static final int WARN = 5;
    public static final int ERROR = 6;

    public static int v(String tag, String msg) {
        System.err.println("V/" + tag + ": " + msg);
        return 0;
    }

    public static int d(String tag, String msg) {
        System.err.println("D/" + tag + ": " + msg);
        return 0;
    }

    public static int i(String tag, String msg) {
        System.err.println("I/" + tag + ": " + msg);
        return 0;
    }

    public static int w(String tag, String msg) {
        System.err.println("W/" + tag + ": " + msg);
        return 0;
    }

    public static int w(String tag, String msg, Throwable tr) {
        System.err.println("W/" + tag + ": " + msg);
        if (tr != null) tr.printStackTrace();
        return 0;
    }

    public static int e(String tag, String msg) {
        System.err.println("E/" + tag + ": " + msg);
        return 0;
    }

    public static int e(String tag, String msg, Throwable tr) {
        System.err.println("E/" + tag + ": " + msg);
        if (tr != null) tr.printStackTrace();
        return 0;
    }
}
