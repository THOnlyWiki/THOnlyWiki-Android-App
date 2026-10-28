package cc.thonly.app.ui;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.List;

import cc.thonly.app.R;
import cc.thonly.app.data.Event;
import cc.thonly.app.data.EventApiException;
import cc.thonly.app.data.EventCategory;
import cc.thonly.app.data.EventData;
import cc.thonly.app.data.EventStore;

/** 速查表页：年份 / 类型筛选 + 关键字搜索 + 列表。 */
public final class ListController implements EventStore.Listener {

    private final Activity activity;
    private final EventStore store;
    private final EventAdapter adapter;
    private final EditText searchInput;
    private final LinearLayout yearChips;
    private final LinearLayout categoryChips;
    private final TextView countLabel;
    private final TextView emptyLabel;

    /** null 表示不限类型 */
    private EventCategory categoryFilter;
    private String keyword = "";
    private int selectedYear;

    public ListController(Activity activity, View root, EventStore store, EventInteraction interaction) {
        this.activity = activity;
        this.store = store;
        this.adapter = new EventAdapter(activity, interaction);
        this.searchInput = root.findViewById(R.id.search);
        this.yearChips = root.findViewById(R.id.yearChips);
        this.categoryChips = root.findViewById(R.id.categoryChips);
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

        renderCategoryChips();
    }

    @Override
    public void onLoadingStarted(int year) {
        selectedYear = year;
        countLabel.setOnClickListener(null);
        countLabel.setText(R.string.loading);
    }

    @Override
    public void onDataLoaded(EventData data) {
        renderYearChips(data);
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

    private void renderYearChips(EventData data) {
        yearChips.removeAllViews();
        for (final int year : data.years()) {
            yearChips.addView(ChipFactory.create(activity, String.valueOf(year),
                    year == selectedYear, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (year != selectedYear) {
                                store.load(year, false);
                            }
                        }
                    }));
        }
    }

    private void renderCategoryChips() {
        categoryChips.removeAllViews();
        categoryChips.addView(ChipFactory.create(activity,
                activity.getString(R.string.category_all), categoryFilter == null,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setCategoryFilter(null);
                    }
                }));

        for (final EventCategory category : EventCategory.values()) {
            categoryChips.addView(ChipFactory.create(activity,
                    activity.getString(category.labelRes()), category == categoryFilter,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            setCategoryFilter(category);
                        }
                    }));
        }
    }

    private void setCategoryFilter(EventCategory category) {
        categoryFilter = category;
        renderCategoryChips();
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
