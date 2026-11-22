package cc.thonly.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import cc.thonly.app.util.AppLog;

/**
 * 本地关注列表。
 *
 * <p>key 用「年份:id」——活动 id 只在本年度内唯一（.ics 路径就是 /excel/calendar/2026/199.ics），
 * 跨年可能撞号。
 *
 * <p>除了 id，还存一份活动快照（名称、日期、地区、是否取消），
 * 这样「我的行程」不用联网、也不用切年份就能列出来。
 */
public final class Favorites {

    private static final String PREFS_NAME = "favorites";
    private static final String KEY_ITEMS = "items";
    private static final String KEY_LAST_SYNC = "last_full_sync";
    /** 早期版本只存了 id，没有快照，读出来仍让 ★ 亮着 */
    private static final String KEY_LEGACY_IDS = "ids";

    private final SharedPreferences preferences;
    private final Context context;
    private final Map<String, FavoriteEvent> items = new LinkedHashMap<String, FavoriteEvent>();
    private final Set<String> legacyIds = new HashSet<String>();

    public Favorites(Context context) {
        this.context = context.getApplicationContext();
        String server = ServerConfig.baseUrl();
        String scope = ServerConfig.DEFAULT_BASE_URL.equals(server) ? PREFS_NAME
                : PREFS_NAME + "-" + UUID.nameUUIDFromBytes(server.getBytes(StandardCharsets.UTF_8));
        this.preferences = this.context.getSharedPreferences(scope, Context.MODE_PRIVATE);
        loadItems();
        Set<String> stored = preferences.getStringSet(KEY_LEGACY_IDS, null);
        if (stored != null) {
            legacyIds.addAll(stored);
            legacyIds.removeAll(items.keySet());
        }
    }

    private void loadItems() {
        String raw = preferences.getString(KEY_ITEMS, null);
        if (raw == null || raw.isEmpty()) {
            return;
        }
        try {
            JSONObject root = new JSONObject(raw);
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject json = root.optJSONObject(key);
                if (json == null) {
                    continue;
                }
                int[] parsed = parseKey(key);
                if (parsed == null) {
                    continue;
                }
                items.put(key, new FavoriteEvent(parsed[0], parsed[1],
                        json.optString("name", ""),
                        json.optString("region", ""),
                        json.optString("date", ""),
                        readStatus(json), json.optBoolean("unavailable", false),
                        json.isNull("previousDate") ? null : json.optString("previousDate", "")));
            }
        } catch (Exception e) {
            AppLog.e("favorites", "读取关注列表失败", e);
        }
    }

    private static EventStatus readStatus(JSONObject json) {
        try {
            return EventStatus.valueOf(json.optString("status"));
        } catch (IllegalArgumentException ignored) {
            return json.optBoolean("cancelled", false) ? EventStatus.CANCELLED : EventStatus.UNKNOWN;
        }
    }

    private void saveItems() {
        JSONObject root = new JSONObject();
        try {
            for (Map.Entry<String, FavoriteEvent> entry : items.entrySet()) {
                FavoriteEvent event = entry.getValue();
                JSONObject json = new JSONObject();
                json.put("name", event.name());
                json.put("region", event.region());
                json.put("date", event.rawDate());
                json.put("cancelled", event.status().isCancelled());
                json.put("status", event.status().name());
                json.put("unavailable", event.isUnavailable());
                json.put("previousDate", event.previousDate());
                root.put(entry.getKey(), json);
            }
        } catch (Exception e) {
            AppLog.e("favorites", "保存关注列表失败", e);
        }
        preferences.edit().putString(KEY_ITEMS, root.toString())
                .putStringSet(KEY_LEGACY_IDS, new HashSet<String>(legacyIds)).apply();
    }

    public boolean isFavorite(int year, int id) {
        String key = key(year, id);
        return items.containsKey(key) || legacyIds.contains(key);
    }

    /** @return 切换后的状态 */
    public boolean toggle(int year, Event event) {
        String key = key(year, event.id());
        boolean nowFavorite;
        if (isFavorite(year, event.id())) {
            items.remove(key);
            nowFavorite = false;
        } else {
            items.put(key, snapshotOf(year, event, eventDate(event)));
            nowFavorite = true;
        }
        legacyIds.remove(key);
        saveItems();
        // 收藏变了就重排提醒：新收藏要排，取消收藏要把闹钟撤掉
        cc.thonly.app.notify.ReminderScheduler.reschedule(context);
        return nowFavorite;
    }

    private static String eventDate(Event event) {
        if (event.date().isResolved()) {
            return String.format(java.util.Locale.US, "%04d-%02d-%02d",
                    event.date().year(), event.date().month(), event.date().day());
        }
        return "";
    }

    private static FavoriteEvent snapshotOf(int year, Event event, String date) {
        return new FavoriteEvent(year, event.id(), event.name(), event.region(), date,
                event.status(), false, null);
    }

    /** 缓存只用于补齐早期的 ID 收藏，只有网络返回的完整年度列表可以更新已有快照。 */
    public boolean reconcile(EventData data, boolean fresh) {
        int year = data.year();
        boolean changed = false;
        Set<String> seen = new HashSet<String>();
        for (Event event : data.events()) {
            String key = key(year, event.id());
            FavoriteEvent old = items.get(key);
            if (old == null) {
                if (!legacyIds.remove(key)) {
                    continue;
                }
                items.put(key, snapshotOf(year, event, eventDate(event)));
                changed = true;
            } else if (fresh) {
                FavoriteEvent updated = old.updated(event.name(), event.region(), eventDate(event), event.status());
                if (old != updated) {
                    items.put(key, updated);
                    changed = true;
                }
            }
            if (fresh) {
                seen.add(key);
            }
        }
        if (fresh) {
            for (Map.Entry<String, FavoriteEvent> entry : items.entrySet()) {
                FavoriteEvent old = entry.getValue();
                if (old.year() == year && !seen.contains(entry.getKey()) && !old.isUnavailable()) {
                    entry.setValue(old.markUnavailable());
                    changed = true;
                }
            }
        }
        if (changed) {
            saveItems();
        }
        return changed;
    }

    public FavoriteEvent find(int year, int id) {
        return items.get(key(year, id));
    }

    public Set<Integer> years() {
        Set<Integer> years = new TreeSet<Integer>();
        for (FavoriteEvent event : items.values()) {
            years.add(event.year());
        }
        for (String key : legacyIds) {
            int[] parsed = parseKey(key);
            if (parsed != null) {
                years.add(parsed[0]);
            }
        }
        return years;
    }

    public long lastFullSyncAt() {
        return preferences.getLong(KEY_LAST_SYNC, 0);
    }

    public void recordFullSync(long timestamp) {
        preferences.edit().putLong(KEY_LAST_SYNC, timestamp).apply();
    }

    /** 按日期升序，日期未定的排最后 */
    public List<FavoriteEvent> list() {
        List<FavoriteEvent> all = new ArrayList<FavoriteEvent>(items.values());
        Collections.sort(all, new Comparator<FavoriteEvent>() {
            @Override
            public int compare(FavoriteEvent left, FavoriteEvent right) {
                long a = left.daysUntil();
                long b = right.daysUntil();
                boolean aUnknown = a == Long.MIN_VALUE;
                boolean bUnknown = b == Long.MIN_VALUE;
                if (aUnknown && bUnknown) {
                    return 0;
                }
                if (aUnknown) {
                    return 1;
                }
                if (bUnknown) {
                    return -1;
                }
                return a < b ? -1 : (a > b ? 1 : 0);
            }
        });
        return all;
    }

    public int count() {
        return items.size() + legacyIds.size();
    }

    private static String key(int year, int id) {
        return year + ":" + id;
    }

    private static int[] parseKey(String key) {
        int index = key.indexOf(':');
        if (index <= 0) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(key.substring(0, index)),
                    Integer.parseInt(key.substring(index + 1))};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
