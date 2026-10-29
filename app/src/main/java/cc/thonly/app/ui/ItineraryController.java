package cc.thonly.app.ui;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cc.thonly.app.R;
import cc.thonly.app.data.FavoriteEvent;
import cc.thonly.app.data.Favorites;

/**
 * 我的行程：顶部是最近一场的大倒计时，下面是关注的全部活动。
 *
 * <p>数据来自关注快照，不联网、也不受当前选中年份影响。
 * 同一天有两场以上会标出来——学生一般只能去一场，这点很实际。
 */
public final class ItineraryController {

    private final Activity activity;
    private final TextView daysView;
    private final TextView nameView;
    private final TextView metaView;
    private final TextView emptyView;
    private final ListView listView;
    private final ItineraryAdapter adapter;

    private final List<FavoriteEvent> items = new ArrayList<FavoriteEvent>();
    private final Map<String, Integer> sameDayCount = new HashMap<String, Integer>();

    public ItineraryController(Activity activity, View root) {
        this.activity = activity;
        this.daysView = root.findViewById(R.id.itineraryDays);
        this.nameView = root.findViewById(R.id.itineraryName);
        this.metaView = root.findViewById(R.id.itineraryMeta);
        this.emptyView = root.findViewById(R.id.itineraryEmpty);
        this.listView = root.findViewById(R.id.itineraryList);

        this.adapter = new ItineraryAdapter();
        listView.setAdapter(adapter);

        reload();
    }

    /** 每次进入页面都重读，关注可能在别的页面改过 */
    public void reload() {
        Favorites favorites = new Favorites(activity);
        items.clear();
        items.addAll(favorites.list());

        sameDayCount.clear();
        for (FavoriteEvent event : items) {
            String key = event.rawDate();
            if (!key.isEmpty()) {
                Integer count = sameDayCount.get(key);
                sameDayCount.put(key, count == null ? 1 : count + 1);
            }
        }

        adapter.notifyDataSetChanged();
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        renderHeader();
    }

    private void renderHeader() {
        FavoriteEvent next = null;
        for (FavoriteEvent event : items) {
            if (event.isUpcoming() && !event.isCancelled()) {
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
                next.dateText(), next.region()));
        metaView.setVisibility(View.VISIBLE);
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

            ((TextView) view.findViewById(R.id.itemDate)).setText(event.dateText());

            TextView nameView = view.findViewById(R.id.itemName);
            nameView.setText(event.name());
            nameView.setTextColor(activity.getColor(event.isCancelled()
                    ? R.color.text_tertiary : R.color.text_primary));

            // 地区 + 同日冲突提示
            StringBuilder meta = new StringBuilder(event.region());
            Integer sameDay = sameDayCount.get(event.rawDate());
            if (sameDay != null && sameDay > 1) {
                if (meta.length() > 0) {
                    meta.append(" · ");
                }
                meta.append(activity.getString(R.string.itinerary_same_day, sameDay));
            }
            TextView metaView = view.findViewById(R.id.itemMeta);
            if (meta.length() == 0) {
                metaView.setVisibility(View.GONE);
            } else {
                metaView.setText(meta);
                metaView.setVisibility(View.VISIBLE);
            }

            TextView remainView = view.findViewById(R.id.itemRemain);
            remainView.setText(event.remainingText());
            remainView.setTextColor(activity.getColor(
                    event.isUpcoming() && !event.isCancelled()
                            ? R.color.accent : R.color.text_tertiary));
            return view;
        }
    }
}
