package cc.thonly.app.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import cc.thonly.app.AppConfig;

/** 极简 HTTP 客户端，只做 GET 取文本，不引第三方网络库。 */
public final class HttpClient {

    private HttpClient() {
    }

    public static String getText(String url) throws IOException {
        return getText(url, null);
    }

    /** 带 Cookie 的 GET（组队这类要登录态的只读接口用） */
    public static String getText(String url, String cookieHeader) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(AppConfig.CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(AppConfig.READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", AppConfig.USER_AGENT);
            if (cookieHeader != null && !cookieHeader.isEmpty()) {
                connection.setRequestProperty("Cookie", cookieHeader);
            }

            int status = connection.getResponseCode();
            boolean success = status >= 200 && status < 300;
            InputStream stream = success ? connection.getInputStream() : connection.getErrorStream();
            String body = readAll(stream);
            if (!success) {
                throw new IOException("HTTP " + status);
            }
            return body;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            builder.append(buffer, 0, count);
        }
        reader.close();
        return builder.toString();
    }
}
