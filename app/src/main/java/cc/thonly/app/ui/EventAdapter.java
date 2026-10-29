package cc.thonly.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import cc.thonly.app.R;
import cc.thonly.app.data.Event;
import cc.thonly.app.data.Favorites;

/** 活动列表适配器，速查表和日历页共用。 */
public final class EventAdapter extends BaseAdapter {

    /** 关注状态变了，列表需要重新统计/过滤 */
    public interface OnFavoriteListener {
        void onFavoriteChanged();
    }

    private final Context context;
    private final EventInteraction interaction;
    private final List<Event> items = new ArrayList<Event>();
    /** 编辑模式下整行都是「修改」入口，右侧的「＋日历」先收起来免得误触 */
    private boolean editMode;

    private Favorites favorites;
    private int year;
    private boolean favoriteEnabled;
    private OnFavoriteListener favoriteListener;

    public EventAdapter(Context context, EventInteraction interaction) {
        this.context = context;
        this.interaction = interaction;
    }

    public void setEditMode(boolean editMode) {
        if (this.editMode == editMode) {
            return;
        }
        this.editMode = editMode;
        notifyDataSetChanged();
    }

    /** 只有速查表页显示星标，日历页不显示 */
    public void setFavoriteEnabled(boolean enabled) {
        if (this.favoriteEnabled == enabled) {
            return;
        }
        this.favoriteEnabled = enabled;
        notifyDataSetChanged();
    }

    public void setFavoriteContext(Favorites favorites, int year, OnFavoriteListener listener) {
        this.favorites = favorites;
        this.year = year;
        this.favoriteListener = listener;
    }

    public void submit(List<Event> events) {
        items.clear();
        if (events != null) {
            items.addAll(events);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Event getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_event, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final Event event = items.get(position);
        holder.bind(event, interaction);
        return convertView;
    }

    /** 视图缓存，顺带把绑定逻辑收在一起 */
    private final class ViewHolder {

        private final View statusBar;
        private final TextView nameView;
        private final TextView metaView;
        private final TextView actionView;
        private final TextView starView;

        ViewHolder(View root) {
            statusBar = root.findViewById(R.id.bar);
            nameView = root.findViewById(R.id.name);
            metaView = root.findViewById(R.id.meta);
            actionView = root.findViewById(R.id.action);
            starView = root.findViewById(R.id.star);
        }

        void bind(final Event event, final EventInteraction interaction) {
            nameView.setText(event.name());
            nameView.getPaint().setStrikeThruText(event.status().isCancelled());
            nameView.setTextColor(context.getColor(event.status().isCancelled()
                    ? R.color.text_tertiary
                    : R.color.text_primary));
            metaView.setText(buildMeta(event));
            statusBar.setBackgroundColor(context.getColor(event.status().colorRes()));

            actionView.setVisibility(!editMode && event.hasIcsFile() ? View.VISIBLE : View.GONE);
            actionView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    interaction.onAddToCalendar(event);
                }
            });

            bindStar(event);
        }

        private void bindStar(final Event event) {
            if (!favoriteEnabled || favorites == null) {
                starView.setVisibility(View.GONE);
                return;
            }
            starView.setVisibility(View.VISIBLE);
            starView.setTextColor(context.getColor(
                    favorites.isFavorite(year, event.id()) ? R.color.accent : R.color.text_tertiary));
            starView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean nowFavorite = favorites.toggle(year, event);
                    Toast.makeText(context,
                            nowFavorite ? R.string.favorite_added : R.string.favorite_removed,
                            Toast.LENGTH_SHORT).show();
                    notifyDataSetChanged();
                    if (favoriteListener != null) {
                        favoriteListener.onFavoriteChanged();
                    }
                }
            });
        }

        private String buildMeta(Event event) {
            String date = event.date().isResolved()
                    ? context.getString(R.string.date_month_day,
                    event.date().month(), event.date().day())
                    : fallbackDate(event);

            String region = event.region().isEmpty()
                    ? context.getString(R.string.unknown_region)
                    : event.region();

            return context.getString(R.string.meta_line,
                    date, region, context.getString(event.category().labelRes()));
        }

        private String fallbackDate(Event event) {
            return event.date().raw().isEmpty()
                    ? context.getString(R.string.unknown_date)
                    : event.date().raw();
        }
    }
}
