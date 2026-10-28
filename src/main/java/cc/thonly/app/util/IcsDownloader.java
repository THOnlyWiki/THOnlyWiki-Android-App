package cc.thonly.app.util;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import java.io.IOException;

/** 把 .ics 交给系统下载管理器，下载完点通知就能加入系统日历。 */
public final class IcsDownloader {

    private static final String MIME_TYPE = "text/calendar";
    private static final int MAX_FILE_NAME_LENGTH = 40;
    private static final String ILLEGAL_FILE_CHARS = "[\\\\/:*?\"<>|\\s]+";

    private IcsDownloader() {
    }

    public static void enqueue(Context context, String url, String title) throws IOException {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setMimeType(MIME_TYPE);
        request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS, buildFileName(title));

        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) {
            throw new IOException("系统下载服务不可用");
        }
        manager.enqueue(request);
    }

    static String buildFileName(String title) {
        String name = title == null ? "" : title.trim().replaceAll(ILLEGAL_FILE_CHARS, "_");
        if (name.length() > MAX_FILE_NAME_LENGTH) {
            name = name.substring(0, MAX_FILE_NAME_LENGTH);
        }
        return (name.isEmpty() ? "activity" : name) + ".ics";
    }
}
