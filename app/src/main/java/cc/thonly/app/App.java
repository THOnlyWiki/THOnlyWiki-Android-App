package cc.thonly.app;

import android.app.Application;

import cc.thonly.app.data.ServerConfig;

/** 只做一件事：在进程启动时把服务器地址读进来。 */
public final class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ServerConfig.init(this);
    }
}
