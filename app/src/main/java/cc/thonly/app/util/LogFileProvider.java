package cc.thonly.app.util;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 把 {@link AppLog} 的日志文件暴露成一个 content URI，供分享用。
 *
 * <p>没有引 AndroidX，所以自己写一个最小 Provider 代替 FileProvider——
 * Android 7 之后不允许直接分享 {@code file://} 链接。
 */
public final class LogFileProvider extends ContentProvider {

    public static final String AUTHORITY = "cc.thonly.app.logs";
    private static final String FILE_NAME = "app.log";

    public static Uri contentUri() {
        return Uri.parse("content://" + AUTHORITY + "/" + FILE_NAME);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = AppLog.file();
        if (file == null || !file.exists()) {
            throw new FileNotFoundException("日志文件不存在");
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /** 部分接收方（微信、QQ）会读 DISPLAY_NAME 和 SIZE，不实现会显示成未知文件 */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File file = AppLog.file();
        long size = file != null && file.exists() ? file.length() : 0L;
        String[] columns = projection != null
                ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) {
                row[i] = FILE_NAME;
            } else if (OpenableColumns.SIZE.equals(columns[i])) {
                row[i] = size;
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "text/plain";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
