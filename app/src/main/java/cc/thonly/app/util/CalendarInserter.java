package cc.thonly.app.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.CalendarContract;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import cc.thonly.app.R;
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
            List<ResolveInfo> handlers = queryCalendarApps(activity, intent);
            if (handlers.size() > 1) {
                // 装了多个日历时自己弹列表让用户选。
                // 不用 Intent.createChooser——MIUI 会直接把 ACTION_INSERT 解析掉，
                // 选择器根本不显示（实测：数到 2 个应用仍然直接进了系统日历）。
                showPicker(activity, intent, handlers);
            } else {
                activity.startActivity(intent);
            }
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    private static List<ResolveInfo> queryCalendarApps(Activity activity, Intent intent) {
        List<ResolveInfo> handlers = activity.getPackageManager()
                .queryIntentActivities(intent, 0);
        return handlers == null ? new ArrayList<ResolveInfo>() : handlers;
    }

    private static void showPicker(final Activity activity, final Intent intent,
                                   final List<ResolveInfo> handlers) {
        final PackageManager packageManager = activity.getPackageManager();
        CharSequence[] labels = new CharSequence[handlers.size()];
        for (int i = 0; i < handlers.size(); i++) {
            labels[i] = handlers.get(i).loadLabel(packageManager);
        }

        new AlertDialog.Builder(activity)
                .setTitle(R.string.choose_calendar_app)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ResolveInfo info = handlers.get(which);
                        Intent explicit = new Intent(intent);
                        explicit.setComponent(new ComponentName(
                                info.activityInfo.packageName, info.activityInfo.name));
                        try {
                            activity.startActivity(explicit);
                        } catch (ActivityNotFoundException ignored) {
                            // 理论上不会发生，用户刚在列表里看到它
                        }
                    }
                })
                .setNegativeButton(R.string.edit_cancel, null)
                .show();
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
