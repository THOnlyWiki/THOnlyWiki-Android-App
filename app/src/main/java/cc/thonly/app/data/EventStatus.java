package cc.thonly.app.data;

import cc.thonly.app.R;

/** 活动状态。服务端是自由文本，这里做一次归类。 */
public enum EventStatus {

    PLAN("计划", R.color.status_plan),
    CONFIRMED("定档", R.color.status_confirmed),
    ON_SCHEDULE("如期", R.color.status_confirmed),
    POSTPONED("延期", R.color.status_postponed),
    CANCELLED("取消", R.color.status_cancelled),
    UNKNOWN("", R.color.status_unknown);

    private final String label;
    private final int colorRes;

    EventStatus(String label, int colorRes) {
        this.label = label;
        this.colorRes = colorRes;
    }

    public String label() {
        return label;
    }

    public int colorRes() {
        return colorRes;
    }

    public boolean isCancelled() {
        return this == CANCELLED;
    }

    /** 用包含匹配，服务端若写成「已取消」也能识别 */
    public static EventStatus fromLabel(String label) {
        if (label != null) {
            for (EventStatus status : values()) {
                if (status != UNKNOWN && !status.label.isEmpty() && label.contains(status.label)) {
                    return status;
                }
            }
        }
        return UNKNOWN;
    }
}
