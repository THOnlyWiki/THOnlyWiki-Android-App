package cc.thonly.app.data;

import org.json.JSONObject;

/** 一支组队帖（接口返回的摘要）。 */
public final class Team {

    private final long id;
    private final String title;
    private final String type;
    private final String note;
    private final int capacity;
    private final int joined;
    private final int remaining;
    private final String joinMode;
    private final String ownerName;
    private final String status;
    private final boolean mine;

    private Team(long id, String title, String type, String note, int capacity, int joined,
                 int remaining, String joinMode, String ownerName, String status, boolean mine) {
        this.id = id;
        this.title = title;
        this.type = type;
        this.note = note;
        this.capacity = capacity;
        this.joined = joined;
        this.remaining = remaining;
        this.joinMode = joinMode;
        this.ownerName = ownerName;
        this.status = status;
        this.mine = mine;
    }

    public static Team fromJson(JSONObject json) {
        return new Team(
                json.optLong("id"),
                json.optString("title", ""),
                json.optString("type", "company"),
                json.optString("note", ""),
                json.optInt("capacity", 2),
                json.optInt("joined", 1),
                json.optInt("remaining", 0),
                json.optString("joinMode", "approve"),
                json.optString("ownerName", ""),
                json.optString("status", "open"),
                json.optBoolean("mine", false));
    }

    public long id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String type() {
        return type;
    }

    public String note() {
        return note;
    }

    public int capacity() {
        return capacity;
    }

    public int joined() {
        return joined;
    }

    public int remaining() {
        return remaining;
    }

    /** approve 需要批准 / instant 立即进入 */
    public String joinMode() {
        return joinMode;
    }

    public String ownerName() {
        return ownerName;
    }

    public String status() {
        return status;
    }

    public boolean isMine() {
        return mine;
    }

    public boolean isOpen() {
        return "open".equals(status);
    }

    public boolean isFull() {
        return remaining <= 0;
    }
}
