package cc.thonly.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 预算分摊的费用类型：内置几项 + 用户自己加的（存本地，下次还在）。 */
public final class BudgetTypes {

    /** 选中它表示「新建一个类型」，不是真的类型 */
    public static final String NEW_TYPE = "+ 新建类型…";

    private static final String PREFS_NAME = "budget";
    private static final String KEY_CUSTOM = "custom_types";

    private static final String[] PRESETS = {"门票", "交通", "住宿", "餐饮", "周边", "其他"};

    private final SharedPreferences preferences;
    private final Set<String> custom = new HashSet<String>();

    public BudgetTypes(Context context) {
        this.preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> stored = preferences.getStringSet(KEY_CUSTOM, null);
        if (stored != null) {
            custom.addAll(stored);
        }
    }

    /** 内置在前、自定义在后 */
    public List<String> all() {
        List<String> types = new ArrayList<String>();
        for (String preset : PRESETS) {
            types.add(preset);
        }
        List<String> extra = new ArrayList<String>(custom);
        java.util.Collections.sort(extra);
        types.addAll(extra);
        return types;
    }

    /** 下拉里的完整选项：所有类型 + 新建入口 */
    public List<String> options() {
        List<String> options = all();
        options.add(NEW_TYPE);
        return options;
    }

    /** @return true 表示新增成功；重名或空名返回 false */
    public boolean addCustom(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || all().contains(trimmed)) {
            return false;
        }
        custom.add(trimmed);
        preferences.edit().putStringSet(KEY_CUSTOM, new HashSet<String>(custom)).apply();
        return true;
    }
}
