package cc.thonly.app.notify;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import cc.thonly.app.R;
import cc.thonly.app.ui.MainActivity;

/** 通知渠道与发通知的薄封装（不引 androidx，走框架 API）。 */
public final class Notifications {

    public static final String CHANNEL_ID = "event_reminder";

    private Notifications() {
    }

    /** 建渠道（API 26+ 必须先建，重复调用无副作用）。 */
    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.reminder_channel_desc));
        channel.enableVibration(true);
        manager.createNotificationChannel(channel);
    }

    /** 现在能不能发通知（Android 13+ 需要运行时权限）。 */
    public static boolean canPost(Context context) {
        if (Build.VERSION.SDK_INT >= 33) {
            return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return manager != null && manager.areNotificationsEnabled();
    }

    /**
     * 发一条提醒。
     *
     * @param id    通知 ID（同一个 ID 会覆盖，这里用事件 ID 保证一场活动一条）
     * @param title 标题
     * @param text  正文
     */
    public static void show(Context context, int id, String title, String text) {
        if (!canPost(context)) {
            return;
        }
        ensureChannel(context);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_reminder)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(openApp(context, id));
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(id, builder.build());
        }
    }

    /** 点通知 → 打开 App 并落到「行程」页 */
    private static PendingIntent openApp(Context context, int id) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_OPEN_PAGE, MainActivity.PAGE_NAME_ITINERARY);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(context, id, intent, flags);
    }
}
