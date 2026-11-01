package cc.thonly.app.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 开机后闹钟全丢，重新排一遍。
 *
 * <p>有些 ROM 的「应用更新」也会清掉闹钟，所以 App 启动时也会再排一次（见 MainActivity）。
 */
public final class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            ReminderScheduler.reschedule(context);
        }
    }
}
