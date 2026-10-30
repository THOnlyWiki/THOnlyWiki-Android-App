package cc.thonly.app.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import cc.thonly.app.AppConfig;
import cc.thonly.app.util.AppLog;
import cc.thonly.app.util.FileCache;
import cc.thonly.app.util.HttpClient;

/**
 * 走 HTTP 的仓库实现。
 *
 * <p>取数策略（面向长期没网或服务端宕机的场景）：
 * <ol>
 *   <li>本地有存档且未过期 → 直接用存档，秒开</li>
 *   <li>存档过期或不存在 → 请求网络，成功后立刻落盘</li>
 *   <li>网络失败 → 退回任意一份存档（哪怕已过期），保证还有内容可看</li>
 * </ol>
 * 只有网络失败且完全没存档时才抛异常。
 */
public final class RemoteEventRepository implements EventRepository {

    /** 除了服务端给的年份，再按手机时间往后延两年，避免跨年后年份条断档 */
    private static final int YEARS_AHEAD = 2;

    /** 存档有效期：6 小时内不重复请求，过期后尝试更新一次 */
    private static final long CACHE_TTL_MS = 6L * 60L * 60L * 1000L;

    private final FileCache cache;

    public RemoteEventRepository(FileCache cache) {
        this.cache = cache;
    }

    @Override
    public LoadResult load(int year, boolean forceRefresh) throws EventApiException {
        String cacheKey = "activity-" + year + ".json";
        String cached = cache.read(cacheKey);
        long cachedAt = cache.lastModified(cacheKey);

        boolean cacheFresh = !forceRefresh
                && cached != null
                && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS;
        if (cacheFresh) {
            return new LoadResult(parse(year, cached), true, cachedAt);
        }

        try {
            String body = HttpClient.getText(ServerConfig.activityApi() + "/list?year=" + year);
            cache.write(cacheKey, body);
            return new LoadResult(parse(year, body), false, System.currentTimeMillis());
        } catch (IOException networkError) {
            // 网络不通：只要本地有存档就继续用，哪怕已经过期
            if (cached != null) {
                AppLog.e("api", "拉取 " + year + " 失败，改用本地存档", networkError);
                return new LoadResult(parse(year, cached), true, cachedAt);
            }
            AppLog.e("api", "拉取 " + year + " 失败，且没有本地存档", networkError);
            throw new EventApiException("请求活动数据失败", networkError);
        }
    }

    private EventData parse(int requestedYear, String body) throws EventApiException {
        try {
            JSONObject root = new JSONObject(body);

            List<Integer> years = new ArrayList<Integer>();
            JSONArray yearArray = root.optJSONArray("years");
            if (yearArray != null) {
                for (int i = 0; i < yearArray.length(); i++) {
                    years.add(yearArray.optInt(i));
                }
            }
            if (years.isEmpty()) {
                years.add(requestedYear);
            }

            List<Event> events = new ArrayList<Event>();
            JSONArray rows = root.optJSONArray("rows");
            if (rows != null) {
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.optJSONObject(i);
                    if (row != null) {
                        events.add(Event.fromJson(row));
                    }
                }
            }

            return new EventData(root.optInt("year", requestedYear), mergeYears(years), events);
        } catch (JSONException e) {
            throw new EventApiException("活动数据格式异常", e);
        }
    }

    /**
     * 服务端返回的年份 + 手机当前年份起两年。
     *
     * <p>站点数据会随年份增长，但客户端不该等它更新——按手机时间补两年，
     * 跨年后用户仍然能直接选到新年份（服务端对任意年份都会返回结构，没有数据就是空列表）。
     */
    private static List<Integer> mergeYears(List<Integer> serverYears) {
        Set<Integer> merged = new TreeSet<Integer>(serverYears);
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        for (int offset = 0; offset <= YEARS_AHEAD; offset++) {
            merged.add(currentYear + offset);
        }
        return new ArrayList<Integer>(merged);
    }
}
