package cc.thonly.app.data;

import java.util.Calendar;

/** 关注活动的一份快照，存本地，用来渲染「我的行程」。 */
public final class FavoriteEvent {

    private final int year;
    private final int id;
    private final String name;
    private final String region;
    /** yyyy-MM-dd，空串表示日期未定 */
    private final String date;
    private final EventStatus status;
    private final boolean unavailable;
    /** null 表示尚未观察到日期变化，空串表示原先未定档。 */
    private final String previousDate;

    public FavoriteEvent(int year, int id, String name, String region, String date,
                         EventStatus status, boolean unavailable, String previousDate) {
        this.year = year;
        this.id = id;
        this.name = name;
        this.region = region;
        this.date = date == null ? "" : date;
        this.status = status;
        this.unavailable = unavailable;
        this.previousDate = previousDate;
    }

    public int year() {
        return year;
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

    public EventStatus status() {
        return status;
    }

    public boolean isUnavailable() {
        return unavailable;
    }

    public String previousDate() {
        return previousDate;
    }

    public boolean canRemind() {
        return !unavailable && !status.isCancelled() && status != EventStatus.POSTPONED
                && calendar() != null;
    }

    FavoriteEvent updated(String newName, String newRegion, String newDate, EventStatus newStatus) {
        if (!unavailable && date.equals(newDate) && status == newStatus
                && name.equals(newName) && region.equals(newRegion)) {
            return this;
        }
        return new FavoriteEvent(year, id, newName, newRegion, newDate, newStatus, false,
                date.equals(newDate) ? previousDate : date);
    }

    FavoriteEvent markUnavailable() {
        return unavailable ? this
                : new FavoriteEvent(year, id, name, region, date, status, true, previousDate);
    }

    /** 原始日期 yyyy-MM-dd，未定档为空串 */
    public String rawDate() {
        return date;
    }

    /** 10月2日 / 日期待定 */
    public String dateText() {
        Calendar calendar = calendar();
        if (calendar == null) {
            return "日期待定";
        }
        return (calendar.get(Calendar.MONTH) + 1) + "月" + calendar.get(Calendar.DAY_OF_MONTH) + "日";
    }

    /** 距离今天还有几天；日期未定返回 Long.MIN_VALUE */
    public long daysUntil() {
        Calendar target = calendar();
        if (target == null) {
            return Long.MIN_VALUE;
        }
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        return Math.round((target.getTimeInMillis() - today.getTimeInMillis()) / 86400000.0);
    }

    /** 还没开始的（含今天） */
    public boolean isUpcoming() {
        return canRemind() && daysUntil() >= 0;
    }

    /**
     * 活动日期（未定档返回 null）；提醒排期要用，公开。
     *
     * <p>统一走 {@link EventDate}：收藏里存的是 {@code yyyy-MM-dd}，
     * 服务端给的是 {@code yyyy/M/d}，两种格式必须由同一个解析器认。
     */
    public Calendar calendar() {
        return EventDate.parse(date).toCalendar();
    }
}
