package cc.thonly.app.ui;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import cc.thonly.app.R;
import cc.thonly.app.data.Event;
import cc.thonly.app.data.EventApiException;
import cc.thonly.app.data.EventCategory;
import cc.thonly.app.data.EventData;
import cc.thonly.app.data.EventStore;

/** 速查表页：年份 / 类型两个下拉筛选 + 关键字搜索 + 列表。 */
public final class ListController implements EventStore.Listener {

    private final Activity activity;
    private final EventStore store;
    private final EventAdapter adapter;
    private final EditText searchInput;
    private final Spinner yearSpinner;
    private final Spinner categorySpinner;
    private final TextView countLabel;
    private final TextView emptyLabel;

    /** null 表示不限类型 */
    private EventCategory categoryFilter;
    private String keyword = "";
    private int selectedYear;
    private List<Integer> availableYears = new ArrayList<Integer>();

    public ListController(Activity activity, View root, EventStore store, EventInteraction interaction) {
        this.activity = activity;
        this.store = store;
        this.adapter = new EventAdapter(activity, interaction);
        this.searchInput = root.findViewById(R.id.search);
        this.yearSpinner = root.findViewById(R.id.yearSpinner);
        this.categorySpinner = root.findViewById(R.id.categorySpinner);
        this.countLabel = root.findViewById(R.id.listCount);
        this.emptyLabel = root.findViewById(R.id.listEmpty);
        this.selectedYear = Calendar.getInstance().get(Calendar.YEAR);

        ListView listView = root.findViewById(R.id.listView);
        listView.setAdapter(adapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                keyword = s == null ? "" : s.toString();
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        setupCategorySpinner();
        renderYearSpinner();
    }

    @Override
    public void onLoadingStarted(int year) {
        selectedYear = year;
        countLabel.setOnClickListener(null);
        countLabel.setText(R.string.loading);
    }

    @Override
    public void onDataLoaded(EventData data) {
        renderYearSpinner();
        applyFilter();
    }

    @Override
    public void onLoadFailed(final int year, EventApiException error) {
        countLabel.setText(R.string.load_failed_retry);
        countLabel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                store.load(year, true);
            }
        });
    }

    // ------------------------------------------------------------ 下拉菜单

    private void setupCategorySpinner() {
        List<String> labels = new ArrayList<String>();
        labels.add(activity.getString(R.string.category_all));
        for (EventCategory category : EventCategory.values()) {
            labels.add(activity.getString(category.labelRes()));
        }
        categorySpinner.setAdapter(new DropdownAdapter(activity, labels));
        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                setCategoryFilter(position == 0 ? null : EventCategory.values()[position - 1]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    /**
     * 年份下拉。数据还没回来时只显示当前年份占位；
     * 选中项与 {@link #selectedYear} 相同就什么都不做，避免 setSelection 触发多余请求。
     */
    private void renderYearSpinner() {
        EventData data = store.current();
        List<Integer> years = data == null ? new ArrayList<Integer>() : new ArrayList<Integer>(data.years());
        if (years.isEmpty()) {
            years.add(selectedYear);
        }
        availableYears = years;

        List<String> labels = new ArrayList<String>();
        for (int year : years) {
            labels.add(String.valueOf(year));
        }
        yearSpinner.setAdapter(new DropdownAdapter(activity, labels));

        int index = years.indexOf(selectedYear);
        yearSpinner.setSelection(index < 0 ? 0 : index, false);

        yearSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= availableYears.size()) {
                    return;
                }
                int year = availableYears.get(position);
                if (year != selectedYear) {
                    store.load(year, false);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setCategoryFilter(EventCategory category) {
        categoryFilter = category;
        applyFilter();
    }

    private void applyFilter() {
        EventData data = store.current();
        if (data == null) {
            return;
        }
        List<Event> events = data.filter(categoryFilter, keyword);
        adapter.submit(events);
        countLabel.setText(activity.getString(R.string.count_in_year, selectedYear, events.size()));
        emptyLabel.setVisibility(events.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
