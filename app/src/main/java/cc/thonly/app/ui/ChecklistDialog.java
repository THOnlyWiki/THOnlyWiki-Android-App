package cc.thonly.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import cc.thonly.app.R;
import cc.thonly.app.data.Checklist;

/**
 * 勾选清单弹窗，行前装备和 Cosplay 备忘共用。
 *
 * <p>内置条目写死，用户还能自己加——自定义条目存在本地，下次打开还在，
 * 每行右侧的 × 可以删掉它。勾选状态即时落盘。
 */
public final class ChecklistDialog {

    private final Activity activity;
    private final String key;
    private final String[] presets;
    private final Checklist checklist;
    private final LinearLayout container;

    private ChecklistDialog(Activity activity, String key, String[] presets) {
        this.activity = activity;
        this.key = key;
        this.presets = presets;
        this.checklist = new Checklist(activity, key);

        int padding = dp(20);
        this.container = new LinearLayout(activity);
        this.container.setOrientation(LinearLayout.VERTICAL);
        this.container.setPadding(padding, dp(8), padding, dp(4));
    }

    public static void show(final Activity activity, String key, int titleRes, String[] presets) {
        final ChecklistDialog dialog = new ChecklistDialog(activity, key, presets);
        dialog.rebuild();

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(dialog.container);

        new AlertDialog.Builder(activity)
                .setTitle(titleRes)
                .setView(scroll)
                .setPositiveButton(R.string.dialog_close, null)
                .setNeutralButton(R.string.checklist_clear, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        dialog.checklist.clear();
                        dialog.rebuild();
                    }
                })
                .show();
    }

    /** 内置条目在前、自定义在后，最后是「添加」入口 */
    private void rebuild() {
        container.removeAllViews();
        for (String item : presets) {
            container.addView(createRow(item, false));
        }
        for (String item : checklist.customItems()) {
            container.addView(createRow(item, true));
        }
        container.addView(createAddEntry());
    }

    private View createRow(final String item, boolean custom) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        CheckBox box = new CheckBox(activity);
        box.setText(item);
        box.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        box.setChecked(checklist.isChecked(item));
        box.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        box.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                checklist.set(item, isChecked);
            }
        });
        row.addView(box);

        if (custom) {
            TextView remove = new TextView(activity);
            remove.setText("×");
            remove.setTextColor(activity.getColor(R.color.text_tertiary));
            remove.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            remove.setGravity(Gravity.CENTER);
            remove.setPadding(dp(12), dp(8), dp(4), dp(8));
            remove.setContentDescription(activity.getString(R.string.checklist_remove));
            remove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    checklist.removeCustom(item);
                    rebuild();
                }
            });
            row.addView(remove);
        }
        return row;
    }

    private View createAddEntry() {
        TextView add = new TextView(activity);
        add.setText(R.string.checklist_add);
        add.setTextColor(activity.getColor(R.color.accent));
        add.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        add.setPadding(0, dp(10), 0, dp(6));
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptAdd();
            }
        });
        return add;
    }

    private void promptAdd() {
        final EditText input = new EditText(activity);
        input.setHint(R.string.checklist_new_item_hint);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        input.setSingleLine(true);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.checklist_new_item)
                .setView(input)
                .setPositiveButton(R.string.edit_save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim();
                        if (name.isEmpty() || allItems().contains(name)) {
                            Toast.makeText(activity, R.string.checklist_item_invalid,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        checklist.addCustom(name);
                        rebuild();
                    }
                })
                .setNegativeButton(R.string.edit_cancel, null)
                .show();
    }

    private List<String> allItems() {
        List<String> items = new ArrayList<String>();
        for (String preset : presets) {
            items.add(preset);
        }
        items.addAll(checklist.customItems());
        return items;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
