package cc.thonly.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Toast;

import cc.thonly.app.AppConfig;
import cc.thonly.app.R;
import cc.thonly.app.data.AppVersion;
import cc.thonly.app.data.SettingsStore;

/**
 * 版本更新提示。
 *
 * <p>勾选「不再提醒该版本」后，同一个版本不再弹窗；服务端发了更高版本仍会提醒。
 * 勾选框用自定义 View 而不是 {@code setMultiChoiceItems}——后者在 DeviceDefault
 * 主题下不会渲染出列表，实测是个坑。
 */
public final class UpdateDialog {

    private UpdateDialog() {
    }

    /** 只有服务端版本更新、且用户没屏蔽过这个版本时才弹 */
    public static void showIfNeeded(Activity activity, AppVersion current, AppVersion latest,
                                    SettingsStore settings) {
        if (latest.compareTo(current) <= 0) {
            return;
        }
        if (latest.name().equals(settings.dismissedVersion())) {
            return;
        }
        show(activity, current, latest, settings);
    }

    public static void show(final Activity activity, AppVersion current, final AppVersion latest,
                            final SettingsStore settings) {
        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_update, null);
        final CheckBox neverRemind = (CheckBox) content.findViewById(R.id.neverRemind);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_title)
                .setMessage(activity.getString(R.string.update_message,
                        current.name(), latest.name()))
                .setView(content)
                .setPositiveButton(R.string.update_go, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        openDownloadPage(activity);
                    }
                })
                .setNegativeButton(R.string.update_later, null)
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        if (neverRemind.isChecked()) {
                            settings.dismissVersion(latest.name());
                        }
                    }
                })
                .show();
    }

    public static void openDownloadPage(Activity activity) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(AppConfig.DOWNLOAD_PAGE_URL)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.toast_no_browser, Toast.LENGTH_SHORT).show();
        }
    }
}
