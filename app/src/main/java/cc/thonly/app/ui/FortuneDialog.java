package cc.thonly.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.TextView;
import android.widget.Toast;

import cc.thonly.app.R;
import cc.thonly.app.data.FortuneSlip;
import cc.thonly.app.data.FortuneSlips;

/** 今日签文：每天一张，同一天同一台设备结果不变。 */
public final class FortuneDialog {

    private FortuneDialog() {
    }

    public static void show(Activity activity) {
        FortuneSlip slip = FortuneSlips.today(activity);
        if (slip == null) {
            Toast.makeText(activity, R.string.fortune_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder message = new StringBuilder();
        for (String line : slip.body()) {
            message.append(line).append('\n');
        }
        if (!slip.sign().isEmpty()) {
            message.append('\n').append(activity.getString(R.string.fortune_author, slip.sign()))
                    .append('\n');
        }
        message.append(activity.getString(R.string.fortune_footer));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(slip.title())
                .setMessage(message.toString().trim())
                .setPositiveButton(R.string.dialog_close, null)
                .show();

        // 凶签标题标红，吉签保持默认。
        // android.R.id.alertTitle 不是公开资源，只能按名字查。
        if (slip.isBadLuck()) {
            int titleId = activity.getResources().getIdentifier("alertTitle", "id", "android");
            if (titleId != 0) {
                TextView title = dialog.findViewById(titleId);
                if (title != null) {
                    title.setTextColor(activity.getColor(R.color.status_cancelled));
                }
            }
        }
    }
}
