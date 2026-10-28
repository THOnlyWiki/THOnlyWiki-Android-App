package cc.thonly.app.ui;

import android.app.Activity;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import cc.thonly.app.R;
import cc.thonly.app.data.Event;
import cc.thonly.app.data.EventApiException;
import cc.thonly.app.data.EventData;
import cc.thonly.app.data.EventStore;

/** 日历页：月历网格 + 选中日期的活动列表。 */
public final class CalendarController implements EventStore.Listener, MonthView.OnDaySelectedListener {

    private final Activity activity;
    private final EventStore store;
    private final EventAdapter adapter;
    private final TextView monthTitle;
    private final MonthView monthView;
    private final TextView dayTitle;
    private final TextView emptyLabel;

    private int year;
    private int month;
    private int day;
    /** 已经为哪一年发过请求，避免数据年份对不上时反复请求 */
    private int requestedYear;

    public CalendarController(Activity activity, View root, EventStore store,
                              EventInteraction interaction) {
        this.activity = activity;
        this.store = store;
        this.adapter = new EventAdapter(activity, interaction);
        this.monthTitle = root.findViewById(R.id.monthTitle);
        this.monthView = root.findViewById(R.id.monthView);
        this.dayTitle = root.findViewById(R.id.dayTitle);
        this.emptyLabel = root.findViewById(R.id.dayEmpty);

        ListView dayList = root.findViewById(R.id.dayList);
        dayList.setAdapter(adapter);

        Calendar now = Calendar.getInstance();
        year = now.get(Calendar.YEAR);
        month = now.get(Calendar.MONTH) + 1;
        day = now.get(Calendar.DAY_OF_MONTH);
        requestedYear = year;

        monthView.setListener(this);
        root.findViewById(R.id.prevMonth).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shiftMonth(-1);
            }
        });
        root.findViewById(R.id.nextMonth).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shiftMonth(1);
            }
        });
    }

    /** 切到日历页时刷新一次 */
    public void onShown() {
        refresh();
    }

    @Override
    public void onLoadingStarted(int year) {
        requestedYear = year;
    }

    @Override
    public void onDataLoaded(EventData data) {
        if (data.year() != year) {
            requestYear(year);
            return;
        }
        refresh();
    }

    @Override
    public void onLoadFailed(int year, EventApiException error) {
        // 列表页负责展示失败提示，这里保持上次内容即可
    }

    @Override
    public void onDaySelected(int year, int month, int day) {
        this.year = year;
        this.month = month;
        this.day = day;
        showDayEvents(eventsOn(month, day));
    }

    private void shiftMonth(int delta) {
        month += delta;
        if (month < 1) {
            month = 12;
            year -= 1;
        } else if (month > 12) {
            month = 1;
            year += 1;
        }
        day = Math.min(day, maxDayOf(year, month));
        refresh();
    }

    private void refresh() {
        EventData data = store.current();
        monthTitle.setText(activity.getString(R.string.month_title, year, month));

        if (data == null || data.year() != year) {
            requestYear(year);
            monthView.setMonth(year, month);
            monthView.setMarkedDays(null);
            monthView.setSelectedDay(day);
            showDayEvents(Collections.<Event>emptyList());
            return;
        }

        monthView.setMonth(year, month);
        monthView.setMarkedDays(data.markedDaysOf(month));
        monthView.setSelectedDay(day);
        showDayEvents(data.eventsOn(month, day));
    }

    private void showDayEvents(List<Event> events) {
        adapter.submit(events);
        dayTitle.setText(activity.getString(R.string.day_title, month, day, events.size()));
        emptyLabel.setVisibility(events.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private List<Event> eventsOn(int month, int day) {
        EventData data = store.current();
        if (data == null || data.year() != year) {
            return Collections.emptyList();
        }
        return data.eventsOn(month, day);
    }

    private void requestYear(int target) {
        if (requestedYear == target) {
            return;
        }
        requestedYear = target;
        store.load(target, false);
    }

    private static int maxDayOf(int year, int month) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month - 1, 1);
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
    }
}
