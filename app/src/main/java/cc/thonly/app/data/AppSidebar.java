package cc.thonly.app.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 服务端下发的抽屉导航配置，对应站点页面 {@code WikiKernel:Sidebar-App}。
 *
 * <p>解决的问题：速查表、日历这类内容站点和 App 都有，入口该走哪边由服务端决定。
 * 每条目用 {@code target} 指定去向：
 * <ul>
 *   <li>{@code app} —— 打开 App 原生页面，{@code page} 指定页面 id</li>
 *   <li>{@code web} —— 在 App 内的 WebView 打开站内页面</li>
 *   <li>{@code external} —— 交给系统浏览器</li>
 * </ul>
 *
 * <p>解析全程不抛异常：任何一处不合法就丢掉那一项，整体不合法就返回空配置，
 * 让 App 退回内置导航。配置写错最多少几个入口，不能让人打不开 App。
 */
public final class AppSidebar {

    /** 客户端认识的结构版本，服务端给更高的版本时整体忽略 */
    private static final int SUPPORTED_SCHEMA = 1;

    public enum Target {
        APP,
        WEB,
        EXTERNAL
    }

    public static final class Item {

        private final String label;
        private final Target target;
        private final String page;
        private final String url;

        Item(String label, Target target, String page, String url) {
            this.label = label;
            this.target = target;
            this.page = page;
            this.url = url;
        }

        public String label() {
            return label;
        }

        public Target target() {
            return target;
        }

        /** 仅 {@link Target#APP} 时有值，如 {@code list}、{@code calendar} */
        public String page() {
            return page;
        }

        /** {@code web}/{@code external} 必填；{@code app} 时作为老版本的兜底地址 */
        public String url() {
            return url;
        }
    }

    public static final class Section {

        private final String title;
        private final List<Item> items;

        Section(String title, List<Item> items) {
            this.title = title;
            this.items = items;
        }

        public String title() {
            return title;
        }

        public List<Item> items() {
            return items;
        }
    }

    private final boolean replaceBuiltIn;
    private final List<Section> sections;

    private AppSidebar(boolean replaceBuiltIn, List<Section> sections) {
        this.replaceBuiltIn = replaceBuiltIn;
        this.sections = sections;
    }

    public static AppSidebar empty() {
        return new AppSidebar(false, Collections.<Section>emptyList());
    }

    /** mode=replace 时用云端条目整体替换内置导航（「设置」永远保留） */
    public boolean isReplaceBuiltIn() {
        return replaceBuiltIn;
    }

    public List<Section> sections() {
        return sections;
    }

    public boolean isEmpty() {
        return sections.isEmpty();
    }

    public static AppSidebar parse(String json, AppVersion currentVersion) {
        if (json == null || json.trim().isEmpty()) {
            return empty();
        }
        try {
            JSONObject root = new JSONObject(json.trim());
            int schema = root.optInt("schema", SUPPORTED_SCHEMA);
            if (schema > SUPPORTED_SCHEMA) {
                return empty();
            }
            // 服务端声明的最低 App 版本，低于它整份配置都不同步，继续用内置导航
            if (!isVersionEnough(root.optString("min_app_version", ""), currentVersion)) {
                return empty();
            }

            boolean replace = "replace".equalsIgnoreCase(root.optString("mode", "extend"));
            List<Section> sections = new ArrayList<Section>();

            JSONArray sectionArray = root.optJSONArray("sections");
            if (sectionArray != null) {
                for (int i = 0; i < sectionArray.length(); i++) {
                    JSONObject raw = sectionArray.optJSONObject(i);
                    if (raw == null) {
                        continue;
                    }
                    Section section = parseSection(raw);
                    if (section != null) {
                        sections.add(section);
                    }
                }
            }
            return new AppSidebar(replace, Collections.unmodifiableList(sections));
        } catch (Exception e) {
            // JSON 语法错误、类型不对等等，一律当作没有配置
            return empty();
        }
    }

    private static Section parseSection(JSONObject raw) {
        JSONArray itemArray = raw.optJSONArray("items");
        if (itemArray == null) {
            return null;
        }
        List<Item> items = new ArrayList<Item>();
        for (int i = 0; i < itemArray.length(); i++) {
            JSONObject itemJson = itemArray.optJSONObject(i);
            if (itemJson == null) {
                continue;
            }
            Item item = parseItem(itemJson);
            if (item != null) {
                items.add(item);
            }
        }
        if (items.isEmpty()) {
            return null;
        }
        String title = raw.optString("title", "").trim();
        return new Section(title, Collections.unmodifiableList(items));
    }

    private static Item parseItem(JSONObject json) {
        String label = json.optString("label", "").trim();
        if (label.isEmpty()) {
            return null;
        }

        Target target = parseTarget(json.optString("target", "web"));
        String page = json.optString("page", "").trim();
        String url = json.optString("url", "").trim();

        // app 目标可以没有 url（页面 id 够用），其余目标必须有地址
        if (target == Target.APP) {
            if (page.isEmpty() && url.isEmpty()) {
                return null;
            }
        } else if (url.isEmpty()) {
            return null;
        }

        return new Item(label, target, page, url);
    }

    private static Target parseTarget(String value) {
        if ("app".equalsIgnoreCase(value)) {
            return Target.APP;
        }
        if ("external".equalsIgnoreCase(value)) {
            return Target.EXTERNAL;
        }
        return Target.WEB;
    }

    /**
     * 版本门槛检查。{@code min_app_version} 缺失或写坏时按「不限制」处理，
     * 因为写坏的是服务端配置，没必要让所有客户端都失去云端导航。
     */
    private static boolean isVersionEnough(String minVersion, AppVersion currentVersion) {
        if (minVersion == null || minVersion.trim().isEmpty() || currentVersion == null) {
            return true;
        }
        AppVersion required = AppVersion.parse(minVersion);
        if (required == null) {
            return true;
        }
        return currentVersion.compareTo(required) >= 0;
    }
}
