package android.view;

public class ViewGroup {
    public static class LayoutParams {
        public static final int MATCH_PARENT = -1;
        public static final int WRAP_CONTENT = -2;

        public LayoutParams(int w, int h) {}
        public LayoutParams() {}
    }

    public static class MarginLayoutParams extends LayoutParams {
        public MarginLayoutParams(int w, int h) {}
        public MarginLayoutParams() {}
    }
}
