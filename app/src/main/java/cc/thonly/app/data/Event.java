package cc.thonly.app.data;

import org.json.JSONObject;

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

    /** 服务端给的是站内相对路径，这里补成完整 URL */
    public String icsUrl() {
        if (icsPath.isEmpty()) {
            return "";
        }
        return icsPath.startsWith("http") ? icsPath : AppConfig.BASE_URL + icsPath;
    }
}
