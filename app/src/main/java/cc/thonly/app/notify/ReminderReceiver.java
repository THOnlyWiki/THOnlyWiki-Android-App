package cc.thonly.app.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import cc.thonly.app.R;
import cc.thonly.app.data.FavoriteEvent;
import cc.thonly.app.data.Favorites;
import cc.thonly.app.data.ServerConfig;

/**
 * 闹钟到点 → 发通知。
 *
 * <p>发完顺手整表重排一次：这样过期的闹钟被清掉，
 * 用户之后改了收藏也能自愈（不依赖 App 被杀之前的状态）。
 */
public final class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ServerConfig.baseUrl().equals(intent.getStringExtra("server"))) {
            return;
        }
        FavoriteEvent favorite = new Favorites(context).find(
                intent.getIntExtra("year", 0), intent.getIntExtra("eventId", 0));
        int slot = intent.getIntExtra("slot", ReminderScheduler.SLOT_MORNING);
        // 广播可能在改期或取消前已经排入系统队列，发通知前核对最新快照。
        if (favorite == null || !favorite.canRemind()
                || !favorite.rawDate().equals(intent.getStringExtra("date"))) {
            return;
        }

        boolean evening = slot == ReminderScheduler.SLOT_EVENING;
        String title = context.getString(evening ? R.string.reminder_title_evening
                : R.string.reminder_title_today, favorite.name());

        StringBuilder body = new StringBuilder();
        body.append(favorite.dateText());
        if (!favorite.region().isEmpty()) {
            body.append(" · ").append(favorite.region());
        }

        Notifications.show(context, ReminderScheduler.key(favorite, slot), slot, title, body.toString());
        ReminderScheduler.reschedule(context);
    }
}
