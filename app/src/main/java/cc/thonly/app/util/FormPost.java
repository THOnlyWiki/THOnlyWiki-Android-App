package cc.thonly.app.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import cc.thonly.app.AppConfig;

/**
 * 表单编码的 POST。
 *
 * <p>站点的写接口（登录、注册、活动增删改）都是 {@code application/x-www-form-urlencoded}，
 * 登录态走 Cookie 头，返回 {@code {code, message, ...}}。统一放这里，省得各处重写。
 */
public final class FormPost {

    /** 一次响应：状态码、正文、Set-Cookie 里捞出来的键值对 */
    public static final class Response {

        private final int status;
        private final String body;
        private final List<String> cookies;

        Response(int status, String body, List<String> cookies) {
            this.status = status;
            this.body = body;
            this.cookies = cookies;
        }

        public int status() {
            return status;
        }

        public String body() {
            return body;
        }

        public List<String> cookies() {
            return cookies;
        }
    }

    private FormPost() {
    }

    public static Response post(String url, Map<String, String> form, String cookieHeader)
            throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(AppConfig.CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(AppConfig.READ_TIMEOUT_MS);
            connection.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            connection.setRequestProperty("User-Agent", AppConfig.USER_AGENT);
            if (cookieHeader != null && !cookieHeader.isEmpty()) {
                connection.setRequestProperty("Cookie", cookieHeader);
            }

            OutputStream output = connection.getOutputStream();
            output.write(buildBody(form).getBytes(StandardCharsets.UTF_8));
            output.close();

            int status = connection.getResponseCode();
            String body = readAll(status < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream());
            return new Response(status, body, readCookies(connection));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String buildBody(Map<String, String> form) throws IOException {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(URLEncoder.encode(entry.getKey(), "UTF-8"))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(),
                            "UTF-8"));
        }
        return body.toString();
    }

    /** 只取 {@code name=value}，属性（Path/HttpOnly/…）交给调用方决定怎么用 */
    private static List<String> readCookies(HttpURLConnection connection) {
        List<String> cookies = new ArrayList<String>();
        for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
            if (entry.getKey() == null || !"Set-Cookie".equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            for (String raw : entry.getValue()) {
                String pair = raw.split(";", 2)[0].trim();
                if (pair.indexOf('=') > 0) {
                    cookies.add(pair);
                }
            }
        }
        return cookies;
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
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
