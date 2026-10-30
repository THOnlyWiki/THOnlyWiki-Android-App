package cc.thonly.app.data;

import android.os.Handler;
import android.os.Looper;

import java.io.IOException;

import cc.thonly.app.AppConfig;
import cc.thonly.app.util.HttpClient;

/** 从站点拉最新版本号，回调保证在主线程。 */
public final class UpdateChecker {

    public interface Listener {
        void onLatestVersion(AppVersion latest);

        void onCheckFailed();
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void check(final Listener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                AppVersion latest = null;
                try {
                    latest = AppVersion.parse(HttpClient.getText(ServerConfig.updateCheckUrl()));
                } catch (IOException ignored) {
                    // 网络失败按「检查失败」处理
                }
                final AppVersion result = latest;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (result == null) {
                            listener.onCheckFailed();
                        } else {
                            listener.onLatestVersion(result);
                        }
                    }
                });
            }
        }, "update-check").start();
    }
}
