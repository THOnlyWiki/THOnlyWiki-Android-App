package cc.thonly.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cc.thonly.app.R;
import cc.thonly.app.data.ActivityEditor;
import cc.thonly.app.data.AppSidebar;
import cc.thonly.app.data.AppVersion;
import cc.thonly.app.data.EventApiException;
import cc.thonly.app.data.EventData;
import cc.thonly.app.data.EventStore;
import cc.thonly.app.data.Favorites;
import cc.thonly.app.data.RemoteEventRepository;
import cc.thonly.app.data.SettingsStore;
import cc.thonly.app.data.SidebarRepository;
import cc.thonly.app.data.UpdateChecker;
import cc.thonly.app.data.UserSession;
import cc.thonly.app.util.AppLog;
import cc.thonly.app.util.CookieBridge;
import cc.thonly.app.util.DeviceInfo;
import cc.thonly.app.util.FileCache;

/**
 * 应用入口，只负责装配：数据仓库、各页面控制器、右侧导航抽屉、启动时检查更新。
 *
 * <p>抽屉分两区：
 * <ul>
 *   <li>内容区 —— 速查表 / 日历 / 主站，可被服务端下发的 {@code WikiKernel:Sidebar-App} 接管</li>
 *   <li>系统区 —— 账号、设置，永远保留，避免配置写坏后用户连退出都找不到</li>
 * </ul>
 */
public final class MainActivity extends Activity implements EventStore.Listener, AuthHost {

    private static final int PAGE_LIST = 0;
    private static final int PAGE_CALENDAR = 1;
    private static final int PAGE_WEB = 2;
    private static final int PAGE_LOGIN = 3;
    private static final int PAGE_REGISTER = 4;
    private static final int PAGE_SETTINGS = 5;
    private static final int PAGE_TOOLBOX = 6;
    private static final int PAGE_MUSIC = 7;

    /** 不是页面，只用于「这一行没有对应的原生页」 */
    private static final int PAGE_NONE = -1;

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
    private SidebarRepository sidebarRepository;
    private FileCache cache;
    private AppVersion currentVersion;

    private ListController listController;
    private CalendarController calendarController;
    private WebController webController;
    private LoginController loginController;
    private RegisterController registerController;
    private SettingsController settingsController;
    private ToolboxController toolboxController;
    private MusicController musicController;

    private View[] pages;
    private View scrim;
    private View drawer;
    private View navBuiltIn;
    private LinearLayout navDynamic;
    private TextView offlineBanner;
    private TextView navLoginItem;
    private TextView navRegisterItem;
    private TextView navLogoutItem;
    private TextView navToolboxItem;
    private TextView accountLabel;

    /** 固定行（内置 + 系统）与动态行分开存，动态行每次重新下发时重建 */
    private final List<TextView> fixedRows = new ArrayList<TextView>();
    private final List<Integer> fixedPages = new ArrayList<Integer>();
    private final List<TextView> dynamicRows = new ArrayList<TextView>();
    private final List<Integer> dynamicPages = new ArrayList<Integer>();

    private int currentPage = PAGE_LIST;
    private boolean drawerOpen;
    private int drawerWidth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AppLog.init(this);
        installCrashHandler();

        settings = new SettingsStore(this);
        session = new UserSession(this);
        updateChecker = new UpdateChecker();
        currentVersion = resolveCurrentVersion();
        logEnvironment();
        cache = new FileCache(new File(getFilesDir(), CACHE_DIR_NAME));
        actions = new EventActions(this);
        store = new EventStore(new RemoteEventRepository(cache));
        sidebarRepository = new SidebarRepository(cache);

        // 上次登录的 Cookie 先灌进 WebView，主站打开就是登录态
        CookieBridge.apply(this, session.cookies());

        FrameLayout content = findViewById(R.id.content);
        View listPage = getLayoutInflater().inflate(R.layout.view_list, content, false);
        View calendarPage = getLayoutInflater().inflate(R.layout.view_calendar, content, false);
        View webPage = getLayoutInflater().inflate(R.layout.view_web, content, false);
        View loginPage = getLayoutInflater().inflate(R.layout.view_login, content, false);
        View registerPage = getLayoutInflater().inflate(R.layout.view_register, content, false);
        View settingsPage = getLayoutInflater().inflate(R.layout.view_settings, content, false);
        View toolboxPage = getLayoutInflater().inflate(R.layout.view_toolbox, content, false);
        View musicPage = getLayoutInflater().inflate(R.layout.view_music, content, false);
        content.addView(listPage);
        content.addView(calendarPage);
        content.addView(webPage);
        content.addView(loginPage);
        content.addView(registerPage);
        content.addView(settingsPage);
        content.addView(toolboxPage);
        content.addView(musicPage);
        pages = new View[]{listPage, calendarPage, webPage, loginPage, registerPage,
                settingsPage, toolboxPage, musicPage};

        listController = new ListController(this, listPage, store, actions,
                new ActivityEditor(session));
        listController.setOnSessionExpired(new Runnable() {
            @Override
            public void run() {
                // 服务端说会话失效：按退出登录处理，收回编辑入口和云端导航
                onSessionChanged();
            }
        });
        listController.setFavorites(new Favorites(this));
        calendarController = new CalendarController(this, calendarPage, store, actions);
        webController = new WebController(this, webPage);
        loginController = new LoginController(this, loginPage, session, this);
        registerController = new RegisterController(this, registerPage, session, this);
        settingsController = new SettingsController(this, settingsPage, settings,
                updateChecker, currentVersion, cache);
        toolboxController = new ToolboxController(this, toolboxPage, new Runnable() {
            @Override
            public void run() {
                selectPage(PAGE_MUSIC);
            }
        });
        musicController = new MusicController(this, musicPage);
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
        listController.setLoggedIn(session.isLoggedIn());
        store.load(currentYear(), false);
        checkUpdateOnStart();
        loadRemoteSidebar();
    }

    // ------------------------------------------------------------ 日志

    /** 线上排查用：把未捕获异常写进日志文件，再交回系统默认处理 */
    private void installCrashHandler() {
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable error) {
                AppLog.e("crash", "未捕获异常，线程 " + thread.getName(), error);
                if (previous != null) {
                    previous.uncaughtException(thread, error);
                }
            }
        });
    }

    private void logEnvironment() {
        AppLog.i("app", "启动 " + currentVersion.name()
                + " · " + DeviceInfo.model(this)
                + " · Android " + Build.VERSION.RELEASE + "(" + Build.VERSION.SDK_INT + ")");
        // WebView 版本要初始化 WebView 内核，放后台线程，别卡启动
        new Thread(new Runnable() {
            @Override
            public void run() {
                AppLog.i("app", "WebView " + DeviceInfo.webViewVersion(MainActivity.this));
            }
        }, "log-webview").start();
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
        navBuiltIn = findViewById(R.id.navBuiltIn);
        navDynamic = findViewById(R.id.navDynamic);
        drawerWidth = getResources().getDimensionPixelSize(R.dimen.drawer_width);
        drawer.setTranslationX(drawerWidth);

        scrim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDrawer();
            }
        });

        bindNavRow(R.id.navList, PAGE_LIST);
        bindNavRow(R.id.navCalendar, PAGE_CALENDAR);
        bindNavRow(R.id.navToolbox, PAGE_TOOLBOX);
        bindNavRow(R.id.navWeb, PAGE_WEB);
        bindNavRow(R.id.navLogin, PAGE_LOGIN);
        bindNavRow(R.id.navRegister, PAGE_REGISTER);
        bindNavRow(R.id.navSettings, PAGE_SETTINGS);

        navLoginItem = findViewById(R.id.navLogin);
        navRegisterItem = findViewById(R.id.navRegister);
        navLogoutItem = findViewById(R.id.navLogout);
        navToolboxItem = findViewById(R.id.navToolbox);
        accountLabel = findViewById(R.id.accountLabel);
        navLogoutItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDrawer();
                loginController.logout();
            }
        });

        TextView versionLabel = findViewById(R.id.navVersion);
        versionLabel.setText(getString(R.string.version_label, currentVersion.name()));

        updateAuthUi();
    }

    private void bindNavRow(int viewId, final int page) {
        TextView row = findViewById(viewId);
        fixedRows.add(row);
        fixedPages.add(page);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectPage(page);
                closeDrawer();
            }
        });
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

    // ------------------------------------------------------------ 云端抽屉配置

    private void loadRemoteSidebar() {
        // 云端抽屉只给登录用户下发，未登录就用内置导航
        if (!session.isLoggedIn()) {
            renderSidebar(AppSidebar.empty());
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final AppSidebar sidebar = sidebarRepository.load(currentVersion);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        renderSidebar(sidebar);
                    }
                });
            }
        }, "sidebar").start();
    }

    private void renderSidebar(AppSidebar sidebar) {
        navDynamic.removeAllViews();
        dynamicRows.clear();
        dynamicPages.clear();

        if (sidebar.isEmpty()) {
            // 没配置、配置坏了、或 App 版本低于 min_app_version —— 用内置导航
            navBuiltIn.setVisibility(View.VISIBLE);
            navToolboxItem.setVisibility(View.VISIBLE);
            updateNavHighlight();
            return;
        }

        navBuiltIn.setVisibility(sidebar.isReplaceBuiltIn() ? View.GONE : View.VISIBLE);

        boolean toolboxInConfig = false;
        for (AppSidebar.Section section : sidebar.sections()) {
            if (!section.title().isEmpty()) {
                navDynamic.addView(createSectionTitle(section.title()));
            }
            for (AppSidebar.Item item : section.items()) {
                navDynamic.addView(createNavRow(item));
                if (item.target() == AppSidebar.Target.APP
                        && pageIdOf(item.page()) == PAGE_TOOLBOX) {
                    toolboxInConfig = true;
                }
            }
        }
        // 云端已经给了「百宝箱」入口就不再显示固定的那个，避免出现两个
        navToolboxItem.setVisibility(toolboxInConfig ? View.GONE : View.VISIBLE);
        updateNavHighlight();
    }

    private TextView createSectionTitle(String title) {
        TextView view = new TextView(this);
        view.setText(title);
        view.setTextSize(12);
        view.setTextColor(getColor(R.color.text_secondary));
        view.setPadding(dp(20), dp(14), dp(20), dp(4));
        return view;
    }

    private TextView createNavRow(final AppSidebar.Item item) {
        TextView row = (TextView) getLayoutInflater().inflate(R.layout.item_nav, navDynamic, false);
        row.setText(item.label());

        int page = item.target() == AppSidebar.Target.APP ? pageIdOf(item.page()) : PAGE_NONE;
        dynamicRows.add(row);
        dynamicPages.add(page);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDrawer();
                route(item);
            }
        });
        return row;
    }

    /** 一条配置最终去哪：原生页 / App 内网页 / 系统浏览器 */
    private void route(AppSidebar.Item item) {
        if (item.target() == AppSidebar.Target.APP) {
            int page = pageIdOf(item.page());
            if (page != PAGE_NONE) {
                selectPage(page);
            } else {
                // 云端配了当前版本还不认识的页面：明确告知，不做静默降级
                AppLog.e("sidebar", "未知页面: " + item.page() + " (" + item.label() + ")", null);
                showUnsupportedPageDialog(item);
            }
            return;
        }
        if (item.target() == AppSidebar.Target.EXTERNAL) {
            openExternally(item.url());
            return;
        }
        openInWeb(item.url());
    }

    private void showUnsupportedPageDialog(AppSidebar.Item item) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.unsupported_page_title)
                .setMessage(getString(R.string.unsupported_page_message, item.label()))
                .setPositiveButton(R.string.update_go, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        UpdateDialog.openDownloadPage(MainActivity.this);
                    }
                })
                .setNegativeButton(R.string.edit_cancel, null)
                .show();
    }

    private int pageIdOf(String page) {
        if ("list".equals(page)) {
            return PAGE_LIST;
        }
        if ("calendar".equals(page)) {
            return PAGE_CALENDAR;
        }
        if ("web".equals(page)) {
            return PAGE_WEB;
        }
        if ("login".equals(page)) {
            return PAGE_LOGIN;
        }
        if ("register".equals(page)) {
            return PAGE_REGISTER;
        }
        if ("settings".equals(page)) {
            return PAGE_SETTINGS;
        }
        if ("toolbox".equals(page)) {
            return PAGE_TOOLBOX;
        }
        return PAGE_NONE;
    }

    private void openInWeb(String url) {
        webController.openUrl(url);
        selectPage(PAGE_WEB);
    }

    private void openExternally(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException ignored) {
            // 没有浏览器就算了
        }
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
        updateNavHighlight();

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
        if (page == PAGE_TOOLBOX) {
            toolboxController.onShown();
        }
    }

    private void updateNavHighlight() {
        applyHighlight(fixedRows, fixedPages);
        applyHighlight(dynamicRows, dynamicPages);
    }

    private void applyHighlight(List<TextView> rows, List<Integer> rowPages) {
        for (int i = 0; i < rows.size(); i++) {
            boolean active = rowPages.get(i) == currentPage;
            TextView row = rows.get(i);
            row.setTextColor(getColor(active ? R.color.accent : R.color.text_primary));
            row.setTypeface(active
                    ? Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    : Typeface.create("sans-serif", Typeface.NORMAL));
        }
    }

    // ------------------------------------------------------------ 登录态

    @Override
    public void onAuthenticated() {
        updateAuthUi();
        listController.setLoggedIn(true);
        loadRemoteSidebar();
        // 先刷新（没加载过就会直接打开首页），再切过去，避免重复请求
        webController.reload();
        selectPage(PAGE_WEB);
    }

    @Override
    public void onSessionChanged() {
        updateAuthUi();
        listController.setLoggedIn(false);
        // 退出登录后云端导航一并收回
        renderSidebar(AppSidebar.empty());
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
        musicController.onDestroy();
        super.onDestroy();
    }
}
