package cc.thonly.app.util;

import android.net.Uri;

import cc.thonly.app.AppConfig;

/** 站点 URL 工具：统一拼 {@code open_reference=app}，方便服务端区分来自 App 的访问。 */
public final class AppUrls {

    public static final String HOST = "thonly.cc";

    /** 首页（URL 里是中文，必须预先百分号编码） */
    public static final String HOME = AppConfig.BASE_URL + "/%E9%A6%96%E9%A1%B5";

    private static final String PARAM_REFERENCE = "open_reference";
    private static final String VALUE_REFERENCE = "app";

    private AppUrls() {
    }

    public static boolean isInternal(Uri uri) {
        String host = uri == null ? null : uri.getHost();
        if (host == null) {
            return false;
        }
        host = host.toLowerCase();
        return host.equals(HOST) || host.endsWith("." + HOST);
    }

    public static boolean hasReference(Uri uri) {
        return uri != null && uri.getQueryParameter(PARAM_REFERENCE) != null;
    }

    /** 站内地址补上 open_reference=app；站外地址原样返回 */
    public static String withReference(String url) {
        Uri uri = Uri.parse(url);
        if (!isInternal(uri) || hasReference(uri)) {
            return url;
        }
        return uri.buildUpon()
                .appendQueryParameter(PARAM_REFERENCE, VALUE_REFERENCE)
                .build()
                .toString();
    }
}
