package cc.thonly.app.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 活动提醒的开关。
 *
 * <p>默认**开**：这个 App 的核心价值就是「别错过活动」，
 * 默认关掉等于让用户永远发现不了它。首次进设置页会引导开通知权限。
 */
public final class ReminderSettings {

    private static final String PREFS = "wikikernel.reminders";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_GUIDED = "guided";

    private final SharedPreferences preferences;

    public ReminderSettings(Context context) {
        this.preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isEnabled() {
        return preferences.getBoolean(KEY_ENABLED, true);
    }

    public void setEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /** 后台限制的说明弹过没有（只弹一次，别每次开开关都烦人） */
    public boolean hasShownGuide() {
        return preferences.getBoolean(KEY_GUIDED, false);
    }

    public void markGuideShown() {
        preferences.edit().putBoolean(KEY_GUIDED, true).apply();
    }
}
