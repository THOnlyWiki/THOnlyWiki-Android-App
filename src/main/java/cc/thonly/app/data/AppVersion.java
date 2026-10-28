package cc.thonly.app.data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 版本号值对象，支持 {@code 1.2}、{@code 1.2.0}、{@code v1.2.0} 这类写法。
 *
 * <p>服务端返回的是纯文本（例如 {@code 1.1.0}），可能夹带换行或前缀，
 * 所以解析时只抓第一段数字点号，解析不出来就返回 null 由调用方忽略。
 */
public final class AppVersion implements Comparable<AppVersion> {

    private static final Pattern NUMERIC = Pattern.compile("(\\d+(?:\\.\\d+)*)");

    private final String name;
    private final int[] parts;

    private AppVersion(String name, int[] parts) {
        this.name = name;
        this.parts = parts;
    }

    public static AppVersion parse(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = NUMERIC.matcher(text.trim());
        if (!matcher.find()) {
            return null;
        }
        String numeric = matcher.group(1);
        String[] segments = numeric.split("\\.");
        int[] parts = new int[segments.length];
        for (int i = 0; i < segments.length; i++) {
            try {
                parts[i] = Integer.parseInt(segments[i]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return new AppVersion(numeric, parts);
    }

    public String name() {
        return name;
    }

    @Override
    public int compareTo(AppVersion other) {
        int length = Math.max(parts.length, other.parts.length);
        for (int i = 0; i < length; i++) {
            int left = i < parts.length ? parts[i] : 0;
            int right = i < other.parts.length ? other.parts[i] : 0;
            if (left != right) {
                return left < right ? -1 : 1;
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return name;
    }
}
