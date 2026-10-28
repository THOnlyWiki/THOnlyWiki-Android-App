package cc.thonly.app.data;

import android.content.Context;
import android.content.SharedPreferences;

/** 登录态：用户名 + 服务端下发的 Cookie（JSESSIONID / wiki_token）。 */
public final class UserSession {

    private static final String PREFS_NAME = "session";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_COOKIES = "cookies";

    private final SharedPreferences preferences;

    public UserSession(Context context) {
        this.preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isLoggedIn() {
        return !username().isEmpty() && !cookies().isEmpty();
    }

    public String username() {
        String value = preferences.getString(KEY_USERNAME, "");
        return value == null ? "" : value;
    }

    /** 形如 {@code JSESSIONID=xxx; wiki_token=yyy} */
    public String cookies() {
        String value = preferences.getString(KEY_COOKIES, "");
        return value == null ? "" : value;
    }

    public void save(String username, java.util.List<String> cookiePairs) {
        StringBuilder builder = new StringBuilder();
        for (String pair : cookiePairs) {
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(pair);
        }
        preferences.edit()
                .putString(KEY_USERNAME, username == null ? "" : username)
                .putString(KEY_COOKIES, builder.toString())
                .apply();
    }

    public void clear() {
        preferences.edit().remove(KEY_USERNAME).remove(KEY_COOKIES).apply();
    }
}
