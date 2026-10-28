package cc.thonly.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import cc.thonly.app.R;
import cc.thonly.app.data.ActivityEditor;
import cc.thonly.app.data.Event;
import cc.thonly.app.data.EventApiException;
import cc.thonly.app.data.EventCategory;

/**
 * 活动的新增 / 编辑弹窗。
 *
 * <p>字段与站点表格一致：名称（必填）、类型、状态、日期（可勾「日期未定」）、地区、QQ 群。
 * 保存失败时不关闭弹窗，把服务端的 message 直接显示在弹窗里；
 * 删除要二次确认，且只有编辑已有条目时才出现。
 */
public final class EventEditDialog {

    public interface Listener {
        void onSaved();

        /** 服务端说会话已失效，本地登录态需要清掉 */
        void onSessionExpired();
    }

    private static final String DATE_NONE = "暂无";
    private static final String[] STATUS_LABELS = {"计划", "定档", "如期", "延期", "取消"};

    private final Activity activity;
    private final ActivityEditor editor;
    private final Listener listener;

    private AlertDialog dialog;
    private EditText nameInput;
    private EditText dateInput;
    private EditText regionInput;
    private EditText qqInput;
    private Spinner categorySpinner;
    private Spinner statusSpinner;
    private CheckBox dateNoneCheck;
    private TextView errorView;

    private int year;
    private Event editing;

    public EventEditDialog(Activity activity, ActivityEditor editor, Listener listener) {
        this.activity = activity;
        this.editor = editor;
        this.listener = listener;
    }

    /** @param event null 表示新增 */
    public void show(int year, Event event) {
        this.year = year;
        this.editing = event;

        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_event_edit, null);
        nameInput = content.findViewById(R.id.editName);
        dateInput = content.findViewById(R.id.editDate);
        regionInput = content.findViewById(R.id.editRegion);
        qqInput = content.findViewById(R.id.editQq);
        categorySpinner = content.findViewById(R.id.editCategory);
        statusSpinner = content.findViewById(R.id.editStatus);
        dateNoneCheck = content.findViewById(R.id.editDateNone);
        errorView = content.findViewById(R.id.editError);

        setupSpinners();
        fill(event);

        dateNoneCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                dateInput.setEnabled(!isChecked);
            }
        });
        dateInput.setEnabled(!dateNoneCheck.isChecked());

        dialog = new AlertDialog.Builder(activity)
                .setTitle(event == null ? R.string.edit_add_title : R.string.edit_edit_title)
                .setView(content)
                .setPositiveButton(R.string.edit_save, null)
                .setNegativeButton(R.string.edit_cancel, null)
                .setNeutralButton(R.string.edit_delete, null)
                .create();
        dialog.show();

        // 自己接管保存按钮，失败时不关闭弹窗
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit();
            }
        });

        Button deleteButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (event == null) {
            deleteButton.setVisibility(View.GONE);
        } else {
            deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmDelete();
                }
            });
        }
    }

    // ------------------------------------------------------------ 填充

    private void setupSpinners() {
        List<String> categories = new ArrayList<String>();
        for (EventCategory category : EventCategory.values()) {
            categories.add(activity.getString(category.labelRes()));
        }
        categorySpinner.setAdapter(new DropdownAdapter(activity, categories));

        List<String> statuses = new ArrayList<String>();
        for (String label : STATUS_LABELS) {
            statuses.add(label);
        }
        statusSpinner.setAdapter(new DropdownAdapter(activity, statuses));
    }

    private void fill(Event event) {
        if (event == null) {
            return;
        }
        nameInput.setText(event.name());
        regionInput.setText(event.region());
        qqInput.setText(event.qqGroup());
        categorySpinner.setSelection(indexOfCategory(event.category()), false);
        statusSpinner.setSelection(indexOfStatus(event.status().label()), false);

        String raw = event.date().raw();
        if (raw == null || raw.isEmpty() || DATE_NONE.equals(raw)) {
            dateNoneCheck.setChecked(true);
        } else {
            dateInput.setText(raw);
        }
    }

    private int indexOfCategory(EventCategory category) {
        EventCategory[] values = EventCategory.values();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == category) {
                return i;
            }
        }
        return values.length - 1;
    }

    private int indexOfStatus(String label) {
        for (int i = 0; i < STATUS_LABELS.length; i++) {
            if (STATUS_LABELS[i].equals(label)) {
                return i;
            }
        }
        return 0;
    }

    // ------------------------------------------------------------ 保存 / 删除

    private void submit() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            showError(R.string.edit_name_required);
            return;
        }
        boolean dateNone = dateNoneCheck.isChecked();
        String date = dateNone ? DATE_NONE : dateInput.getText().toString().trim();
        if (!dateNone && date.isEmpty()) {
            showError(R.string.edit_date_required);
            return;
        }

        final String finalName = name;
        final String finalDate = date;
        final String region = regionInput.getText().toString().trim();
        final String qq = qqInput.getText().toString().trim();
        final EventCategory category = EventCategory.values()[categorySpinner.getSelectedItemPosition()];
        final String status = STATUS_LABELS[statusSpinner.getSelectedItemPosition()];
        final String id = editing == null ? "" : String.valueOf(editing.id());

        setBusy(R.string.edit_saving);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final ActivityEditor.Result result = editor.save(
                            year, id, category, status, finalName, region, finalDate, qq);
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            handleResult(result);
                        }
                    });
                } catch (EventApiException e) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setIdle();
                            showError(R.string.auth_network_error);
                        }
                    });
                }
            }
        }, "event-save").start();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(activity)
                .setMessage(activity.getString(R.string.edit_delete_confirm, editing.name()))
                .setPositiveButton(R.string.edit_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface confirm, int which) {
                        deleteNow();
                    }
                })
                .setNegativeButton(R.string.edit_cancel, null)
                .show();
    }

    private void deleteNow() {
        final String id = String.valueOf(editing.id());
        setBusy(R.string.edit_deleting);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final ActivityEditor.Result result = editor.delete(year, id);
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            handleResult(result);
                        }
                    });
                } catch (EventApiException e) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setIdle();
                            showError(R.string.auth_network_error);
                        }
                    });
                }
            }
        }, "event-delete").start();
    }

    private void handleResult(ActivityEditor.Result result) {
        if (result.isSuccess()) {
            dialog.dismiss();
            listener.onSaved();
            return;
        }
        // 服务端说没登录 = 会话已经失效（超时或服务端重启），本地登录态得清掉
        if (result.message() != null && result.message().contains("登录")) {
            dialog.dismiss();
            editor.clearSession();
            Toast.makeText(activity, R.string.session_expired, Toast.LENGTH_LONG).show();
            listener.onSessionExpired();
            return;
        }
        setIdle();
        showError(result.message());
    }

    // ------------------------------------------------------------ 状态

    private void setBusy(int textRes) {
        Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        save.setEnabled(false);
        save.setText(textRes);
        errorView.setVisibility(View.GONE);
    }

    private void setIdle() {
        Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        save.setEnabled(true);
        save.setText(R.string.edit_save);
    }

    private void showError(int messageRes) {
        showError(activity.getString(messageRes));
    }

    private void showError(String message) {
        errorView.setText(message);
        errorView.setVisibility(View.VISIBLE);
    }
}
