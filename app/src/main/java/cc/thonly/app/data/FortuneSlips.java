package cc.thonly.app.data;

import android.content.Context;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cc.thonly.app.util.AppLog;

/**
 * 签文库 + 每日抽签。
 *
 * <p>签文从 assets 里的 fortune_slips.json 读（来源：HorainingyouBot 的
 * Touhou_Fortune_Slips.json），61 张。
 *
 * <p>抽签是确定性的：种子 = 设备 ID + 当天日期，所以同一台设备同一天
 * 反复打开都是同一张，第二天自动换。设备 ID 用 {@code Settings.Secure.ANDROID_ID}，
 * 不需要任何权限。
 */
public final class FortuneSlips {

    private static final String ASSET_NAME = "fortune_slips.json";
    /** 混进哈希里，避免外部能直接推算 */
    private static final String SALT = "thonly-fortune-v1";

    private static List<FortuneSlip> cache;

    private FortuneSlips() {
    }

    /** 当天这张签；数据读不出来时返回 null */
    public static FortuneSlip today(Context context) {
        List<FortuneSlip> all = all(context);
        if (all.isEmpty()) {
            return null;
        }
        int index = (int) (seed(context) % all.size());
        return all.get(index);
    }

    public static int count(Context context) {
        return all(context).size();
    }

    private static List<FortuneSlip> all(Context context) {
        if (cache != null) {
            return cache;
        }
        List<FortuneSlip> slips = new ArrayList<FortuneSlip>();
        try {
            JSONObject root = new JSONObject(readAsset(context));
            JSONArray array = root.optJSONArray("slips");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    JSONArray contentArray = item.optJSONArray("content");
                    if (contentArray == null || contentArray.length() == 0) {
                        continue;
                    }
                    List<String> content = new ArrayList<String>();
                    for (int j = 0; j < contentArray.length(); j++) {
                        content.add(contentArray.optString(j, ""));
                    }
                    slips.add(new FortuneSlip(item.optString("id", ""), content,
                            item.optString("sign", "")));
                }
            }
        } catch (Exception e) {
            AppLog.e("fortune", "读取签文失败", e);
        }
        cache = slips;
        return cache;
    }

    /** 设备 ID + 当天日期 的 SHA-256，取前 8 字节当无符号数 */
    private static long seed(Context context) {
        String deviceId = deviceId(context);
        String date = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((deviceId + "|" + date + "|" + SALT)
                    .getBytes(StandardCharsets.UTF_8));
            long value = 0;
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | (hash[i] & 0xFFL);
            }
            return value & Long.MAX_VALUE;
        } catch (Exception e) {
            // 理论上不会发生，退化成字符串哈希
            return Math.abs((deviceId + date).hashCode());
        }
    }

    private static String deviceId(Context context) {
        try {
            String id = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ANDROID_ID);
            if (id != null && !id.isEmpty()) {
                return id;
            }
        } catch (Exception ignored) {
            // 落到下面的兜底
        }
        return android.os.Build.FINGERPRINT;
    }

    private static String readAsset(Context context) throws IOException {
        InputStream input = context.getAssets().open(ASSET_NAME);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int count;
        while ((count = input.read(chunk)) != -1) {
            buffer.write(chunk, 0, count);
        }
        input.close();
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }
}
