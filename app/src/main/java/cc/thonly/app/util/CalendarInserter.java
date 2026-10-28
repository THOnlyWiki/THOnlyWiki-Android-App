package cc.thonly.app.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.provider.CalendarContract;

import java.util.Calendar;
import java.util.TimeZone;

import cc.thonly.app.data.Event;

/**
 * 直接把活动塞进系统日历的「新建日程」界面。
 *
 * <p>用 {@link Intent#ACTION_INSERT} 而不是往数据库里写，好处是不需要任何日历权限，
 * 由日历应用自己处理；用户确认保存即可。
 */
public final class CalendarInserter {

    private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;
    private static final String UTC = "UTC";
    private static final String SOURCE_URL = "https://thonly.cc";
    /** CalendarContract.Events 的目录 MIME type，ACTION_INSERT 需要它才能匹配到日历应用 */
    private static final String CALENDAR_DIR_TYPE = "vnd.android.cursor.dir/event";

    private CalendarInserter() {
    }

    /**
     * @return true 表示已成功拉起日历应用；false 表示没有日历应用或日期不可解析
     */
    public static boolean insert(Activity activity, Event event) {
        if (!event.date().isResolved()) {
            return false;
        }

        long begin = startOfDayUtc(event);
        Intent intent = new Intent(Intent.ACTION_INSERT)
                // 必须带上 MIME type，否则系统匹配不到日历应用（实测会 ActivityNotFoundException）
                .setDataAndType(CalendarContract.Events.CONTENT_URI, CALENDAR_DIR_TYPE)
                .putExtra(CalendarContract.Events.TITLE, event.name())
                .putExtra(CalendarContract.Events.EVENT_LOCATION, event.region())
                .putExtra(CalendarContract.Events.DESCRIPTION, buildDescription(event))
                .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + MILLIS_PER_DAY);

        try {
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    /** 全天日程按 UTC 零点计算，避免时区偏移导致日期变成前一天 */
    private static long startOfDayUtc(Event event) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(UTC));
        calendar.clear();
        calendar.set(event.date().year(), event.date().month() - 1, event.date().day());
        return calendar.getTimeInMillis();
    }

    private static String buildDescription(Event event) {
        StringBuilder description = new StringBuilder();
        if (!event.qqGroup().isEmpty()) {
            description.append("QQ 群：").append(event.qqGroup()).append('\n');
        }
        description.append("来源：").append(SOURCE_URL);
        return description.toString();
    }
}
