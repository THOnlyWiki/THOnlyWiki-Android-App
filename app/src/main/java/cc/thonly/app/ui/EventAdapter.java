package cc.thonly.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import cc.thonly.app.R;
import cc.thonly.app.data.Event;

/** 活动列表适配器，速查表和日历页共用。 */
public final class EventAdapter extends BaseAdapter {

    private final Context context;
    private final EventInteraction interaction;
    private final List<Event> items = new ArrayList<Event>();
    /** 编辑模式下整行都是「修改」入口，右侧的「＋日历」先收起来免得误触 */
    private boolean editMode;

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

        ViewHolder(View root) {
            statusBar = root.findViewById(R.id.bar);
            nameView = root.findViewById(R.id.name);
            metaView = root.findViewById(R.id.meta);
            actionView = root.findViewById(R.id.action);
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
