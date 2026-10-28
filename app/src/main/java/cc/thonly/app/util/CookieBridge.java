package cc.thonly.app.util;

import android.content.Context;
import android.webkit.CookieManager;

import cc.thonly.app.AppConfig;

/** 把登录拿到的 Cookie 注入 WebView，让主站页面保持登录态。 */
public final class CookieBridge {

    private CookieBridge() {
    }

    /** @param cookieHeader 形如 {@code JSESSIONID=xxx; wiki_token=yyy} */
    public static void apply(Context context, String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return;
        }
        CookieManager manager = CookieManager.getInstance();
        manager.setAcceptCookie(true);
        for (String pair : cookieHeader.split("; ")) {
            if (pair.contains("=")) {
                manager.setCookie(AppConfig.BASE_URL, pair + "; path=/");
            }
        }
        manager.flush();
    }

    public static void clear() {
        CookieManager manager = CookieManager.getInstance();
        manager.removeAllCookies(null);
        manager.flush();
    }

    /**
     * 当前对站点的 Cookie，以 WebView 的 CookieManager 为准。
     *
     * <p>登录时我们把 Cookie 存进了 SharedPreferences，但网页加载过程中服务端可能换发新会话，
     * 那份存档就过期了。写接口要用这里取的最新值。
     */
    public static String current() {
        String cookie = CookieManager.getInstance().getCookie(AppConfig.BASE_URL);
        return cookie == null ? "" : cookie;
    }
}
