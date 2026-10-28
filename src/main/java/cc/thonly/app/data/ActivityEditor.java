package cc.thonly.app.data;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import cc.thonly.app.AppConfig;
import cc.thonly.app.util.FormPost;

/**
 * 活动增删改，对应站点表格的写接口。
 *
 * <p>契约与网页端 {@code activity.js} 完全一致：
 * <pre>
 * POST /api/thonly-activity/save    year, id(空=新增), category, status, name, region, date, qq, comment
 * POST /api/thonly-activity/delete  year, id, comment
 * </pre>
 * 成功返回 {@code {"saved":true}} / {@code {"deleted":true}}，
 * 失败返回 {@code {"message":"..."}}。登录态靠 Cookie，服务端另外校验 edit 权限。
 */
public final class ActivityEditor {

    /** 一次写操作的结果 */
    public static final class Result {

        private final boolean success;
        private final String message;

        Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String message() {
            return message;
        }
    }

    private final UserSession session;

    public ActivityEditor(UserSession session) {
        this.session = session;
    }

    /**
     * @param id 为空表示新增，非空表示改这一条
     */
    public Result save(int year, String id, EventCategory category, String status, String name,
                       String region, String date, String qq) throws EventApiException {
        boolean creating = id == null || id.isEmpty();

        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("year", String.valueOf(year));
        form.put("id", creating ? "" : id);
        form.put("category", category.key());
        form.put("status", status);
        form.put("name", name);
        form.put("region", region);
        form.put("date", date);
        form.put("qq", qq);
        form.put("comment", creating ? "App 新增" : "App 编辑");
        return submit("/save", form, "saved");
    }

    public Result delete(int year, String id) throws EventApiException {
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("year", String.valueOf(year));
        form.put("id", id);
        form.put("comment", "App 删除");
        return submit("/delete", form, "deleted");
    }

    private Result submit(String path, Map<String, String> form, String successKey)
            throws EventApiException {
        try {
            FormPost.Response response = FormPost.post(
                    AppConfig.ACTIVITY_API + path, form, session.cookies());
            JSONObject root = new JSONObject(response.body());
            boolean success = root.optBoolean(successKey, false);
            String message = root.optString("message", "");
            if (!success && message.isEmpty()) {
                message = "操作失败，请稍后再试";
            }
            return new Result(success, message);
        } catch (IOException e) {
            throw new EventApiException("网络请求失败", e);
        } catch (JSONException e) {
            return new Result(false, "服务器返回了无法识别的内容");
        }
    }
}
