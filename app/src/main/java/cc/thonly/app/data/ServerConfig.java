package cc.thonly.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

/**
 * 服务器地址配置。
 *
 * <p>默认指向正式站点，设置页可以改成局域网地址，方便 App 和本地后端联调。
 * 改完需要重建界面（MainActivity.recreate），否则旧的 EventStore 还拿着老地址。
 *
 * <p>注意：这里只决定「连哪里」，不改变协议。填 http:// 的话就是明文传输，
 * 只在调试时这么用。
 */
public final class ServerConfig {

    private static final String PREFS_NAME = "server";
    private static final String KEY_BASE_URL = "base_url";

    public static final String DEFAULT_BASE_URL = "https://thonly.cc";

    private static SharedPreferences preferences;
    /** 读一次就缓存，避免每次拼 URL 都过一遍 SharedPreferences */
    private static String cached;

    private ServerConfig() {
    }

    public static void init(Context context) {
        if (preferences == null) {
            preferences = context.getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    /** 当前生效的地址，末尾不带斜杠 */
    public static String baseUrl() {
        if (cached != null) {
            return cached;
        }
        String stored = preferences == null ? null : preferences.getString(KEY_BASE_URL, null);
        cached = normalize(stored);
        return cached;
    }

    public static boolean isCustom() {
        return !DEFAULT_BASE_URL.equals(baseUrl());
    }

    public static String activityApi() {
        return baseUrl() + "/api/thonly-activity";
    }

    public static String updateCheckUrl() {
        return baseUrl() + "/index.php?title=WikiKernel%3AAppVersion&action=raw";
    }

    public static String downloadPageUrl() {
        return baseUrl() + "/WikiApp%E5%AE%A2%E6%88%B7%E7%AB%AF%E4%B8%8B%E8%BD%BD";
    }

    /**
     * 保存新地址。
     *
     * @return false 表示地址不合法，什么都没改
     */
    public static boolean setBaseUrl(String raw) {
        if (preferences == null) {
            return false;
        }
        String value = raw == null ? "" : raw.trim();
        if (!isValid(value)) {
            return false;
        }
        cached = normalize(value);
        preferences.edit().putString(KEY_BASE_URL, cached).apply();
        return true;
    }

    /** 恢复默认地址 */
    public static void reset() {
        cached = null;
        if (preferences != null) {
            preferences.edit().remove(KEY_BASE_URL).apply();
        }
    }

    private static boolean isValid(String value) {
        if (value.isEmpty()) {
            return false;
        }
        String withScheme = value.startsWith("http://") || value.startsWith("https://")
                ? value : "https://" + value;
        Uri uri = Uri.parse(withScheme);
        return uri.getHost() != null && !uri.getHost().isEmpty();
    }

    /** 补协议、去尾部斜杠；空值回落到默认地址 */
    private static String normalize(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return DEFAULT_BASE_URL;
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://" + value;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
