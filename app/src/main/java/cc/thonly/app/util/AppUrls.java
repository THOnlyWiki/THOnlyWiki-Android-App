package cc.thonly.app.util;

import android.net.Uri;

import java.util.Locale;

import cc.thonly.app.data.ServerConfig;

/** 站点 URL 工具：统一拼 {@code open_reference=app}，方便服务端区分来自 App 的访问。 */
public final class AppUrls {

    /** 首页（URL 里是中文，必须预先百分号编码） */
    private static final String HOME_PATH = "/%E9%A6%96%E9%A1%B5";

    private static final String PARAM_REFERENCE = "open_reference";
    private static final String VALUE_REFERENCE = "app";

    private AppUrls() {
    }

    /**
     * 当前服务器（可能是设置页里改过的局域网地址）的首页。
     *
     * <p>不能做成 static final：ServerConfig 在 Application 启动时才初始化，
     * 而且用户随时可以改地址，提前算好的常量会一直指向正式站。
     */
    public static String home() {
        return ServerConfig.baseUrl() + HOME_PATH;
    }

    public static boolean isInternal(Uri uri) {
        String host = uri == null ? null : uri.getHost();
        if (host == null) {
            return false;
        }
        String baseHost = Uri.parse(ServerConfig.baseUrl()).getHost();
        if (baseHost == null || baseHost.isEmpty()) {
            return false;
        }
        host = host.toLowerCase(Locale.ROOT);
        baseHost = baseHost.toLowerCase(Locale.ROOT);
        return host.equals(baseHost) || host.endsWith("." + baseHost);
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
