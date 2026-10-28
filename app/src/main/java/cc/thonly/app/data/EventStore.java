package cc.thonly.app.data;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
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

    private final EventRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<Listener>();

    private EventData current = EventData.empty();
    private boolean loading;
    private int loadingYear;
    private int lastRequestedYear;

    public EventStore(EventRepository repository) {
        this.repository = repository;
    }

    public EventData current() {
        return current;
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
        if (loading && loadingYear == year && !forceRefresh) {
            return;
        }
        loading = true;
        loadingYear = year;
        lastRequestedYear = year;
        notifyLoadingStarted(year);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final EventData data = repository.load(year, forceRefresh);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            loading = false;
                            current = data;
                            for (Listener listener : snapshot()) {
                                listener.onDataLoaded(data);
                            }
                        }
                    });
                } catch (final EventApiException e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
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

    /** 重新拉取最近一次请求的年份，用于「刷新」按钮 */
    public void reload() {
        if (lastRequestedYear > 0) {
            load(lastRequestedYear, true);
        }
    }

    public void shutdown() {
        listeners.clear();
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
