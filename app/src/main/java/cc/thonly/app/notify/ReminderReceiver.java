package cc.thonly.app.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import cc.thonly.app.R;

/**
 * 闹钟到点 → 发通知。
 *
 * <p>发完顺手整表重排一次：这样过期的闹钟被清掉，
 * 用户之后改了收藏也能自愈（不依赖 App 被杀之前的状态）。
 */
public final class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        int eventId = intent.getIntExtra("eventId", 0);
        String name = intent.getStringExtra("name");
        String region = intent.getStringExtra("region");
        String dateText = intent.getStringExtra("dateText");
        int slot = intent.getIntExtra("slot", ReminderScheduler.SLOT_MORNING);
        if (eventId == 0 || name == null) {
            return;
        }

        boolean evening = slot == ReminderScheduler.SLOT_EVENING;
        String title = context.getString(evening ? R.string.reminder_title_evening
                : R.string.reminder_title_today, name);

        StringBuilder body = new StringBuilder();
        if (dateText != null && !dateText.isEmpty()) {
            body.append(dateText);
        }
        if (region != null && !region.isEmpty()) {
            if (body.length() > 0) {
                body.append(" · ");
            }
            body.append(region);
        }
        if (body.length() == 0) {
            body.append(context.getString(R.string.reminder_body_fallback));
        }

        Notifications.show(context, eventId, title, body.toString());
        ReminderScheduler.reschedule(context);
    }
}
