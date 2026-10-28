package cc.thonly.app.ui;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import cc.thonly.app.R;
import cc.thonly.app.data.AppVersion;
import cc.thonly.app.data.EventApiException;
import cc.thonly.app.data.EventData;
import cc.thonly.app.data.EventStore;
import cc.thonly.app.data.RemoteEventRepository;
import cc.thonly.app.data.SettingsStore;
import cc.thonly.app.data.UpdateChecker;
import cc.thonly.app.data.UserSession;
import cc.thonly.app.util.CookieBridge;
import cc.thonly.app.util.FileCache;

/**
 * 应用入口，只负责装配：数据仓库、各页面控制器、右侧导航抽屉、启动时检查更新。
 * 具体业务都在各自的 Controller 里。
 */
public final class MainActivity extends Activity implements EventStore.Listener, AuthHost {

    private static final int PAGE_LIST = 0;
    private static final int PAGE_CALENDAR = 1;
    private static final int PAGE_WEB = 2;
    private static final int PAGE_LOGIN = 3;
    private static final int PAGE_REGISTER = 4;
    private static final int PAGE_SETTINGS = 5;

    /** 存档放在 filesDir，系统不会像 cacheDir 那样在空间紧张时清掉 */
    private static final String CACHE_DIR_NAME = "activity";
    private static final long DRAWER_ANIMATION_MS = 200L;
    /** 存档超过这个年龄才提示「本地存档」，太新的没必要打扰 */
    private static final long STALE_HINT_MS = 60L * 60L * 1000L;

    private EventStore store;
    private EventActions actions;
    private SettingsStore settings;
    private UserSession session;
    private UpdateChecker updateChecker;
    private FileCache cache;
    private AppVersion currentVersion;

    private ListController listController;
    private CalendarController calendarController;
    private WebController webController;
    private LoginController loginController;
    private RegisterController registerController;
    private SettingsController settingsController;

    private View[] pages;
    private View scrim;
    private View drawer;
    private TextView offlineBanner;
    private TextView[] navItems;
    private TextView navLoginItem;
    private TextView navRegisterItem;
    private TextView navLogoutItem;
    private TextView accountLabel;

    private int currentPage = PAGE_LIST;
    private boolean drawerOpen;
    private int drawerWidth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settings = new SettingsStore(this);
        session = new UserSession(this);
        updateChecker = new UpdateChecker();
        currentVersion = resolveCurrentVersion();
        cache = new FileCache(new File(getFilesDir(), CACHE_DIR_NAME));
        actions = new EventActions(this);
        store = new EventStore(new RemoteEventRepository(cache));

        // 上次登录的 Cookie 先灌进 WebView，主站打开就是登录态
        CookieBridge.apply(this, session.cookies());

        FrameLayout content = findViewById(R.id.content);
        View listPage = getLayoutInflater().inflate(R.layout.view_list, content, false);
        View calendarPage = getLayoutInflater().inflate(R.layout.view_calendar, content, false);
        View webPage = getLayoutInflater().inflate(R.layout.view_web, content, false);
        View loginPage = getLayoutInflater().inflate(R.layout.view_login, content, false);
        View registerPage = getLayoutInflater().inflate(R.layout.view_register, content, false);
        View settingsPage = getLayoutInflater().inflate(R.layout.view_settings, content, false);
        content.addView(listPage);
        content.addView(calendarPage);
        content.addView(webPage);
        content.addView(loginPage);
        content.addView(registerPage);
        content.addView(settingsPage);
        pages = new View[]{listPage, calendarPage, webPage, loginPage, registerPage, settingsPage};

        listController = new ListController(this, listPage, store, actions);
        calendarController = new CalendarController(this, calendarPage, store, actions);
        webController = new WebController(this, webPage);
        loginController = new LoginController(this, loginPage, session, this);
        registerController = new RegisterController(this, registerPage, session, this);
        settingsController = new SettingsController(this, settingsPage, settings,
                updateChecker, currentVersion, cache);
        store.addListener(listController);
        store.addListener(calendarController);
        store.addListener(this);

        offlineBanner = findViewById(R.id.offlineBanner);
        offlineBanner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                store.reload();
            }
        });

        bindHeader();
        bindDrawer();

        selectPage(PAGE_LIST);
        store.load(currentYear(), false);
        checkUpdateOnStart();
    }

    // ------------------------------------------------------------ 装配

    private void bindHeader() {
        findViewById(R.id.menuButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDrawer();
            }
        });
        findViewById(R.id.refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 在主站页时刷新网页，其它页刷新活动数据
                if (currentPage == PAGE_WEB) {
                    webController.reload();
                } else {
                    store.reload();
                }
            }
        });
    }

    private void bindDrawer() {
        drawer = findViewById(R.id.drawer);
        scrim = findViewById(R.id.scrim);
        drawerWidth = getResources().getDimensionPixelSize(R.dimen.drawer_width);
        drawer.setTranslationX(drawerWidth);

        scrim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDrawer();
            }
        });

        navItems = new TextView[]{
                findViewById(R.id.navList),
                findViewById(R.id.navCalendar),
                findViewById(R.id.navWeb),
                findViewById(R.id.navLogin),
                findViewById(R.id.navRegister),
                findViewById(R.id.navSettings)
        };
        for (int i = 0; i < navItems.length; i++) {
            final int page = i;
            navItems[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectPage(page);
                    closeDrawer();
                }
            });
        }

        TextView versionLabel = findViewById(R.id.navVersion);
        versionLabel.setText(getString(R.string.version_label, currentVersion.name()));

        navLoginItem = navItems[PAGE_LOGIN];
        navRegisterItem = navItems[PAGE_REGISTER];
        navLogoutItem = findViewById(R.id.navLogout);
        accountLabel = findViewById(R.id.accountLabel);
        navLogoutItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDrawer();
                loginController.logout();
            }
        });

        updateAuthUi();
    }

    /** 抽屉里的登录/注册项跟着登录态走：登录后换成「退出登录」 */
    private void updateAuthUi() {
        boolean loggedIn = session.isLoggedIn();
        navLoginItem.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        navRegisterItem.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        navLogoutItem.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        accountLabel.setText(loggedIn
                ? getString(R.string.drawer_account, session.username())
                : getString(R.string.drawer_not_logged_in));
    }

    private AppVersion resolveCurrentVersion() {
        try {
            String name = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            AppVersion version = AppVersion.parse(name);
            if (version != null) {
                return version;
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // 落到下面的兜底
        }
        return AppVersion.parse("0.0.0");
    }

    // ------------------------------------------------------------ 导航

    private void toggleDrawer() {
        if (drawerOpen) {
            closeDrawer();
        } else {
            openDrawer();
        }
    }

    private void openDrawer() {
        if (drawerOpen) {
            return;
        }
        drawerOpen = true;
        scrim.setVisibility(View.VISIBLE);
        scrim.setAlpha(0f);
        drawer.animate().translationX(0f).setDuration(DRAWER_ANIMATION_MS).start();
        scrim.animate().alpha(1f).setDuration(DRAWER_ANIMATION_MS).start();
    }

    private void closeDrawer() {
        if (!drawerOpen) {
            return;
        }
        drawerOpen = false;
        drawer.animate().translationX(drawerWidth).setDuration(DRAWER_ANIMATION_MS).start();
        scrim.animate().alpha(0f).setDuration(DRAWER_ANIMATION_MS).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (!drawerOpen) {
                    scrim.setVisibility(View.GONE);
                }
            }
        }).start();
    }

    private void selectPage(int page) {
        currentPage = page;
        for (int i = 0; i < pages.length; i++) {
            pages[i].setVisibility(i == page ? View.VISIBLE : View.GONE);
        }
        for (int i = 0; i < navItems.length; i++) {
            boolean active = i == page;
            navItems[i].setTextColor(getColor(active ? R.color.accent : R.color.text_primary));
            navItems[i].setTypeface(active
                    ? Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    : Typeface.create("sans-serif", Typeface.NORMAL));
        }

        if (page == PAGE_CALENDAR) {
            calendarController.onShown();
        } else {
            calendarController.onHidden();
        }
        if (page == PAGE_WEB) {
            webController.onShown();
        }
        if (page == PAGE_LOGIN) {
            loginController.onShown();
        }
        if (page == PAGE_REGISTER) {
            registerController.onShown();
        }
        if (page == PAGE_SETTINGS) {
            settingsController.onShown();
        }
    }

    // ------------------------------------------------------------ 登录态

    @Override
    public void onAuthenticated() {
        updateAuthUi();
        // 先刷新（没加载过就会直接打开首页），再切过去，避免重复请求
        webController.reload();
        selectPage(PAGE_WEB);
    }

    @Override
    public void onSessionChanged() {
        updateAuthUi();
        webController.reload();
    }

    @Override
    public void openLoginPage() {
        selectPage(PAGE_LOGIN);
    }

    @Override
    public void openRegisterPage() {
        selectPage(PAGE_REGISTER);
    }

    // ------------------------------------------------------------ 更新检查

    private void checkUpdateOnStart() {
        if (!settings.isAutoCheckUpdate()) {
            return;
        }
        updateChecker.check(new UpdateChecker.Listener() {
            @Override
            public void onLatestVersion(AppVersion latest) {
                UpdateDialog.showIfNeeded(MainActivity.this, currentVersion, latest, settings);
            }

            @Override
            public void onCheckFailed() {
                // 启动时静默失败，不打扰用户
            }
        });
    }

    private static int currentYear() {
        return Calendar.getInstance().get(Calendar.YEAR);
    }

    // ------------------------------------------------------------ 数据状态

    @Override
    public void onLoadingStarted(int year) {
        offlineBanner.setVisibility(View.GONE);
    }

    @Override
    public void onDataLoaded(EventData data) {
        updateStaleBanner();
    }

    @Override
    public void onLoadFailed(int year, EventApiException error) {
        // 失败提示由列表页负责，这里不重复打扰
    }

    /** 用的是本地存档、且存档已经有点旧时，顶部提示一下，避免用户以为是最新的 */
    private void updateStaleBanner() {
        long updatedAt = store.lastUpdatedAt();
        boolean stale = store.isFromCache()
                && updatedAt > 0
                && System.currentTimeMillis() - updatedAt > STALE_HINT_MS;
        if (!stale) {
            offlineBanner.setVisibility(View.GONE);
            return;
        }
        String time = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                .format(new Date(updatedAt));
        offlineBanner.setText(getString(R.string.offline_banner, time));
        offlineBanner.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------ 生命周期

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        actions.onRequestPermissionsResult(requestCode, grantResults);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (drawerOpen) {
                closeDrawer();
                return true;
            }
            if (currentPage == PAGE_WEB && webController.canGoBack()) {
                webController.goBack();
                return true;
            }
            if (currentPage != PAGE_LIST) {
                selectPage(PAGE_LIST);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        webController.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webController.onResume();
    }

    @Override
    protected void onDestroy() {
        store.removeListener(listController);
        store.removeListener(calendarController);
        store.removeListener(this);
        store.shutdown();
        webController.onDestroy();
        super.onDestroy();
    }
}
