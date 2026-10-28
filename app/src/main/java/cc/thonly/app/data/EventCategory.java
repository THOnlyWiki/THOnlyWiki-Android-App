package cc.thonly.app.data;

import cc.thonly.app.R;

/** 活动类型。{@link #key} 与服务端 category 字段一一对应。 */
public enum EventCategory {

    THO("tho", R.string.category_tho),
    THP("thp", R.string.category_thp),
    SCHOOL("school", R.string.category_school),
    LIVE("live", R.string.category_live),
    OTHER("other", R.string.category_other);

    private final String key;
    private final int labelRes;

    EventCategory(String key, int labelRes) {
        this.key = key;
        this.labelRes = labelRes;
    }

    public String key() {
        return key;
    }

    public int labelRes() {
        return labelRes;
    }

    /** 未知或新增的类型统一归到 {@link #OTHER}，保证老版本 App 不会崩 */
    public static EventCategory fromKey(String key) {
        for (EventCategory category : values()) {
            if (category.key.equals(key)) {
                return category;
            }
        }
        return OTHER;
    }
}
