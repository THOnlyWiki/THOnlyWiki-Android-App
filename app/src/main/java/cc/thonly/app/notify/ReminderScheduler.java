package cc.thonly.app.notify;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import cc.thonly.app.data.FavoriteEvent;
import cc.thonly.app.data.Favorites;
import cc.thonly.app.data.ReminderSettings;

/**
 * 给收藏的活动排提醒。
 *
 * <p>活动数据只有日期没有时刻，所以固定两个点：
 * <ul>
 *     <li><b>前一天 20:00</b>——「明天 · XXX」，晚上正好规划第二天</li>
 *     <li><b>当天 08:00</b>——「今天 · XXX」，出门前最后一遍</li>
 * </ul>
 *
 * <p>每次收藏变化、开机、App 启动都会整表重排（幂等）：先按记录下来的 ID 全部取消，
 * 再按当前收藏重新排。这样取消收藏也能把闹钟撤掉。
 */
public final class ReminderScheduler {

    /** 前一天提醒的钟点 */
    private static final int EVENING_HOUR = 20;
    /** 当天提醒的钟点 */
    private static final int MORNING_HOUR = 8;

    /** 前一天 = 1，当天 = 2（拼进 alarmId，保证同一活动两个闹钟不打架） */
    public static final int SLOT_EVENING = 1;
    public static final int SLOT_MORNING = 2;

    private static final String PREFS = "wikikernel.reminders.scheduled";
    private static final String KEY_IDS = "ids";

    private ReminderScheduler() {
    }

    /** 整表重排（幂等） */
    public static void reschedule(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager alarms = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) {
            return;
        }
        cancelScheduled(app, alarms);

        if (!new ReminderSettings(app).isEnabled() || !Notifications.canPost(app)) {
            return;
        }
        Notifications.ensureChannel(app);

        Set<String> scheduled = new HashSet<String>();
        long now = System.currentTimeMillis();
        for (FavoriteEvent favorite : new Favorites(app).list()) {
            // 已取消的活动不提醒
            if (favorite.isCancelled()) {
                continue;
            }
            // 收藏里存的是 yyyy-MM-dd（不是 EventDate 的 yyyy/M/d），用 FavoriteEvent 自己的解析
            Calendar date = favorite.calendar();
            if (date == null) {
                continue;
            }
            long evening = triggerAt(date, -1, EVENING_HOUR);
            long morning = triggerAt(date, 0, MORNING_HOUR);
            if (evening > now) {
                schedule(app, alarms, favorite, SLOT_EVENING, evening);
                scheduled.add(String.valueOf(alarmId(favorite.id(), SLOT_EVENING)));
            }
            if (morning > now) {
                schedule(app, alarms, favorite, SLOT_MORNING, morning);
                scheduled.add(String.valueOf(alarmId(favorite.id(), SLOT_MORNING)));
            }
        }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putStringSet(KEY_IDS, scheduled).apply();
    }

    /** 活动当天 offsetDays 天后的 hour 点整 */
    private static long triggerAt(Calendar date, int offsetDays, int hour) {
        Calendar calendar = (Calendar) date.clone();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (offsetDays != 0) {
            calendar.add(Calendar.DAY_OF_MONTH, offsetDays);
        }
        return calendar.getTimeInMillis();
    }

    private static int alarmId(int eventId, int slot) {
        return eventId * 10 + slot;
    }

    private static void schedule(Context context, AlarmManager alarms, FavoriteEvent favorite,
                                 int slot, long triggerAt) {
        PendingIntent pending = pendingIntent(context, favorite, slot);
        if (pending == null) {
            return;
        }
        boolean exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms();
        try {
            if (exact) {
                // 打盹模式下也能响，国内 ROM 上比 setExact 更容易活下来
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            }
        } catch (SecurityException ignored) {
            // 精确闹钟权限被回收：退回非精确，宁可晚一点也别不响
            try {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            } catch (SecurityException ignoredToo) {
                // 彻底没权限就算了，不影响 App 其它功能
            }
        }
    }

    private static PendingIntent pendingIntent(Context context, FavoriteEvent favorite, int slot) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("cc.thonly.app.REMIND." + alarmId(favorite.id(), slot));
        intent.putExtra("eventId", favorite.id());
        intent.putExtra("name", favorite.name());
        intent.putExtra("region", favorite.region());
        intent.putExtra("dateText", favorite.dateText());
        intent.putExtra("slot", slot);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, alarmId(favorite.id(), slot), intent, flags);
    }

    /** 按上次记录下来的 ID 全部取消 */
    private static void cancelScheduled(Context context, AlarmManager alarms) {
        Set<String> ids = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY_IDS, new HashSet<String>());
        for (String raw : ids) {
            int id;
            try {
                id = Integer.parseInt(raw);
            } catch (NumberFormatException ignored) {
                continue;
            }
            Intent intent = new Intent(context, ReminderReceiver.class);
            intent.setAction("cc.thonly.app.REMIND." + id);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pending = PendingIntent.getBroadcast(context, id, intent, flags);
            alarms.cancel(pending);
            pending.cancel();
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_IDS).apply();
    }
}
