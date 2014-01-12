package compiler;

public class StringUtil {
    public static String join(String sep, Object[] args) {
        String result = "";
        for (int i = 0; i < args.length; ++i) {
            result += args[i];
            if (i + 1 < args.length) {
                result += sep;
            }
        }
        return result;
    }
}
