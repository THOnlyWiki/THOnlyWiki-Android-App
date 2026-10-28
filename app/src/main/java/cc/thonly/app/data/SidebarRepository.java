package cc.thonly.app.data;

import java.io.IOException;

import cc.thonly.app.AppConfig;
import cc.thonly.app.util.FileCache;
import cc.thonly.app.util.HttpClient;

/**
 * 拉取服务端下发的抽屉配置。
 *
 * <p>网络优先、失败回落缓存；都拿不到就返回空配置，App 用内置导航。
 * 配置文件很小（通常 1 KB 内），所以不做 TTL，每次启动都尝试取最新的。
 */
public final class SidebarRepository {

    private static final String SOURCE_URL =
            AppConfig.BASE_URL + "/index.php?title=WikiKernel%3ASidebar-App&action=raw";
    private static final String CACHE_KEY = "sidebar.json";

    private final FileCache cache;

    public SidebarRepository(FileCache cache) {
        this.cache = cache;
    }

    public AppSidebar load(AppVersion currentVersion) {
        try {
            String body = HttpClient.getText(SOURCE_URL);
            cache.write(CACHE_KEY, body);
            return AppSidebar.parse(body, currentVersion);
        } catch (IOException e) {
            String cached = cache.read(CACHE_KEY);
            return cached == null ? AppSidebar.empty() : AppSidebar.parse(cached, currentVersion);
        }
    }
}
