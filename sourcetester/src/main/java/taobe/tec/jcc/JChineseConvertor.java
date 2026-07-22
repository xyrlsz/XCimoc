package taobe.tec.jcc;

/**
 * Stub for JChineseConvertor
 */
public class JChineseConvertor {
    private static JChineseConvertor instance;

    public static JChineseConvertor getInstance() {
        if (instance == null) instance = new JChineseConvertor();
        return instance;
    }

    public String s2t(String text) { return text; }
    public String t2s(String text) { return text; }
}
