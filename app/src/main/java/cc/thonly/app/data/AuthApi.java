package cc.thonly.app.data;

import org.json.JSONException;
import org.json.JSONObject;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cc.thonly.app.AppConfig;

/**
 * 登录 / 注册接口。
 *
 * <p>与站点表单同源：POST {@code /api/user/login}、{@code /api/user/register}，
 * 表单编码，返回 {@code {code, success, message, data}}。
 * 成功后服务端下发 {@code JSESSIONID} 与（勾选记住登录时的）{@code wiki_token}，
 * 这里把 Set-Cookie 原样捞出来交给调用方保存。
 */
public final class AuthApi {

    private static final String LOGIN_PATH = "/api/user/login";
    private static final String REGISTER_PATH = "/api/user/register";
    private static final String SUCCESS_CODE = "200";

    /** 一次登录/注册的结果 */
    public static final class Result {

        private final boolean success;
        private final String message;
        private final String username;
        private final List<String> cookies;

        Result(boolean success, String message, String username, List<String> cookies) {
            this.success = success;
            this.message = message;
            this.username = username;
            this.cookies = cookies;
        }

        public boolean isSuccess() {
            return success;
        }

        public String message() {
            return message;
        }

        public String username() {
            return username;
        }

        public List<String> cookies() {
            return cookies;
        }
    }

    public Result login(String username, String password, boolean remember)
            throws EventApiException {
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("username", username);
        form.put("password", password);
        if (remember) {
            form.put("remember", "1");
        }
        return submit(LOGIN_PATH, form);
    }

    public Result register(String username, String password, String confirmPassword,
                           String email, String timeZone) throws EventApiException {
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("username", username);
        form.put("password", password);
        form.put("confirmPassword", confirmPassword);
        form.put("email", email == null ? "" : email);
        form.put("timeZone", timeZone == null ? "" : timeZone);
        return submit(REGISTER_PATH, form);
    }

    private Result submit(String path, Map<String, String> form) throws EventApiException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(AppConfig.BASE_URL + path).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(AppConfig.CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(AppConfig.READ_TIMEOUT_MS);
            connection.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            connection.setRequestProperty("User-Agent", AppConfig.USER_AGENT);

            OutputStream output = connection.getOutputStream();
            output.write(buildBody(form).getBytes(StandardCharsets.UTF_8));
            output.close();

            int status = connection.getResponseCode();
            String body = readAll(status < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream());
            return parse(body, readCookies(connection));
        } catch (IOException e) {
            throw new EventApiException("网络请求失败", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private Result parse(String body, List<String> cookies) {
        try {
            JSONObject root = new JSONObject(body);
            boolean success = root.optBoolean("success")
                    || SUCCESS_CODE.equals(root.optString("code"));
            String message = root.optString("message", "");
            String username = "";
            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                username = data.optString("username", "");
            }
            return new Result(success, message, username, cookies);
        } catch (JSONException e) {
            return new Result(false, "服务器返回了无法识别的内容", "", cookies);
        }
    }

    /** 只取 {@code name=value} 部分，属性（Path/HttpOnly/…）交给 WebView 自己补 */
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

    private static String buildBody(Map<String, String> form) throws IOException {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(URLEncoder.encode(entry.getKey(), "UTF-8"))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        return body.toString();
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
