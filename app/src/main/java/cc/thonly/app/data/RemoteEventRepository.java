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
import cc.thonly.app.util.FileCache;
import cc.thonly.app.util.HttpClient;

/**
 * 走 HTTP 的仓库实现。
 *
 * <p>取数策略：先读缓存 → 缓存没有就请求网络 → 网络失败再退回缓存。
 * 这样弱网或断网时仍能看到上一次的数据。
 */
public final class RemoteEventRepository implements EventRepository {

    /** 除了服务端给的年份，再按手机时间往后延两年，避免跨年后年份条断档 */
    private static final int YEARS_AHEAD = 2;

    private final FileCache cache;

    public RemoteEventRepository(FileCache cache) {
        this.cache = cache;
    }

    @Override
    public EventData load(int year, boolean forceRefresh) throws EventApiException {
        String cacheKey = "activity-" + year + ".json";
        String body = forceRefresh ? null : cache.read(cacheKey);
        EventApiException networkError = null;

        if (body == null) {
            try {
                body = HttpClient.getText(AppConfig.ACTIVITY_API + "/list?year=" + year);
                cache.write(cacheKey, body);
            } catch (IOException e) {
                networkError = new EventApiException("请求活动数据失败", e);
                body = cache.read(cacheKey);
            }
        }

        if (body == null) {
            throw networkError != null ? networkError : new EventApiException("没有可用数据");
        }
        return parse(year, body);
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
