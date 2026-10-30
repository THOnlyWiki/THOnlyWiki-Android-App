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
import cc.thonly.app.data.ActivityEditor;
import cc.thonly.app.data.Event;
import cc.thonly.app.data.EventApiException;
import cc.thonly.app.data.EventCategory;
import cc.thonly.app.data.EventData;
import cc.thonly.app.data.EventDate;
import cc.thonly.app.data.EventStore;
import cc.thonly.app.data.FavoriteEvent;
import cc.thonly.app.data.Favorites;

/** 速查表页：年份 / 类型两个下拉筛选 + 关键字搜索 + 列表。 */
public final class ListController implements EventStore.Listener {

    private final Activity activity;
    private final EventStore store;
    private final EventAdapter adapter;
    private final EditText searchInput;
    private final Spinner yearSpinner;
    private final Spinner categorySpinner;
    private final Spinner regionSpinner;
    private final TextView countLabel;
    private final TextView emptyLabel;
    private final TextView editToggle;
    private final TextView addButton;
    private final TextView favoriteToggle;
    private final TextView countdownView;
    private final ListView listView;
    private final EventInteraction hostInteraction;
    private final EventEditDialog editDialog;

    /** null 表示不限类型 */
    private EventCategory categoryFilter;
    private String keyword = "";
    private int selectedYear;
    private List<Integer> availableYears = new ArrayList<Integer>();
    /** 地区下拉的候选项，第 0 位是「全部地区」，不存在这里 */
    private List<String> regions = new ArrayList<String>();
    /** 点了「今天」但数据还没加载回来时置位，加载完成后滚动 */
    private boolean pendingScrollToToday;
    /** 会话失效时的回调，由 MainActivity 注入（清登录态、收回编辑入口） */
    private Runnable sessionExpiredHandler;
    /** 编辑模式：点条目打开编辑弹窗，右上角出现「新增」 */
    private boolean editMode;
    /** 只看关注 */
    private boolean onlyFavorites;
    private Favorites favorites;
    private Runnable openItinerary;

    public ListController(Activity activity, View root, EventStore store, EventInteraction interaction,
                          ActivityEditor editor) {
        this.activity = activity;
        this.store = store;
        this.hostInteraction = interaction;
        this.adapter = new EventAdapter(activity, interaction);
        this.searchInput = root.findViewById(R.id.search);
        this.yearSpinner = root.findViewById(R.id.yearSpinner);
        this.categorySpinner = root.findViewById(R.id.categorySpinner);
        this.regionSpinner = root.findViewById(R.id.regionSpinner);
        this.countLabel = root.findViewById(R.id.listCount);
        this.emptyLabel = root.findViewById(R.id.listEmpty);
        this.editToggle = root.findViewById(R.id.editToggle);
        this.addButton = root.findViewById(R.id.addButton);
        this.favoriteToggle = root.findViewById(R.id.favoriteToggle);
        this.countdownView = root.findViewById(R.id.listCountdown);
        this.selectedYear = Calendar.getInstance().get(Calendar.YEAR);
        this.editDialog = new EventEditDialog(activity, editor, new EventEditDialog.Listener() {
            @Override
            public void onSaved() {
                // 增删改之后强制拉一次，别让本地存档盖住服务端的结果
                store.load(selectedYear, true);
            }

            @Override
            public void onSessionExpired() {
                if (sessionExpiredHandler != null) {
                    sessionExpiredHandler.run();
                }
            }
        });

        ListView listView = root.findViewById(R.id.listView);
        listView.setAdapter(adapter);
        this.listView = listView;

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Event event = adapter.getItem(position);
                if (editMode) {
                    editDialog.show(selectedYear, event);
                } else {
                    hostInteraction.onEventSelected(event);
                }
            }
        });

        root.findViewById(R.id.jumpToday).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                jumpToNow();
            }
        });

        editToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleEditMode();
            }
        });
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editDialog.show(selectedYear, null);
            }
        });

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
        setupRegionSpinner();
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
        // 老版本只存了 id 的关注，这里补上快照，「我的行程」才有内容
        if (favorites != null) {
            for (Event event : data.events()) {
                favorites.backfill(data.year(), event);
            }
        }
        renderYearSpinner();
        applyFilter();
        if (pendingScrollToToday) {
            pendingScrollToToday = false;
            if (data.year() == Calendar.getInstance().get(Calendar.YEAR)) {
                scrollToToday();
            }
        }
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

    private void setupRegionSpinner() {
        List<String> initial = new ArrayList<String>();
        initial.add(activity.getString(R.string.region_all));
        regionSpinner.setAdapter(new DropdownAdapter(activity, initial));
        regionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // 选区就是唯一真相，重复回调只是重算一次，幂等
                applyFilter();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    /**
     * 地区选项来自当年数据。数据没变就不重建，
     * 否则每次刷新都会把用户选中的项弹回默认值。
     */
    private void renderRegionSpinner(EventData data) {
        List<String> values = data.regions();
        if (values.equals(regions) && regionSpinner.getAdapter() != null) {
            return;
        }

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

    private void setCategoryFilter(EventCategory category) {
        categoryFilter = category;
        applyFilter();
    }

    /** 定位到现在：年份不是今年就先切过去，数据到了再滚动 */
    private void jumpToNow() {
        int todayYear = Calendar.getInstance().get(Calendar.YEAR);
        if (selectedYear != todayYear) {
            pendingScrollToToday = true;
            store.load(todayYear, false);
            return;
        }
        scrollToToday();
    }

    /** 滚到第一场「今天或之后」的活动；全年的都过去了就停在最后一条 */
    private void scrollToToday() {
        EventData data = store.current();
        if (data == null) {
            return;
        }
        List<Event> events = data.filter(categoryFilter, keyword, selectedRegion());
        if (events.isEmpty()) {
            return;
        }

        Calendar now = Calendar.getInstance();
        int nowMonth = now.get(Calendar.MONTH) + 1;
        int nowDay = now.get(Calendar.DAY_OF_MONTH);

        int target = events.size() - 1;
        for (int i = 0; i < events.size(); i++) {
            EventDate date = events.get(i).date();
            if (!date.isResolved()) {
                continue;
            }
            if (date.month() > nowMonth || (date.month() == nowMonth && date.day() >= nowDay)) {
                target = i;
                break;
            }
        }
        listView.setSelection(target);
    }

    private void applyFilter() {
        EventData data = store.current();
        if (data == null) {
            return;
        }
        renderRegionSpinner(data);
        List<Event> events = data.filter(categoryFilter, keyword, selectedRegion());
        if (onlyFavorites && favorites != null) {
            List<Event> onlyFavoritesList = new ArrayList<Event>();
            for (Event event : events) {
                if (favorites.isFavorite(selectedYear, event.id())) {
                    onlyFavoritesList.add(event);
                }
            }
            events = onlyFavoritesList;
        }
        // 年份可能变了，星标要用当年的 key
        adapter.setFavoriteContext(favorites, selectedYear, favoriteListener);
        adapter.submit(events);
        updateCountLabel(events.size());
        updateFavoriteToggle();

        emptyLabel.setText(onlyFavorites ? R.string.favorite_empty : R.string.empty_list);
        emptyLabel.setVisibility(events.isEmpty() ? View.VISIBLE : View.GONE);
        updateCountdown();
    }

    /**
     * 顶部那行倒计时：优先用关注里最近的一场，没关注就用当年最近的一场。
     */
    private void updateCountdown() {
        if (favorites != null) {
            for (FavoriteEvent favorite : favorites.list()) {
                if (favorite.isUpcoming() && !favorite.isCancelled()) {
                    showCountdown(favorite.name(), favorite.daysUntil());
                    return;
                }
            }
        }
        EventData current = store.current();
        if (current == null) {
            countdownView.setVisibility(View.GONE);
            return;
        }
        Event nearest = null;
        long nearestDays = Long.MAX_VALUE;
        for (Event event : current.events()) {
            if (!event.date().isResolved() || event.status().isCancelled()) {
                continue;
            }
            long days = daysUntil(event.date());
            if (days >= 0 && days < nearestDays) {
                nearestDays = days;
                nearest = event;
            }
        }
        if (nearest == null) {
            countdownView.setVisibility(View.GONE);
            return;
        }
        showCountdown(nearest.name(), nearestDays);
    }

    private void showCountdown(String name, long days) {
        countdownView.setText(days == 0
                ? activity.getString(R.string.countdown_today, name)
                : activity.getString(R.string.countdown_line, name, days));
        countdownView.setVisibility(View.VISIBLE);
    }

    private static long daysUntil(EventDate date) {
        Calendar target = Calendar.getInstance();
        target.set(date.year(), date.month() - 1, date.day(), 0, 0, 0);
        target.set(Calendar.MILLISECOND, 0);
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        return Math.round((target.getTimeInMillis() - today.getTimeInMillis()) / 86400000.0);
    }

    private void updateFavoriteToggle() {
        int count = favorites == null ? 0 : favorites.count();
        if (count == 0 && !onlyFavorites) {
            favoriteToggle.setVisibility(View.GONE);
            return;
        }
        favoriteToggle.setVisibility(View.VISIBLE);
        favoriteToggle.setText(activity.getString(R.string.favorite_count, count));
        favoriteToggle.setTextColor(activity.getColor(
                onlyFavorites ? R.color.accent : R.color.text_secondary));
    }

    private void updateCountLabel(int count) {
        if (editMode) {
            countLabel.setText(R.string.edit_mode_hint);
        } else {
            countLabel.setText(activity.getString(R.string.count_in_year, selectedYear, count));
        }
    }

    // ------------------------------------------------------------ 编辑模式

    /** 登录后才出现「编辑」入口；退出登录时自动退出编辑模式 */
    public void setLoggedIn(boolean loggedIn) {
        editToggle.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        if (!loggedIn && editMode) {
            editMode = false;
            updateEditUi();
            applyFilter();
        }
    }

    public void setOnSessionExpired(Runnable handler) {
        this.sessionExpiredHandler = handler;
    }

    /** 注入关注列表；星标只在速查表页显示 */
    public void setFavorites(Favorites favorites) {
        this.favorites = favorites;
        adapter.setFavoriteContext(favorites, selectedYear, favoriteListener);
        adapter.setFavoriteEnabled(true);
        favoriteToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onlyFavorites = !onlyFavorites;
                applyFilter();
            }
        });
    }

    /** 当前选中的年份（组队页要知道队伍挂在哪个年份下） */
    public int selectedYear() {
        return selectedYear;
    }

    /** 顶部倒计时那行点了去哪 */
    public void setOnOpenItinerary(Runnable handler) {
        this.openItinerary = handler;
        countdownView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (openItinerary != null) {
                    openItinerary.run();
                }
            }
        });
    }

    private final EventAdapter.OnFavoriteListener favoriteListener =
            new EventAdapter.OnFavoriteListener() {
                @Override
                public void onFavoriteChanged() {
                    applyFilter();
                }
            };

    private void toggleEditMode() {
        editMode = !editMode;
        updateEditUi();
        applyFilter();
    }

    private void updateEditUi() {
        editToggle.setText(editMode ? R.string.edit_done : R.string.edit_entry);
        addButton.setVisibility(editMode ? View.VISIBLE : View.GONE);
        adapter.setEditMode(editMode);
    }
}
