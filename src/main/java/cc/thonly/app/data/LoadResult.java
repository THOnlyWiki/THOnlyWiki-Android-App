package cc.thonly.app.data;

/** 一次加载的结果：数据本身 + 来源信息（是否来自本地缓存、缓存时间）。 */
public final class LoadResult {

    private final EventData data;
    private final boolean fromCache;
    private final long updatedAtMillis;

    public LoadResult(EventData data, boolean fromCache, long updatedAtMillis) {
        this.data = data;
        this.fromCache = fromCache;
        this.updatedAtMillis = updatedAtMillis;
    }

    public EventData data() {
        return data;
    }

    /** true 表示这次没有走网络，直接用本地存档 */
    public boolean fromCache() {
        return fromCache;
    }

    /** 数据落盘/落网的时间戳，0 表示未知 */
    public long updatedAtMillis() {
        return updatedAtMillis;
    }
}
