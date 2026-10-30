package cc.thonly.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import cc.thonly.app.AppConfig;
import cc.thonly.app.R;
import cc.thonly.app.data.ServerConfig;
import cc.thonly.app.util.QrCode;

/** 二维码生成：输入内容实时出图，可以复制原文或跳去扫码。 */
public final class QrDialog {

    private static final int QR_SIZE = 480;

    private QrDialog() {
    }

    public static void show(final Activity activity) {
        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_qr, null);
        final EditText input = content.findViewById(R.id.qrInput);
        final ImageView image = content.findViewById(R.id.qrImage);
        final TextView empty = content.findViewById(R.id.qrEmpty);

        input.setText(ServerConfig.baseUrl() + "/");
        input.setSelection(input.getText().length());

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                render(s.toString(), image, empty);
            }
        });
        render(input.getText().toString(), image, empty);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.tool_qr_title)
                .setView(content)
                .setPositiveButton(R.string.dialog_close, null)
                .setNeutralButton(R.string.qr_copy, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        copy(activity, input.getText().toString());
                    }
                })
                .setNegativeButton(R.string.qr_scan, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        activity.startActivity(new Intent(activity, ScanActivity.class));
                    }
                })
                .show();
    }

    private static void render(String text, ImageView image, TextView empty) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            image.setImageDrawable(null);
            empty.setText(R.string.qr_empty);
            empty.setVisibility(View.VISIBLE);
            return;
        }
        try {
            Bitmap bitmap = QrCode.encode(trimmed, QR_SIZE);
            image.setImageBitmap(bitmap);
            empty.setVisibility(View.GONE);
        } catch (Exception e) {
            image.setImageDrawable(null);
            // 内容太长时 ZXing 会抛异常
            empty.setText(R.string.qr_too_long);
            empty.setVisibility(View.VISIBLE);
        }
    }

    static void copy(Activity activity, String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        ClipboardManager manager =
                (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText("qr", text.trim()));
            Toast.makeText(activity, R.string.qr_copied, Toast.LENGTH_SHORT).show();
        }
    }
}
