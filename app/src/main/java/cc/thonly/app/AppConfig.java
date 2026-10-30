package cc.thonly.app;

/**
 * 编译期常量。
 *
 * <p>站点地址不在这里了——它运行时可改（设置 → 服务器地址），
 * 见 {@link cc.thonly.app.data.ServerConfig}。这里只留协议层的东西。
 */
public final class AppConfig {

    /** 带上自定义 UA，方便服务端区分 App 流量 */
    public static final String USER_AGENT = "ThonlyApp/1.0 (Android)";

    public static final int CONNECT_TIMEOUT_MS = 10_000;
    public static final int READ_TIMEOUT_MS = 20_000;

    private AppConfig() {
    }
}
