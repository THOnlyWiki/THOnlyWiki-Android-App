package cc.thonly.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import cc.thonly.app.R;
import cc.thonly.app.data.AppVersion;
import cc.thonly.app.data.ServerConfig;
import cc.thonly.app.data.SettingsStore;
import cc.thonly.app.data.UpdateChecker;
import cc.thonly.app.util.AppLog;
import cc.thonly.app.util.DeviceInfo;
import cc.thonly.app.util.FileCache;
import cc.thonly.app.util.LogFileProvider;

/** 设置页：版本信息、自动检查更新、下载最新版、清除缓存。 */
public final class SettingsController {

    private final Activity activity;
    private final SettingsStore settings;
    private final UpdateChecker updateChecker;
    private final FileCache cache;
    private final AppVersion currentVersion;

    private final TextView versionValue;
    private final TextView aboutText;
    private final TextView deviceModelValue;
    private final TextView webViewValue;
    private final TextView serverValue;
    private final Switch autoCheckSwitch;

    public SettingsController(Activity activity, View root, SettingsStore settings,
                              UpdateChecker updateChecker, AppVersion currentVersion, FileCache cache) {
        this.activity = activity;
        this.settings = settings;
        this.updateChecker = updateChecker;
        this.currentVersion = currentVersion;
        this.cache = cache;
        this.versionValue = root.findViewById(R.id.versionValue);
        this.aboutText = root.findViewById(R.id.aboutText);
        this.deviceModelValue = root.findViewById(R.id.deviceModelValue);
        this.webViewValue = root.findViewById(R.id.webViewValue);
        this.serverValue = root.findViewById(R.id.serverValue);
        this.autoCheckSwitch = root.findViewById(R.id.autoCheckUpdate);

        bind(root);
    }

    public void onShown() {
        versionValue.setText(currentVersion.name());
        autoCheckSwitch.setChecked(settings.isAutoCheckUpdate());
        updateServerValue();
    }

    private void updateServerValue() {
        serverValue.setText(ServerConfig.baseUrl());
        // 改过地址就标成强调色，一眼能看出不是正式站点
        serverValue.setTextColor(activity.getColor(ServerConfig.isCustom()
                ? R.color.accent : R.color.text_secondary));
    }

    /** 改服务器地址，用于和本地后端联调 */
    private void editServer() {
        final EditText input = new EditText(activity);
        input.setText(ServerConfig.baseUrl());
        input.setSelection(input.getText().length());
        input.setSingleLine(true);
        input.setHint(R.string.settings_server_hint);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.settings_server)
                .setView(input)
                .setPositiveButton(R.string.edit_save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (ServerConfig.setBaseUrl(input.getText().toString())) {
                            Toast.makeText(activity, R.string.settings_server_saved,
                                    Toast.LENGTH_SHORT).show();
                            // 旧的 EventStore、WebView 还拿着老地址，整体重建最省事
                            activity.recreate();
                        } else {
                            Toast.makeText(activity, R.string.settings_server_invalid,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNeutralButton(R.string.settings_server_reset,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ServerConfig.reset();
                                Toast.makeText(activity, R.string.settings_server_saved,
                                        Toast.LENGTH_SHORT).show();
                                activity.recreate();
                            }
                        })
                .setNegativeButton(R.string.edit_cancel, null)
                .show();
    }

    private void bind(View root) {
        autoCheckSwitch.setChecked(settings.isAutoCheckUpdate());
        autoCheckSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settings.setAutoCheckUpdate(isChecked);
            }
        });

        root.findViewById(R.id.rowCheckUpdate).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkNow();
            }
        });
        root.findViewById(R.id.rowServer).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editServer();
            }
        });
        root.findViewById(R.id.rowDownload).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UpdateDialog.openDownloadPage(activity);
            }
        });
        root.findViewById(R.id.rowClearCache).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cache.clear();
                toast(R.string.toast_cache_cleared);
            }
        });
        root.findViewById(R.id.rowExportLog).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportLog();
            }
        });

        versionValue.setText(currentVersion.name());
        aboutText.setText(activity.getString(R.string.settings_about, currentVersion.name()));

        // 排查问题时用户报这两个值最有用
        deviceModelValue.setText(DeviceInfo.model(activity));
        webViewValue.setText(DeviceInfo.webViewVersion(activity));
    }

    private void checkNow() {
        toast(R.string.update_checking);
        updateChecker.check(new UpdateChecker.Listener() {
            @Override
            public void onLatestVersion(AppVersion latest) {
                if (latest.compareTo(currentVersion) > 0) {
                    UpdateDialog.show(activity, currentVersion, latest, settings);
                } else {
                    toast(R.string.update_is_latest);
                }
            }

            @Override
            public void onCheckFailed() {
                toast(R.string.update_check_failed);
            }
        });
    }

    private void toast(int messageRes) {
        Toast.makeText(activity, messageRes, Toast.LENGTH_SHORT).show();
    }

    /** 把日志文件通过系统分享面板发出去，用户可以直接发到群里 */
    private void exportLog() {
        File file = AppLog.file();
        if (file == null || !file.exists() || file.length() == 0L) {
            toast(R.string.settings_export_log_empty);
            return;
        }

        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_STREAM, LogFileProvider.contentUri())
                .putExtra(Intent.EXTRA_SUBJECT,
                        activity.getString(R.string.settings_export_log_subject))
                // 附一段环境信息，接收方就算打不开文件也能看到基本情况
                .putExtra(Intent.EXTRA_TEXT, environmentSummary())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivity(Intent.createChooser(intent,
                    activity.getString(R.string.settings_export_log_chooser)));
        } catch (ActivityNotFoundException e) {
            toast(R.string.toast_no_share_app);
        }
    }

    private String environmentSummary() {
        return "版本 " + currentVersion.name()
                + " · " + DeviceInfo.model(activity)
                + " · WebView " + DeviceInfo.webViewVersion(activity);
    }
}
