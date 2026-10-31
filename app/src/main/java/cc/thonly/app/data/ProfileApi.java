package cc.thonly.app.data;

import android.content.Context;

import org.json.JSONObject;

import java.io.IOException;

import cc.thonly.app.util.HttpClient;

/** 当前用户资料（目前只有头像）。 */
public final class ProfileApi {

    private ProfileApi() {
    }

    /**
     * 当前用户头像地址（站内相对路径）。
     *
     * <p>没设过头像时服务端返回 {@code /img/avatar-default.svg}，
     * 这里原样带回去，由 {@code AvatarLoader} 判断成"没有头像"画首字。
     */
    public static String myAvatar(Context context) throws IOException {
        UserSession session = new UserSession(context);
        if (!session.isLoggedIn()) {
            return "";
        }
        String body = HttpClient.getText(ServerConfig.baseUrl() + "/api/user/avatar",
                session.cookies());
        try {
            JSONObject root = new JSONObject(body);
            JSONObject data = root.optJSONObject("data");
            return data == null ? "" : data.optString("avatar", "");
        } catch (Exception e) {
            return "";
        }
    }
}
