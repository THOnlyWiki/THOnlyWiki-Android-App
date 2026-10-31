package cc.thonly.app.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cc.thonly.app.util.FormPost;
import cc.thonly.app.util.HttpClient;

/**
 * 消息中心：通知（Echo 插件）+ 私信（SocialProfile 插件）。
 *
 * <p>两个插件各自提供接口，这里统一成 {@link Result}：
 * <ul>
 *     <li>Echo：{@code GET /api/echo/list}、{@code POST /api/echo/mark-read}</li>
 *     <li>私信：{@code GET /api/messages/list}、{@code GET /api/messages/thread}、
 *         {@code POST /api/messages/send}</li>
 * </ul>
 */
public final class MessagesApi {

    private static final String MSG = "/api/messages";
    private static final String ECHO = "/api/echo";

    private MessagesApi() {
    }

    /** 供界面层丢到后台线程执行的调用 */
    public interface Call {
        Result execute() throws IOException;
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

    /** 列表里的一行：通知和会话共用 */
    public static final class Item {

        public final long id;
        public final String title;
        public final String body;
        public final String time;
        public final boolean unread;
        /** 私信会话的对方用户名；通知为 null */
        public final String peer;
        /** 头像地址（站内相对路径）；空表示用首字头像 */
        public final String avatar;

        Item(long id, String title, String body, String time, boolean unread, String peer,
             String avatar) {
            this.id = id;
            this.title = title;
            this.body = body;
            this.time = time;
            this.unread = unread;
            this.peer = peer;
            this.avatar = avatar;
        }
    }

    // ------------------------------------------------------------ 通知

    public static Result notifications(Context context) throws IOException {
        return parse(HttpClient.getText(
                ServerConfig.baseUrl() + ECHO + "/list?limit=30", cookie(context)));
    }

    public static List<Item> notificationItems(Result result) {
        List<Item> items = new ArrayList<Item>();
        JSONArray array = arrayOf(result, "items");
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json == null) {
                continue;
            }
            String actor = json.optString("actorName", "");
            String label = json.optString("typeLabel", "");
            String title = actor.isEmpty() ? label : actor + " · " + label;
            items.add(new Item(json.optLong("id"), title, json.optString("comment", ""),
                    json.optString("timeText", ""), !json.optBoolean("read", false), null,
                    json.optString("actorAvatar", "")));
        }
        return items;
    }

    public static Result markNotificationRead(Context context, long id) throws IOException {
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("id", String.valueOf(id));
        return post(context, ECHO + "/mark-read", form);
    }

    // ------------------------------------------------------------ 私信

    public static Result conversations(Context context) throws IOException {
        return parse(HttpClient.getText(
                ServerConfig.baseUrl() + MSG + "/list?limit=50", cookie(context)));
    }

    /** 收件箱按对方分组，每组取最新一条 */
    public static List<Item> conversationItems(Result result) {
        List<Item> items = new ArrayList<Item>();
        JSONArray array = arrayOf(result, "messages");
        Map<String, Item> byPeer = new LinkedHashMap<String, Item>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json == null) {
                continue;
            }
            String peer = json.optBoolean("incoming", false)
                    ? json.optString("from", "") : json.optString("to", "");
            if (peer.isEmpty() || byPeer.containsKey(peer)) {
                continue;
            }
            byPeer.put(peer, new Item(json.optLong("id"), peer,
                    json.optString("content", ""), shortTime(json.optString("createAt", "")),
                    !json.optBoolean("read", true) && json.optBoolean("incoming", false), peer,
                    json.optString("avatar", "")));
        }
        items.addAll(byPeer.values());
        return items;
    }

    public static Result thread(Context context, String with) throws IOException {
        return parse(HttpClient.getText(ServerConfig.baseUrl() + MSG + "/thread?with="
                + URLEncoder.encode(with, "UTF-8"), cookie(context)));
    }

    public static Result send(Context context, String to, String content) throws IOException {
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("to", to);
        form.put("content", content);
        return post(context, MSG + "/send", form);
    }

    /** 把对话渲染成纯文本（App 端不跑 wikitext） */
    public static String transcript(Result result, String me) {
        StringBuilder text = new StringBuilder();
        JSONArray array = arrayOf(result, "messages");
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json == null) {
                continue;
            }
            boolean incoming = json.optBoolean("incoming", false);
            String who = incoming ? json.optString("from", "") : me;
            text.append(who).append("  ").append(shortTime(json.optString("createAt", "")))
                    .append('\n').append(json.optString("content", "")).append("\n\n");
        }
        return text.toString().trim();
    }

    // ------------------------------------------------------------ 工具

    private static JSONArray arrayOf(Result result, String key) {
        if (result == null || result.data == null) {
            return new JSONArray();
        }
        JSONArray array = result.data.optJSONArray(key);
        return array == null ? new JSONArray() : array;
    }

    private static Result post(Context context, String path, Map<String, String> form)
            throws IOException {
        FormPost.Response response = FormPost.post(ServerConfig.baseUrl() + path, form,
                cookie(context));
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

    /** 2026-10-30T21:33:59 → 10-30 21:33 */
    private static String shortTime(String raw) {
        if (raw == null || raw.length() < 16) {
            return raw == null ? "" : raw;
        }
        String date = raw.substring(5, 10);
        String time = raw.substring(11, 16);
        return date + " " + time;
    }
}
