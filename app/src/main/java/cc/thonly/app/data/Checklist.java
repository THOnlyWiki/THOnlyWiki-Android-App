package cc.thonly.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * 一份勾选清单的本地状态。
 *
 * <p>用条目文字当 key（改文案会让该项的勾选状态丢失，可接受）。
 * 清单本身是写死的常量，勾选状态存 SharedPreferences，关掉 App 再开还在。
 */
public final class Checklist {

    /** 行前装备 */
    public static final String[] PACKING = {
            "现金整钞（百元）",
            "零钱 / 硬币",
            "身份证 / 学生证",
            "门票或入场凭证",
            "手机 + 充电线",
            "移动电源",
            "大容量拉杆箱 / 痛包",
            "B5 / A4 硬胶套（防折角）",
            "便携小板凳",
            "水 / 保温杯",
            "湿纸巾 + 干纸巾",
            "喉糖",
            "雨具 / 遮阳伞",
            "常用药（晕车药、创可贴）",
            "收纳袋（装战利品）"
    };

    /** Cosplay 专属 */
    public static final String[] COSPLAY = {
            "假发 + 发网",
            "一字夹 / 发卡",
            "安全别针",
            "双面胶 / 防走光贴",
            "卸妆水 + 化妆棉",
            "补妆包（粉饼、口红）",
            "鞋套 / 备用袜",
            "道具快修包（胶水、扎带、小螺丝刀）",
            "打底衣物 / 备用丝袜",
            "小镜子",
            "吸油纸",
            "创可贴（磨脚用）",
            "装换下衣服的袋子"
    };

    private static final String PREFS_NAME = "checklist";
    private static final String KEY_SUFFIX_CUSTOM = "_custom";

    private final SharedPreferences preferences;
    private final String key;

    public Checklist(Context context, String key) {
        this.preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.key = key;
    }

    /** 用户自己加的条目，按名称排序 */
    public java.util.List<String> customItems() {
        Set<String> stored = preferences.getStringSet(key + KEY_SUFFIX_CUSTOM, null);
        java.util.List<String> items = new java.util.ArrayList<String>();
        if (stored != null) {
            items.addAll(stored);
            java.util.Collections.sort(items);
        }
        return items;
    }

    public void addCustom(String item) {
        Set<String> stored = preferences.getStringSet(key + KEY_SUFFIX_CUSTOM, null);
        Set<String> next = stored == null ? new HashSet<String>() : new HashSet<String>(stored);
        next.add(item);
        preferences.edit().putStringSet(key + KEY_SUFFIX_CUSTOM, next).apply();
    }

    /** 删掉自定义条目，顺带清掉它的勾选状态 */
    public void removeCustom(String item) {
        Set<String> stored = preferences.getStringSet(key + KEY_SUFFIX_CUSTOM, null);
        if (stored == null) {
            return;
        }
        Set<String> next = new HashSet<String>(stored);
        next.remove(item);
        preferences.edit().putStringSet(key + KEY_SUFFIX_CUSTOM, next).apply();
        set(item, false);
    }

    public boolean isChecked(String item) {
        Set<String> stored = preferences.getStringSet(key, null);
        return stored != null && stored.contains(item);
    }

    public void set(String item, boolean checked) {
        Set<String> stored = preferences.getStringSet(key, null);
        Set<String> next = stored == null ? new HashSet<String>() : new HashSet<String>(stored);
        if (checked) {
            next.add(item);
        } else {
            next.remove(item);
        }
        preferences.edit().putStringSet(key, next).apply();
    }

    public void clear() {
        preferences.edit().remove(key).apply();
    }
}
