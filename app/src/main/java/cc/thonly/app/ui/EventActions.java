package cc.thonly.app.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import java.io.IOException;

import cc.thonly.app.R;
import cc.thonly.app.data.Event;
import cc.thonly.app.util.IcsDownloader;

/**
 * 活动详情弹窗、加入日历、复制群号。
 *
 * <p>这些行为同时被列表页和日历页使用，所以单独抽出来，避免两边各写一遍。
 */
public final class EventActions implements EventInteraction {

    /** 存储权限请求码，宿主 Activity 需要转回来 */
    public static final int REQUEST_STORAGE = 1001;

    private final Activity activity;

    private String pendingUrl;
    private String pendingTitle;

    public EventActions(Activity activity) {
        this.activity = activity;
    }

    @Override
    public void onEventSelected(Event event) {
        showDetail(event);
    }

    @Override
    public void onAddToCalendar(Event event) {
        addToCalendar(event);
    }

    private void showDetail(final Event event) {
        StringBuilder message = new StringBuilder();
        appendLine(message, R.string.label_date, fallback(event.date().raw(), R.string.unknown_date));
        appendLine(message, R.string.label_region, fallback(event.region(), R.string.unknown_region));
        appendLine(message, R.string.label_category, activity.getString(event.category().labelRes()));
        appendLine(message, R.string.label_status, fallback(event.status().label(), R.string.value_unknown));
        if (!event.qqGroup().isEmpty()) {
            appendLine(message, R.string.label_qq, event.qqGroup());
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(event.name())
                .setMessage(message.toString().trim())
                .setNegativeButton(R.string.dialog_close, null);

        if (event.hasIcsFile()) {
            builder.setPositiveButton(R.string.dialog_add_to_calendar, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    addToCalendar(event);
                }
            });
        }
        if (!event.qqGroup().isEmpty()) {
            builder.setNeutralButton(R.string.dialog_copy_qq, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    copyToClipboard(event.qqGroup());
                }
            });
        }
        builder.show();
    }

    private void addToCalendar(Event event) {
        if (!event.hasIcsFile()) {
            toast(R.string.toast_no_ics);
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingUrl = event.icsUrl();
            pendingTitle = event.name();
            activity.requestPermissions(
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
            return;
        }
        download(event.icsUrl(), event.name());
    }

    public void onRequestPermissionsResult(int requestCode, int[] grantResults) {
        if (requestCode != REQUEST_STORAGE || pendingUrl == null) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            download(pendingUrl, pendingTitle);
        } else {
            toast(R.string.toast_no_permission);
        }
        pendingUrl = null;
    }

    private void download(String url, String title) {
        try {
            IcsDownloader.enqueue(activity, url, title);
            toast(R.string.toast_download_started);
        } catch (IOException e) {
            toast(R.string.toast_download_failed);
        }
    }

    private void copyToClipboard(String text) {
        ClipboardManager manager =
                (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null) {
            return;
        }
        manager.setPrimaryClip(ClipData.newPlainText("qq", text));
        toast(activity.getString(R.string.toast_copied, text));
    }

    private void appendLine(StringBuilder builder, int labelRes, String value) {
        builder.append(activity.getString(R.string.detail_line, activity.getString(labelRes), value))
                .append('\n');
    }

    private String fallback(String value, int fallbackRes) {
        return (value == null || value.isEmpty()) ? activity.getString(fallbackRes) : value;
    }

    private void toast(int messageRes) {
        Toast.makeText(activity, messageRes, Toast.LENGTH_SHORT).show();
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }
}
