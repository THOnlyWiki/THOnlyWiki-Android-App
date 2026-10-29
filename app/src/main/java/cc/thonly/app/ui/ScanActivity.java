package cc.thonly.app.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Toast;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import java.util.List;

import cc.thonly.app.R;
import cc.thonly.app.util.AppLog;

/**
 * 扫码页：相机预览 + 逐帧解码。
 *
 * <p>用老的 android.hardware.Camera（不引 CameraX，省体积）。
 * ZXing 的定位算法对旋转不敏感，所以没做画面旋转校正。
 */
public final class ScanActivity extends Activity
        implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private static final int REQUEST_CAMERA = 1;

    private SurfaceView previewView;
    private Camera camera;
    private int previewWidth;
    private int previewHeight;
    private boolean handled;
    private boolean surfaceReady;

    private final MultiFormatReader reader = new MultiFormatReader();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        previewView = findViewById(R.id.scanPreview);
        previewView.getHolder().addCallback(this);

        findViewById(R.id.scanClose).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_CAMERA
                && (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(this, R.string.scan_no_permission, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    // ------------------------------------------------------------ 预览

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        openCamera(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // 尺寸变了重开一次预览
        if (camera != null) {
            camera.stopPreview();
            try {
                camera.setPreviewDisplay(holder);
                camera.startPreview();
            } catch (Exception e) {
                AppLog.e("scan", "重开预览失败", e);
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        releaseCamera();
    }

    private void openCamera(SurfaceHolder holder) {
        if (camera != null) {
            return;
        }
        try {
            camera = Camera.open();
        } catch (Exception e) {
            AppLog.e("scan", "打开相机失败", e);
            Toast.makeText(this, R.string.scan_no_camera, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        try {
            Camera.Parameters params = camera.getParameters();
            Camera.Size size = choosePreviewSize(params.getSupportedPreviewSizes());
            if (size == null) {
                size = params.getPreviewSize();
            }
            if (size == null) {
                throw new IllegalStateException("没有可用的预览尺寸");
            }
            params.setPreviewSize(size.width, size.height);
            previewWidth = size.width;
            previewHeight = size.height;
            params.setPreviewFormat(android.graphics.ImageFormat.NV21);
            camera.setParameters(params);
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(holder);

            int bufferSize = previewWidth * previewHeight * 3 / 2;
            if (bufferSize <= 0) {
                throw new IllegalStateException("预览尺寸异常");
            }
            camera.addCallbackBuffer(new byte[bufferSize]);
            camera.setPreviewCallbackWithBuffer(this);
            camera.startPreview();
        } catch (Exception e) {
            AppLog.e("scan", "配置相机失败", e);
            releaseCamera();
            Toast.makeText(this, R.string.scan_no_camera, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /** 挑一个中等分辨率的预览尺寸，太大解码慢 */
    private static Camera.Size choosePreviewSize(List<Camera.Size> sizes) {
        if (sizes == null || sizes.isEmpty()) {
            return null;
        }
        Camera.Size best = sizes.get(0);
        int bestDiff = Integer.MAX_VALUE;
        for (Camera.Size size : sizes) {
            int diff = Math.abs(size.width - 1280) + Math.abs(size.height - 720);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = size;
            }
        }
        return best;
    }

    private void releaseCamera() {
        if (camera != null) {
            try {
                camera.setPreviewCallbackWithBuffer(null);
                camera.stopPreview();
                camera.release();
            } catch (Exception ignored) {
                // 释放失败无所谓
            }
            camera = null;
        }
    }

    // ------------------------------------------------------------ 解码

    @Override
    public void onPreviewFrame(byte[] data, Camera cam) {
        if (data != null && !handled) {
            decode(data);
        }
        if (cam != null) {
            cam.addCallbackBuffer(data);
        }
    }

    private void decode(byte[] data) {
        PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                data, previewWidth, previewHeight, 0, 0, previewWidth, previewHeight, false);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        try {
            final String text = reader.decodeWithState(bitmap).getText();
            handled = true;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onDecoded(text);
                }
            });
        } catch (NotFoundException ignored) {
            // 这一帧没有码，继续
        } catch (Exception e) {
            AppLog.e("scan", "解码异常", e);
        } finally {
            reader.reset();
        }
    }

    private void onDecoded(final String text) {
        releaseCamera();
        new AlertDialog.Builder(this)
                .setTitle(R.string.scan_result_title)
                .setMessage(text)
                .setPositiveButton(R.string.qr_copy, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        QrDialog.copy(ScanActivity.this, text);
                        finish();
                    }
                })
                .setNeutralButton(R.string.scan_open, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        openLink(text);
                        finish();
                    }
                })
                .setNegativeButton(R.string.scan_again, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        handled = false;
                        if (surfaceReady) {
                            openCamera(previewView.getHolder());
                        }
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        finish();
                    }
                })
                .show();
    }

    private void openLink(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            Toast.makeText(this, R.string.scan_not_link, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.music_no_browser, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
    }
}
