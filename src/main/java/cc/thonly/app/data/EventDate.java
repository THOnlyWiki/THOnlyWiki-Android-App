package cc.thonly.app.data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 活动日期值对象。
 *
 * <p>服务端 date 是自由文本，实测存在这几种形态：
 * <ul>
 *   <li>{@code 2026/2/13} —— 可解析到日</li>
 *   <li>{@code 2026年末}、{@code 2026年3月末-4月初（待定）} —— 只能拿到年份</li>
 *   <li>{@code 暂无} —— 完全未知</li>
 * </ul>
 * 所以这里同时保留原文和解析结果，展示时优先用原文。
 */
public final class EventDate {

    private static final Pattern FULL_DATE =
            Pattern.compile("^(\\d{4})\\s*/\\s*(\\d{1,2})\\s*/\\s*(\\d{1,2})$");
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
            if (isValidMonthDay(month, day)) {
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

    private static boolean isValidMonthDay(int month, int day) {
        return month >= 1 && month <= 12 && day >= 1 && day <= 31;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
