package cc.thonly.app.util;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.webkit.WebSettings;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 设备与运行环境信息，设置页展示用，方便用户反馈问题时报环境。 */
public final class DeviceInfo {

    private static final Pattern CHROME_VERSION = Pattern.compile("Chrome/([\\d.]+)");

    private DeviceInfo() {
    }

    /**
     * 机型展示：优先「用户可见名称 (厂商+型号)」，例如 {@code 小米17 (Xiaomi 25113PN0EC)}。
     *
     * <p>Android 没有公开 API 拿营销名，{@link Build#MODEL} 只是型号代码。
     * {@code Settings.Global.DEVICE_NAME} 在多数国产 ROM 上是营销名（也是蓝牙名称），
     * 拿不到就只显示型号代码。
     */
    public static String model(Context context) {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        String code = modelCode(manufacturer, model);

        String friendly = deviceName(context);
        if (friendly.isEmpty()
                || friendly.equalsIgnoreCase(code)
                || friendly.equalsIgnoreCase(model)) {
            return code;
        }
        // 营销名里已经带厂商（Xiaomi 17）时，括号里只留型号代码，避免「Xiaomi 17 (Xiaomi 25113PN0EC)」
        boolean brandInFriendly = !manufacturer.isEmpty()
                && friendly.toLowerCase(Locale.ROOT).contains(manufacturer.toLowerCase(Locale.ROOT));
        return friendly + " (" + (brandInFriendly ? model : code) + ")";
    }

    /** 形如 {@code Xiaomi 25113PN0EC} */
    private static String modelCode(String manufacturer, String model) {
        if (manufacturer.isEmpty()) {
            return model;
        }
        // 有些机型 MODEL 已经带了厂商名，避免重复
        if (model.toLowerCase(Locale.ROOT).startsWith(manufacturer.toLowerCase(Locale.ROOT))) {
            return model;
        }
        return manufacturer + " " + model;
    }

    private static String deviceName(Context context) {
        try {
            String name = Settings.Global.getString(
                    context.getContentResolver(), Settings.Global.DEVICE_NAME);
            return name == null ? "" : name.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    /** 系统 WebView 的内核版本，取 UA 里的 Chrome 版本号；取不到返回「未知」 */
    public static String webViewVersion(Context context) {
        try {
            String userAgent = WebSettings.getDefaultUserAgent(context);
            Matcher matcher = CHROME_VERSION.matcher(userAgent);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception ignored) {
            // 落到下面的兜底
        }
        return "未知";
    }
}
