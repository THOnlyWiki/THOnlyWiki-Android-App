package cc.thonly.app.ui;

import android.app.Activity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import cc.thonly.app.R;
import cc.thonly.app.data.Event;
import cc.thonly.app.data.EventApiException;
import cc.thonly.app.data.EventData;
import cc.thonly.app.data.EventStore;

/**
 * 日历页：月历网格 + 选中日期的活动列表。
 *
 * <p>年份策略：日历跟随 {@link EventStore} 里的年份，不主动去抢。
 * 只有用户主动翻月跨到别的年份时，才发起一次加载。
 * （早期版本在数据回调里无条件回拉自己那一年，导致列表切到 2025 又被拽回 2026。）
 */
public final class CalendarController implements EventStore.Listener, MonthView.OnDaySelectedListener {

    private final Activity activity;
    private final EventStore store;
    private final EventAdapter adapter;
    private final TextView monthTitle;
    private final MonthView monthView;
    private final TextView dayTitle;
    private final TextView emptyLabel;
    private final ListView dayList;
    private final Spinner regionSpinner;

    private int year;
    private int month;
    private int day;
    private boolean visible;
    /** 下拉里的地区列表，第 0 位是「全部地区」，不在这里存 */
    private List<String> regions = new ArrayList<String>();

    public CalendarController(Activity activity, View root, EventStore store,
                              EventInteraction interaction) {
        this.activity = activity;
        this.store = store;
        this.adapter = new EventAdapter(activity, interaction);
        this.monthTitle = root.findViewById(R.id.monthTitle);
        this.monthView = root.findViewById(R.id.monthView);
        this.dayTitle = root.findViewById(R.id.dayTitle);
        this.emptyLabel = root.findViewById(R.id.dayEmpty);
        this.regionSpinner = root.findViewById(R.id.regionSpinner);

        regionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // 选区就是唯一真相，不做额外缓存；重复回调只是重画一次，幂等
                refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        ListView dayList = root.findViewById(R.id.dayList);
        dayList.setAdapter(adapter);
        this.dayList = dayList;
        dayList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                interaction.onEventSelected(adapter.getItem(position));
            }
        });

        Calendar now = Calendar.getInstance();
        year = now.get(Calendar.YEAR);
        month = now.get(Calendar.MONTH) + 1;
        day = now.get(Calendar.DAY_OF_MONTH);

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
        root.findViewById(R.id.jumpToday).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goToToday();
            }
        });
    }

    /** 页面可见时才响应数据变化，避免在后台和列表抢年份 */
    public void onShown() {
        visible = true;
        followStoreYear();
        refresh();
    }

    public void onHidden() {
        visible = false;
    }

    @Override
    public void onLoadingStarted(int year) {
        // 加载中的状态由列表页展示，这里不需要额外处理
    }

    @Override
    public void onDataLoaded(EventData data) {
        if (!visible) {
            return;
        }
        followStoreYear();
        refresh();
    }

    @Override
    public void onLoadFailed(int year, EventApiException error) {
        // 失败提示由列表页负责，日历保持上一次的显示
    }

    @Override
    public void onDaySelected(int year, int month, int day) {
        this.year = year;
        this.month = month;
        this.day = day;
        showDayEvents(eventsOn(month, day));
    }

    private void followStoreYear() {
        EventData data = store.current();
        if (data == null || data.year() <= 0 || data.year() == year) {
            return;
        }
        year = data.year();
        day = Math.min(day, maxDayOf(year, month));
    }

    private void shiftMonth(int delta) {
        int previousYear = year;
        month += delta;
        if (month < 1) {
            month = 12;
            year -= 1;
        } else if (month > 12) {
            month = 1;
            year += 1;
        }
        day = Math.min(day, maxDayOf(year, month));

        // 用户主动翻到了别的年份，才去拉那一年的数据
        if (year != previousYear) {
            store.load(year, false);
        }
        refresh();
    }

    /** 定位到现在：回到当前年月并选中今天，必要时把那一年的数据拉下来 */
    private void goToToday() {
        Calendar now = Calendar.getInstance();
        int todayYear = now.get(Calendar.YEAR);
        year = todayYear;
        month = now.get(Calendar.MONTH) + 1;
        day = now.get(Calendar.DAY_OF_MONTH);

        EventData data = store.current();
        if (data == null || data.year() != todayYear) {
            store.load(todayYear, false);
        }
        refresh();
        dayList.setSelection(0);
    }

    private void refresh() {
        monthTitle.setText(activity.getString(R.string.month_title, year, month));
        monthView.setMonth(year, month);

        EventData data = store.current();
        boolean yearMatches = data != null && data.year() == year;
        renderRegionSpinner(yearMatches ? data : null);

        if (!yearMatches) {
            monthView.setMarkedDays(null);
            monthView.setSelectedDay(day);
            showDayEvents(Collections.<Event>emptyList());
            return;
        }

        monthView.setMarkedDays(data.markedDaysOf(month, selectedRegion()));
        monthView.setSelectedDay(day);
        showDayEvents(data.eventsOn(month, day, selectedRegion()));
    }

    /**
     * 地区下拉的选项来自当前年份数据。数据没变就不重建，
     * 否则每次刷新都会把用户选中的项弹回默认值。
     */
    private void renderRegionSpinner(EventData data) {
        List<String> values = data == null ? new ArrayList<String>() : data.regions();
        if (values.equals(regions) && regionSpinner.getAdapter() != null) {
            return;
        }

        // 重建前先记住当前选中的地区，重建后尽量还原
        String previous = selectedRegion();
        regions = values;

        List<String> labels = new ArrayList<String>();
        labels.add(activity.getString(R.string.region_all));
        labels.addAll(values);
        regionSpinner.setAdapter(new DropdownAdapter(activity, labels));

        int index = previous == null ? 0 : values.indexOf(previous) + 1;
        regionSpinner.setSelection(Math.max(index, 0), false);
    }

    /** 当前选中的地区，null 表示「全部地区」 */
    private String selectedRegion() {
        int position = regionSpinner.getSelectedItemPosition();
        if (position <= 0 || position - 1 >= regions.size()) {
            return null;
        }
        return regions.get(position - 1);
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
        return data.eventsOn(month, day, selectedRegion());
    }

    private static int maxDayOf(int year, int month) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month - 1, 1);
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
    }
}
