package cc.thonly.app.data;

import org.json.JSONObject;

import java.util.Calendar;

import cc.thonly.app.AppConfig;

/** 一条活动记录，不可变对象。 */
public final class Event {

    private final int id;
    private final String name;
    private final String region;
    private final String qqGroup;
    private final String icsPath;
    private final EventCategory category;
    private final EventStatus status;
    private final EventDate date;

    private Event(int id, String name, String region, String qqGroup, String icsPath,
                  EventCategory category, EventStatus status, EventDate date) {
        this.id = id;
        this.name = name;
        this.region = region;
        this.qqGroup = qqGroup;
        this.icsPath = icsPath;
        this.category = category;
        this.status = status;
        this.date = date;
    }

    public static Event fromJson(JSONObject json) {
        return new Event(
                json.optInt("id"),
                text(json, "name"),
                text(json, "region"),
                text(json, "qq"),
                text(json, "icsUrl"),
                EventCategory.fromKey(text(json, "category")),
                EventStatus.fromLabel(text(json, "status")),
                EventDate.parse(text(json, "date")));
    }

    private static String text(JSONObject json, String key) {
        String value = json.optString(key, "");
        return value == null ? "" : value.trim();
    }

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String region() {
        return region;
    }

    public String qqGroup() {
        return qqGroup;
    }

    public EventCategory category() {
        return category;
    }

    public EventStatus status() {
        return status;
    }

    public EventDate date() {
        return date;
    }

    public boolean hasIcsFile() {
        return !icsPath.isEmpty();
    }

    /**
     * 活动是否已经过去。当天算「还没过去」——今天还有活动在进行。
     * 日期没定的一律当作没过期。
     */
    public boolean isPast() {
        if (!date.isResolved()) {
            return false;
        }
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        Calendar target = Calendar.getInstance();
        target.set(date.year(), date.month() - 1, date.day(), 0, 0, 0);
        target.set(Calendar.MILLISECOND, 0);
        return target.before(today);
    }

    /** 服务端给的是站内相对路径，这里补成完整 URL */
    public String icsUrl() {
        if (icsPath.isEmpty()) {
            return "";
        }
        return icsPath.startsWith("http") ? icsPath : ServerConfig.baseUrl() + icsPath;
    }
}
