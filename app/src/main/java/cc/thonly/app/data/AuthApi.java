package cc.thonly.app.data;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cc.thonly.app.AppConfig;
import cc.thonly.app.util.FormPost;

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
        try {
            FormPost.Response response = FormPost.post(AppConfig.BASE_URL + path, form, null);
            return parse(response.body(), response.cookies());
        } catch (IOException e) {
            throw new EventApiException("网络请求失败", e);
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
}
