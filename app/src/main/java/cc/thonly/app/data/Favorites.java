package cc.thonly.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * 本地关注列表。
 *
 * <p>key 用「年份:id」——活动 id 只在本年度内唯一（.ics 路径就是 /excel/calendar/2026/199.ics），
 * 跨年可能撞号。只存本地，不跟账号同步：换手机丢了也不影响主流程。
 */
public final class Favorites {

    private static final String PREFS_NAME = "favorites";
    private static final String KEY_IDS = "ids";

    private final SharedPreferences preferences;
    private final Set<String> ids = new HashSet<String>();

    public Favorites(Context context) {
        this.preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> stored = preferences.getStringSet(KEY_IDS, null);
        if (stored != null) {
            ids.addAll(stored);
        }
    }

    public boolean isFavorite(int year, int id) {
        return ids.contains(key(year, id));
    }

    /** @return 切换后的状态 */
    public boolean toggle(int year, int id) {
        String key = key(year, id);
        boolean nowFavorite;
        if (ids.remove(key)) {
            nowFavorite = false;
        } else {
            ids.add(key);
            nowFavorite = true;
        }
        // getStringSet 返回的集合不能直接改，写回时给个副本
        preferences.edit().putStringSet(KEY_IDS, new HashSet<String>(ids)).apply();
        return nowFavorite;
    }

    public int count() {
        return ids.size();
    }

    private static String key(int year, int id) {
        return year + ":" + id;
    }
}
