package cc.thonly.app.data;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 活动日期值对象。
 *
 * <p>服务端 date 是自由文本，实测存在这几种形态：
 * <ul>
 *   <li>{@code 2026/2/13} —— 可解析到日</li>
 *   <li>{@code 2026-02-13} —— 收藏快照里存的就是这种（{@code FavoriteEvent}），必须一起认</li>
 *   <li>{@code 2026年2月13日} —— 编辑页手填的</li>
 *   <li>{@code 2026年末}、{@code 2026年3月末-4月初（待定）} —— 只能拿到年份</li>
 *   <li>{@code 暂无} —— 完全未知</li>
 * </ul>
 * 所以这里同时保留原文和解析结果，展示时优先用原文。
 */
public final class EventDate {

    /**
     * 年月日：分隔符统一成 {@code [-/.年月]} 混用都认。
     *
     * <p>之前只认 {@code /}，而收藏快照存的是 {@code yyyy-MM-dd}，
     * 结果收藏的活动全被判成「日期未定」，提醒一条都排不出来。
     */
    private static final Pattern FULL_DATE = Pattern.compile(
            "^(\\d{4})\\s*[-/.年]\\s*(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})\\s*日?$");
    private static final Pattern YEAR_ONLY = Pattern.compile("^(\\d{4})");

    private final String raw;
    private final int year;
    private final int month;
    private final int day;

    private EventDate(String raw, int year, int month, int day) {
        this.raw = raw;
        this.year = year;
        this.month = month;
        this.day = day;
    }

    public static EventDate parse(String raw) {
        String text = raw == null ? "" : raw.trim();

        Matcher full = FULL_DATE.matcher(text);
        if (full.matches()) {
            int year = parseInt(full.group(1));
            int month = parseInt(full.group(2));
            int day = parseInt(full.group(3));
            if (isValidMonthDay(year, month, day)) {
                return new EventDate(text, year, month, day);
            }
        }

        Matcher yearOnly = YEAR_ONLY.matcher(text);
        if (yearOnly.find()) {
            return new EventDate(text, parseInt(yearOnly.group(1)), 0, 0);
        }

        return new EventDate(text, 0, 0, 0);
    }

    /** 能否定位到具体某一天，日历视图只展示能定位的 */
    public boolean isResolved() {
        return month > 0 && day > 0;
    }

    /** 解析结果转成 Calendar（未定档返回 null）；提醒排期用 */
    public Calendar toCalendar() {
        if (!isResolved()) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month - 1, day, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    public String raw() {
        return raw;
    }

    public int year() {
        return year;
    }

    public int month() {
        return month;
    }

    public int day() {
        return day;
    }

    /** 月份 1-12，日按当月实际天数校验（2 月 30 日这种脏数据直接判无效） */
    private static boolean isValidMonthDay(int year, int month, int day) {
        if (month < 1 || month > 12 || day < 1) {
            return false;
        }
        Calendar probe = Calendar.getInstance();
        probe.set(year, month - 1, 1);
        return day <= probe.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
