package cc.thonly.app.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/** 以文件为后端的简易 key-value 缓存，用于断网时兜底。 */
public final class FileCache {

    private final File directory;

    public FileCache(File directory) {
        this.directory = directory;
    }

    public String read(String key) {
        File file = fileOf(key);
        if (!file.exists()) {
            return null;
        }
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, count);
            }
            String content = builder.toString();
            return content.isEmpty() ? null : content;
        } catch (IOException e) {
            return null;
        } finally {
            closeQuietly(reader);
        }
    }

    public void write(String key, String content) {
        if (content == null) {
            return;
        }
        Writer writer = null;
        try {
            if (!directory.exists() && !directory.mkdirs()) {
                return;
            }
            writer = new OutputStreamWriter(new FileOutputStream(fileOf(key)), StandardCharsets.UTF_8);
            writer.write(content);
            writer.flush();
        } catch (IOException ignored) {
            // 缓存失败不影响主流程
        } finally {
            closeQuietly(writer);
        }
    }

    private File fileOf(String key) {
        return new File(directory, key);
    }

    /** 清空缓存目录，设置页的「清除本地缓存」用 */
    public void clear() {
        deleteChildren(directory);
    }

    private static void deleteChildren(File directory) {
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                deleteChildren(child);
            }
            child.delete();
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
                // 忽略
            }
        }
    }
}
