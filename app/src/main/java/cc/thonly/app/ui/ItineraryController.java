package cc.thonly.app.ui;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cc.thonly.app.R;
import cc.thonly.app.data.EventDate;
import cc.thonly.app.data.EventStatus;
import cc.thonly.app.data.FavoriteEvent;
import cc.thonly.app.data.Favorites;

/**
 * 我的行程：顶部是最近一场的大倒计时，下面是关注的全部活动。
 *
 * <p>先显示本机关注快照，前台同步完成后由入口更新，独立于当前选中的速查表年份。
 * 同一天有两场以上会标出来——学生一般只能去一场，这点很实际。
 */
public final class ItineraryController {

    private final Activity activity;
    private final Favorites favorites;
    private final TextView daysView;
    private final TextView nameView;
    private final TextView metaView;
    private final TextView emptyView;
    private final TextView syncView;
    private final ListView listView;
    private final ItineraryAdapter adapter;

    private final List<FavoriteEvent> items = new ArrayList<FavoriteEvent>();
    private final Map<String, Integer> sameDayCount = new HashMap<String, Integer>();
    private boolean syncing;
    private boolean syncFailed;

    public ItineraryController(Activity activity, View root, Favorites favorites, Runnable openList) {
        this.activity = activity;
        this.favorites = favorites;
        this.daysView = root.findViewById(R.id.itineraryDays);
        this.nameView = root.findViewById(R.id.itineraryName);
        this.metaView = root.findViewById(R.id.itineraryMeta);
        this.emptyView = root.findViewById(R.id.itineraryEmpty);
        this.syncView = root.findViewById(R.id.itinerarySync);
        this.listView = root.findViewById(R.id.itineraryList);
        root.findViewById(R.id.itineraryOpenList).setOnClickListener(view -> openList.run());

        this.adapter = new ItineraryAdapter();
        listView.setAdapter(adapter);

        reload();
    }

    /** 从共享的关注快照重绘，切换页面与离线时都可立即调用。 */
    public void reload() {
        items.clear();
        items.addAll(favorites.list());

        sameDayCount.clear();
        for (FavoriteEvent event : items) {
            String key = event.rawDate();
            if (event.canRemind()) {
                Integer count = sameDayCount.get(key);
                sameDayCount.put(key, count == null ? 1 : count + 1);
            }
        }

        adapter.notifyDataSetChanged();
        emptyView.setText(favorites.count() > 0
                ? R.string.itinerary_missing_snapshot : R.string.itinerary_empty);
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        renderHeader();
        renderSyncState();
    }

    public void showSyncState(boolean syncing, boolean failed) {
        this.syncing = syncing;
        this.syncFailed = failed;
        renderSyncState();
    }

    private void renderSyncState() {
        long timestamp = favorites.lastFullSyncAt();
        if (favorites.count() == 0 || (!syncing && !syncFailed && timestamp == 0)) {
            syncView.setVisibility(View.GONE);
            return;
        }
        syncView.setVisibility(View.VISIBLE);
        if (syncing) {
            syncView.setText(R.string.itinerary_sync_loading);
        } else if (timestamp == 0) {
            syncView.setText(R.string.itinerary_sync_failed);
        } else {
            String time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(timestamp));
            syncView.setText(activity.getString(syncFailed
                    ? R.string.itinerary_sync_failed_at : R.string.itinerary_sync_at, time));
        }
    }

    private void renderHeader() {
        FavoriteEvent next = null;
        for (FavoriteEvent event : items) {
            if (event.isUpcoming()) {
                next = event;
                break;
            }
        }
        if (next == null) {
            daysView.setText(R.string.itinerary_no_upcoming);
            nameView.setText(R.string.itinerary_no_upcoming_hint);
            metaView.setVisibility(View.GONE);
            return;
        }
        long days = next.daysUntil();
        daysView.setText(days == 0
                ? activity.getString(R.string.itinerary_today)
                : activity.getString(R.string.itinerary_days_left, days));
        nameView.setText(next.name());
        metaView.setText(activity.getString(R.string.itinerary_meta,
                formatDate(next.rawDate(), false), next.region()));
        metaView.setVisibility(View.VISIBLE);
    }

    private String formatDate(String rawDate, boolean includeYear) {
        Calendar date = EventDate.parse(rawDate).toCalendar();
        if (date == null) {
            return activity.getString(R.string.itinerary_date_pending);
        }
        return includeYear ? DateFormat.getDateInstance(DateFormat.MEDIUM).format(date.getTime())
                : activity.getString(R.string.itinerary_month_day,
                        date.get(Calendar.MONTH) + 1, date.get(Calendar.DAY_OF_MONTH));
    }

    private String remaining(FavoriteEvent event) {
        if (event.isUnavailable()) {
            return activity.getString(R.string.itinerary_unavailable);
        }
        if (event.status().isCancelled()) {
            return activity.getString(R.string.itinerary_cancelled);
        }
        if (event.status() == EventStatus.POSTPONED) {
            return activity.getString(R.string.itinerary_postponed);
        }
        long days = event.daysUntil();
        if (days == Long.MIN_VALUE) {
            return activity.getString(R.string.itinerary_date_pending);
        }
        if (days == 0) {
            return activity.getString(R.string.itinerary_today);
        }
        return days > 0 ? activity.getString(R.string.itinerary_days_left, days)
                : activity.getString(R.string.itinerary_ended);
    }

    private final class ItineraryAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(activity)
                        .inflate(R.layout.item_itinerary, parent, false);
            }
            FavoriteEvent event = items.get(position);

            ((TextView) view.findViewById(R.id.itemDate)).setText(formatDate(event.rawDate(), false));

            TextView nameView = view.findViewById(R.id.itemName);
            nameView.setText(event.name());
            nameView.setTextColor(activity.getColor((event.status().isCancelled() || event.isUnavailable())
                    ? R.color.text_tertiary : R.color.text_primary));

            // 地区 + 同日冲突提示
            StringBuilder meta = new StringBuilder(event.region());
            Integer sameDay = sameDayCount.get(event.rawDate());
            if (event.canRemind() && sameDay != null && sameDay > 1) {
                if (meta.length() > 0) {
                    meta.append(" · ");
                }
                meta.append(activity.getString(R.string.itinerary_same_day, sameDay));
            }
            if (event.previousDate() != null) {
                if (meta.length() > 0) {
                    meta.append('\n');
                }
                meta.append(activity.getString(R.string.itinerary_date_changed,
                        formatDate(event.previousDate(), true), formatDate(event.rawDate(), true)));
            }
            TextView metaView = view.findViewById(R.id.itemMeta);
            if (meta.length() == 0) {
                metaView.setVisibility(View.GONE);
            } else {
                metaView.setText(meta);
                metaView.setVisibility(View.VISIBLE);
            }

            TextView remainView = view.findViewById(R.id.itemRemain);
            remainView.setText(remaining(event));
            remainView.setTextColor(activity.getColor(
                    event.isUpcoming()
                            ? R.color.accent : R.color.text_tertiary));
            return view;
        }
    }
}
