package cc.thonly.app.data;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 数据状态容器：负责异步加载、线程切换、通知观察者。
 *
 * <p>列表页和日历页都监听它，避免两个页面各自发一次请求。
 * 所有回调都保证在主线程触发，观察者不需要再做线程处理。
 */
public final class EventStore {

    public interface Listener {
        void onLoadingStarted(int year);

        void onDataLoaded(EventData data);

        void onLoadFailed(int year, EventApiException error);
    }

    public interface SyncListener {
        void onComplete(List<LoadResult> results, boolean allFresh);
    }

    private final EventRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<Listener>();

    private EventData current = EventData.empty();
    private boolean fromCache;
    private long updatedAtMillis;
    private boolean loading;
    private int loadingYear;
    private int lastRequestedYear;
    private int loadGeneration;
    private boolean syncingItinerary;
    private boolean closed;

    public EventStore(EventRepository repository) {
        this.repository = repository;
    }

    public EventData current() {
        return current;
    }

    /** 当前数据是否来自本地存档（说明这次没连上服务器） */
    public boolean isFromCache() {
        return fromCache;
    }

    /** 当前数据的落盘时间，0 表示未知 */
    public long lastUpdatedAt() {
        return updatedAtMillis;
    }

    public boolean isLoading() {
        return loading;
    }

    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void load(final int year, final boolean forceRefresh) {
        if (closed || (loading && loadingYear == year && !forceRefresh)) {
            return;
        }
        loading = true;
        loadingYear = year;
        lastRequestedYear = year;
        final int generation = ++loadGeneration;
        notifyLoadingStarted(year);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final LoadResult result = repository.load(year, forceRefresh);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (closed || generation != loadGeneration) {
                                return;
                            }
                            loading = false;
                            publish(result);
                        }
                    });
                } catch (final EventApiException e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (closed || generation != loadGeneration) {
                                return;
                            }
                            loading = false;
                            for (Listener listener : snapshot()) {
                                listener.onLoadFailed(year, e);
                            }
                        }
                    });
                }
            }
        });
    }

    /** 单次前台同步关注的年份，不改变速查表或者日历当前选中的年份。 */
    public boolean syncItinerary(Set<Integer> years, SyncListener listener) {
        if (closed || syncingItinerary || years.isEmpty()) {
            return false;
        }
        syncingItinerary = true;
        executor.execute(() -> {
            List<LoadResult> results = new ArrayList<LoadResult>();
            boolean allFresh = true;
            for (int year : years) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                try {
                    LoadResult result = repository.load(year, true);
                    results.add(result);
                    allFresh &= !result.fromCache();
                } catch (EventApiException error) {
                    allFresh = false;
                }
            }
            boolean complete = allFresh;
            mainHandler.post(() -> {
                if (closed) {
                    return;
                }
                syncingItinerary = false;
                listener.onComplete(results, complete);
                if (!loading) {
                    for (LoadResult result : results) {
                        if (!result.fromCache() && result.data().year() == lastRequestedYear) {
                            publish(result);
                        }
                    }
                }
            });
        });
        return true;
    }

    private void publish(LoadResult result) {
        current = result.data();
        fromCache = result.fromCache();
        updatedAtMillis = result.updatedAtMillis();
        for (Listener listener : snapshot()) {
            listener.onDataLoaded(current);
        }
    }

    /** 重新拉取最近一次请求的年份，用于「刷新」按钮 */
    public void reload() {
        if (lastRequestedYear > 0) {
            load(lastRequestedYear, true);
        }
    }

    public void shutdown() {
        closed = true;
        listeners.clear();
        mainHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }

    private List<Listener> snapshot() {
        return new ArrayList<Listener>(listeners);
    }

    private void notifyLoadingStarted(int year) {
        for (Listener listener : snapshot()) {
            listener.onLoadingStarted(year);
        }
    }
}
