package cc.thonly.app.util;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 应用内日志，写到 {@code filesDir/logs/app.log}，供用户导出分享。
 *
 * <p>为什么要自己写一份：Android 4.1 之后普通应用读不到 logcat（需要 READ_LOGS，
 * 只有 debuggable 或 root 才有），所以线上排查只能靠自己记。
 *
 * <p>写入失败一律吞掉——日志不该影响主流程。文件超过 256KB 就清空重来。
 */
public final class AppLog {

    private static final String TAG = "AppLog";
    private static final String DIR_NAME = "logs";
    private static final String FILE_NAME = "app.log";
    private static final long MAX_BYTES = 256L * 1024L;
    private static final Object LOCK = new Object();
    private static final SimpleDateFormat TIME =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    private static File logFile;

    private AppLog() {
    }

    public static void init(Context context) {
        File dir = new File(context.getFilesDir(), DIR_NAME);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "日志目录创建失败");
            return;
        }
        logFile = new File(dir, FILE_NAME);
    }

    public static File file() {
        return logFile;
    }

    public static void i(String tag, String message) {
        write("I", tag, message, null);
    }

    public static void e(String tag, String message, Throwable error) {
        write("E", tag, message, error);
    }

    public static void clear() {
        synchronized (LOCK) {
            if (logFile != null && logFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                logFile.delete();
            }
        }
    }

    private static void write(String level, String tag, String message, Throwable error) {
        if (logFile == null) {
            return;
        }
        StringBuilder line = new StringBuilder(160);
        line.append(TIME.format(new Date()))
                .append(' ').append(level)
                .append('/').append(tag)
                .append(": ").append(message)
                .append('\n');
        if (error != null) {
            line.append(Log.getStackTraceString(error)).append('\n');
        }

        synchronized (LOCK) {
            try {
                if (logFile.length() > MAX_BYTES) {
                    //noinspection ResultOfMethodCallIgnored
                    logFile.delete();
                }
                FileOutputStream output = new FileOutputStream(logFile, true);
                output.write(line.toString().getBytes("UTF-8"));
                output.close();
            } catch (IOException e) {
                Log.w(TAG, "写日志失败: " + e);
            }
        }
    }
}
