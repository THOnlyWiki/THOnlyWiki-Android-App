package cc.thonly.app;

/** 全局配置。改服务器地址只需要动这里。 */
public final class AppConfig {

    /** 站点根地址，接口与 .ics 文件都基于它拼接 */
    public static final String BASE_URL = "https://thonly.cc";

    /** 活动数据接口前缀，见 docs/API.md */
    public static final String ACTIVITY_API = BASE_URL + "/api/thonly-activity";

    /** 带上自定义 UA，方便服务端区分 App 流量 */
    public static final String USER_AGENT = "ThonlyApp/1.0 (Android)";

    public static final int CONNECT_TIMEOUT_MS = 10_000;
    public static final int READ_TIMEOUT_MS = 20_000;

    private AppConfig() {
    }
}
