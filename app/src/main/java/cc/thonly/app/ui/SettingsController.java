package cc.thonly.app.ui;

import android.app.Activity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import cc.thonly.app.R;
import cc.thonly.app.data.AppVersion;
import cc.thonly.app.data.SettingsStore;
import cc.thonly.app.data.UpdateChecker;
import cc.thonly.app.util.FileCache;

/** 设置页：版本信息、自动检查更新、下载最新版、清除缓存。 */
public final class SettingsController {

    private final Activity activity;
    private final SettingsStore settings;
    private final UpdateChecker updateChecker;
    private final FileCache cache;
    private final AppVersion currentVersion;

    private final TextView versionValue;
    private final TextView aboutText;
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
        this.autoCheckSwitch = root.findViewById(R.id.autoCheckUpdate);

        bind(root);
    }

    public void onShown() {
        versionValue.setText(currentVersion.name());
        autoCheckSwitch.setChecked(settings.isAutoCheckUpdate());
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

        versionValue.setText(currentVersion.name());
        aboutText.setText(activity.getString(R.string.settings_about, currentVersion.name()));
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
}
