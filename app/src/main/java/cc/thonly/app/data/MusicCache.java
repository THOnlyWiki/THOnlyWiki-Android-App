package cc.thonly.app.data;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

import cc.thonly.app.util.AppLog;

/**
 * 曲子缓存：下载到 filesDir/music，文件名是 URL 的 SHA-1。
 *
 * <p>缓存过的曲子直接播本地文件，省流量也快。
 */
public final class MusicCache {

    /** 源站在 Cloudflare 后面，带上浏览器 UA 更稳 */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final String REFERER = "https://thbwiki.cc/";

    private final File directory;

    public MusicCache(Context context) {
        this.directory = new File(context.getFilesDir(), "music");
        if (!directory.exists()) {
            //noinspection ResultOfMethodCallIgnored
            directory.mkdirs();
        }
    }

    public File fileFor(String url) {
        return new File(directory, sha1(url) + ".mp3");
    }

    public boolean isCached(String url) {
        File file = fileFor(url);
        return file.exists() && file.length() > 0;
    }

    /** 已缓存就直接返回，否则下载。阻塞调用，放后台线程。 */
    public File download(String url) throws IOException {
        File target = fileFor(url);
        if (target.exists() && target.length() > 0) {
            return target;
        }
        File temp = new File(target.getAbsolutePath() + ".part");

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Referer", REFERER);
        try {
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK && code != 206) {
                throw new IOException("HTTP " + code);
            }
            InputStream input = connection.getInputStream();
            FileOutputStream output = new FileOutputStream(temp);
            byte[] buffer = new byte[16384];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            output.close();
            input.close();

            if (!temp.renameTo(target)) {
                throw new IOException("重命名失败");
            }
            return target;
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            AppLog.e("music", "缓存失败 " + url, e);
            throw e;
        } finally {
            connection.disconnect();
        }
    }

    public long cachedSize() {
        long total = 0;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                total += file.length();
            }
        }
        return total;
    }

    private static String sha1(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(text.getBytes("UTF-8"));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            return String.valueOf(text.hashCode());
        }
    }
}
