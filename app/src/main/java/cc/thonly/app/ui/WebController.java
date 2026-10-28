package cc.thonly.app.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;

import cc.thonly.app.AppConfig;
import cc.thonly.app.R;
import cc.thonly.app.util.AppUrls;

/**
 * 主站页：在 App 内直接打开站点首页。
 *
 * <p>站内跳转都会自动补上 {@code open_reference=app}，方便服务端统计 App 流量；
 * 站外链接交给系统浏览器。
 */
public final class WebController {

    /**
     * 站点自带的导航外壳在 App 里是重复的（App 有自己的抽屉），进页面后隐藏掉。
     *
     * <p>注意不要用 {@code .mdui-ripple} 这种工具类做选择器——它还挂在搜索按钮和
     * 上传按钮上，会一起被干掉。这里只打具体的结构元素，保留 {@code #mw-header-container}
     * 里的搜索框（App 没有站点搜索能力）。
     */
    private static final String APP_CHROME_CSS =
            "#mw-site-navigation,#p-logo-text,#mw-footer-container,#menus-cover{display:none!important}"
                    + "#mw-content-container{margin-left:0!important;padding-top:0!important}";

    private static final String INJECT_CHROME_JS =
            "(function(){var id='thonly-app-chrome';"
                    + "var s=document.getElementById(id);"
                    + "if(!s){s=document.createElement('style');s.id=id;"
                    + "(document.head||document.documentElement).appendChild(s);}"
                    + "s.textContent=\"" + APP_CHROME_CSS + "\";})();";

    private final Activity activity;
    private final WebView webView;
    private final ProgressBar progress;
    private final TextView errorView;

    private boolean loaded;

    public WebController(Activity activity, View root) {
        this.activity = activity;
        this.webView = root.findViewById(R.id.webView);
        this.progress = root.findViewById(R.id.webProgress);
        this.errorView = root.findViewById(R.id.webError);

        configureWebView();
        errorView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadHome();
            }
        });
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setUserAgentString(settings.getUserAgentString() + AppConfig.USER_AGENT);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUri(request.getUrl());
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                errorView.setVisibility(View.GONE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                // 页面渲染完再注入，会有一下布局跳动；服务端按 open_reference=app
                // 输出同样的 CSS 就不会，这份是兜底。
                view.evaluateJavascript(INJECT_CHROME_JS, null);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()) {
                    progress.setVisibility(View.GONE);
                    errorView.setVisibility(View.VISIBLE);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        });
    }

    /** @return true 表示这次跳转由我们接管 */
    private boolean handleUri(Uri uri) {
        if (!AppUrls.isInternal(uri)) {
            openExternally(uri);
            return true;
        }
        if (AppUrls.hasReference(uri)) {
            return false;
        }
        webView.loadUrl(AppUrls.withReference(uri.toString()));
        return true;
    }

    private void openExternally(Uri uri) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException ignored) {
            // 没有应用能处理就算了，留在当前页
        }
    }

    public void onShown() {
        if (!loaded) {
            loadHome();
        }
    }

    public void loadHome() {
        openUrl(AppUrls.HOME);
    }

    /**
     * 打开站内地址。传相对路径（如 {@code /特殊:最近更改}）会自动补全域名与
     * {@code open_reference=app}；传完整 URL 时按原样处理。
     */
    public void openUrl(String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        String absolute = url;
        if (!url.startsWith("http")) {
            absolute = AppConfig.BASE_URL + (url.startsWith("/") ? url : "/" + url);
        }
        loaded = true;
        errorView.setVisibility(View.GONE);
        webView.loadUrl(AppUrls.withReference(absolute));
    }

    /** 登录/注册成功后刷新，让页面带上新的登录态 */
    public void reload() {
        if (webView.getUrl() == null) {
            loadHome();
        } else {
            webView.reload();
        }
    }

    public boolean canGoBack() {
        return webView.canGoBack();
    }

    public void goBack() {
        webView.goBack();
    }

    public void onPause() {
        webView.onPause();
        CookieManager.getInstance().flush();
    }

    public void onResume() {
        webView.onResume();
    }

    public void onDestroy() {
        webView.destroy();
    }
}
