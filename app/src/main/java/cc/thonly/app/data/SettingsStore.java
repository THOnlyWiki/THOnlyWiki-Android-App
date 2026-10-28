package cc.thonly.app.data;

import android.content.Context;
import android.content.SharedPreferences;

/** 本地设置项。集中放这里，避免各处散落 SharedPreferences key。 */
public final class SettingsStore {

    private static final String PREFS_NAME = "settings";
    private static final String KEY_AUTO_CHECK_UPDATE = "auto_check_update";
    private static final String KEY_DISMISSED_VERSION = "dismissed_version";

    private final SharedPreferences preferences;

    public SettingsStore(Context context) {
        this.preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isAutoCheckUpdate() {
        return preferences.getBoolean(KEY_AUTO_CHECK_UPDATE, true);
    }

    public void setAutoCheckUpdate(boolean enabled) {
        preferences.edit().putBoolean(KEY_AUTO_CHECK_UPDATE, enabled).apply();
    }

    /** 用户点过「不再提醒」的那个版本，避免同一个版本反复弹窗 */
    public String dismissedVersion() {
        String value = preferences.getString(KEY_DISMISSED_VERSION, "");
        return value == null ? "" : value;
    }

    public void dismissVersion(String version) {
        preferences.edit().putString(KEY_DISMISSED_VERSION, version == null ? "" : version).apply();
    }
}
