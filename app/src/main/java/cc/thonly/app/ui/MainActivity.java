package cc.thonly.app.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.util.Calendar;

import cc.thonly.app.R;
import cc.thonly.app.data.EventStore;
import cc.thonly.app.data.RemoteEventRepository;
import cc.thonly.app.util.FileCache;

/**
 * 应用入口，只负责装配：创建数据仓库、两个页面控制器、底部导航。
 * 具体业务都在 {@link ListController} 和 {@link CalendarController} 里。
 */
public final class MainActivity extends Activity {

    private static final int TAB_LIST = 0;
    private static final int TAB_CALENDAR = 1;
    private static final String CACHE_DIR_NAME = "activity";

    private EventStore store;
    private EventActions actions;
    private ListController listController;
    private CalendarController calendarController;

    private View listPage;
    private View calendarPage;
    private TextView[] tabViews;
    private int currentTab = TAB_LIST;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        actions = new EventActions(this);
        store = new EventStore(new RemoteEventRepository(
                new FileCache(new File(getCacheDir(), CACHE_DIR_NAME))));

        FrameLayout content = findViewById(R.id.content);
        listPage = getLayoutInflater().inflate(R.layout.view_list, content, false);
        calendarPage = getLayoutInflater().inflate(R.layout.view_calendar, content, false);
        content.addView(listPage);
        content.addView(calendarPage);

        listController = new ListController(this, listPage, store, actions);
        calendarController = new CalendarController(this, calendarPage, store, actions);
        store.addListener(listController);
        store.addListener(calendarController);

        bindTabs();
        findViewById(R.id.refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                store.reload();
            }
        });

        selectTab(TAB_LIST);
        store.load(currentYear(), false);
    }

    private void bindTabs() {
        tabViews = new TextView[]{
                findViewById(R.id.tab0),
                findViewById(R.id.tab1)
        };
        for (int i = 0; i < tabViews.length; i++) {
            final int index = i;
            tabViews[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectTab(index);
                }
            });
        }
    }

    private void selectTab(int index) {
        currentTab = index;
        listPage.setVisibility(index == TAB_LIST ? View.VISIBLE : View.GONE);
        calendarPage.setVisibility(index == TAB_CALENDAR ? View.VISIBLE : View.GONE);

        for (int i = 0; i < tabViews.length; i++) {
            boolean active = i == index;
            tabViews[i].setTextColor(getColor(active ? R.color.accent : R.color.text_secondary));
            tabViews[i].setSelected(active);
        }

        if (index == TAB_CALENDAR) {
            calendarController.onShown();
        }
    }

    private static int currentYear() {
        return Calendar.getInstance().get(Calendar.YEAR);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        actions.onRequestPermissionsResult(requestCode, grantResults);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && currentTab == TAB_CALENDAR) {
            selectTab(TAB_LIST);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        store.removeListener(listController);
        store.removeListener(calendarController);
        store.shutdown();
        super.onDestroy();
    }
}
