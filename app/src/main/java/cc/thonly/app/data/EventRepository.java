package cc.thonly.app.data;

/** 活动数据来源。抽成接口是为了以后换成别的实现（比如本地样例数据）时不用改 UI。 */
public interface EventRepository {

    /**
     * @param year         年份
     * @param forceRefresh true 时忽略缓存，强制走网络
     */
    EventData load(int year, boolean forceRefresh) throws EventApiException;
}
