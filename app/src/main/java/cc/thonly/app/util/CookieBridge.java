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

    public static void clear(Context context) {
        CookieManager manager = CookieManager.getInstance();
        manager.removeAllCookies(null);
        manager.flush();
    }
}
