package cc.thonly.app.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cc.thonly.app.util.FormPost;
import cc.thonly.app.util.HttpClient;

/**
 * 组队接口。服务端插件 thonly-team 提供，前缀 {@code /api/thonly-team}。
 *
 * <p>返回体统一是 {@code {code, message, data}}，code == 200 为成功。
 * 联系方式由服务端控制：没加入时 data 里根本没有 contact 字段。
 */
public final class TeamApi {

    private static final String PREFIX = "/api/thonly-team";

    private TeamApi() {
    }

    public static final class Result {

        public final boolean success;
        public final String message;
        public final JSONObject data;

        Result(boolean success, String message, JSONObject data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }
    }

    /** 供界面层丢到后台线程执行的调用 */
    public interface Call {
        Result execute() throws IOException;
    }

    public static Result list(Context context, int year, int eventId) throws IOException {
        String url = ServerConfig.baseUrl() + PREFIX + "/list?year=" + year + "&eventId=" + eventId;
        return parse(HttpClient.getText(url, cookie(context)));
    }

    public static Result detail(Context context, long id) throws IOException {
        String url = ServerConfig.baseUrl() + PREFIX + "/detail?id=" + id;
        return parse(HttpClient.getText(url, cookie(context)));
    }

    public static List<Team> teams(Result result) {
        List<Team> teams = new ArrayList<Team>();
        if (result == null || result.data == null) {
            return teams;
        }
        JSONArray array = result.data.optJSONArray("teams");
        if (array == null) {
            return teams;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) {
                teams.add(Team.fromJson(item));
            }
        }
        return teams;
    }

    public static Result create(Context context, int year, int eventId, String eventName,
                                String type, String title, int capacity, String joinMode,
                                String contact, String note) throws IOException {
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("eventYear", String.valueOf(year));
        form.put("eventId", String.valueOf(eventId));
        form.put("eventName", eventName == null ? "" : eventName);
        form.put("type", type);
        form.put("title", title);
        form.put("capacity", String.valueOf(capacity));
        form.put("joinMode", joinMode);
        form.put("contact", contact);
        form.put("note", note == null ? "" : note);
        return post(context, "/create", form);
    }

    public static Result join(Context context, long id) throws IOException {
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("id", String.valueOf(id));
        return post(context, "/join", form);
    }

    public static Result leave(Context context, long id) throws IOException {
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("id", String.valueOf(id));
        return post(context, "/leave", form);
    }

    public static Result close(Context context, long id) throws IOException {
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("id", String.valueOf(id));
        return post(context, "/close", form);
    }

    public static Result report(Context context, long id, String reason) throws IOException {
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("id", String.valueOf(id));
        form.put("reason", reason);
        return post(context, "/report", form);
    }

    private static Result post(Context context, String path, Map<String, String> form)
            throws IOException {
        FormPost.Response response = FormPost.post(ServerConfig.baseUrl() + PREFIX + path,
                form, cookie(context));
        return parse(response.body());
    }

    private static String cookie(Context context) {
        UserSession session = new UserSession(context);
        return session.isLoggedIn() ? session.cookies() : "";
    }

    private static Result parse(String body) {
        try {
            JSONObject root = new JSONObject(body);
            int code = root.optInt("code", 0);
            return new Result(code == 200, root.optString("message", ""),
                    root.optJSONObject("data"));
        } catch (Exception e) {
            return new Result(false, "服务器返回了无法识别的内容", null);
        }
    }
}
