package cc.thonly.app;

/** 全局配置。改服务器地址只需要动这里。 */
public final class AppConfig {

    /** 站点根地址，接口与 .ics 文件都基于它拼接 */
    public static final String BASE_URL = "https://thonly.cc";

    /** 活动数据接口前缀，见 docs/API.md */
    public static final String ACTIVITY_API = BASE_URL + "/api/thonly-activity";

    /** 带上自定义 UA，方便服务端区分 App 流量 */
    public static final String USER_AGENT = "ThonlyApp/1.0 (Android)";

    /** 服务端维护的最新版本号，纯文本，例如 1.1.0 */
    public static final String UPDATE_CHECK_URL =
            BASE_URL + "/index.php?title=WikiKernel%3AAppVersion&action=raw";

    /** 下载页，检查到新版本时引导用户去这里 */
    public static final String DOWNLOAD_PAGE_URL =
            BASE_URL + "/WikiApp%E5%AE%A2%E6%88%B7%E7%AB%AF%E4%B8%8B%E8%BD%BD";

    public static final int CONNECT_TIMEOUT_MS = 10_000;
    public static final int READ_TIMEOUT_MS = 20_000;

    private AppConfig() {
    }
}
