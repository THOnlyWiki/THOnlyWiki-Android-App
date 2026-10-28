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
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cc.thonly.app.AppConfig;
import cc.thonly.app.R;
import cc.thonly.app.util.AppLog;
import cc.thonly.app.util.AppUrls;

/**
 * 主站页：在 App 内直接打开站点首页。
 *
 * <p>站内跳转都会自动补上 {@code open_reference=app}，方便服务端统计 App 流量；
 * 站外链接交给系统浏览器。
 */
public final class WebController {

    /**
     * 站点在 App 里显示时去掉的两个元素：顶部 wordmark 图，以及侧栏导航里的水波纹元素。
     *
     * <p>{@code .mdui-ripple} 是站点的通用水波纹组件，顶部工具栏的
     * {@code #header-upload}、{@code #header-search-button} 也带这个类，
     * 所以必须限定在 {@code #site-navigation} 里面，只隐藏侧栏 logo。
     */
    private static final String APP_CHROME_CSS =
            "#p-banner,#site-navigation .mdui-ripple{display:none!important}";

    private static final String INJECT_CHROME_JS =
            "(function(){var id='thonly-app-chrome';"
                    + "var s=document.getElementById(id);"
                    + "if(!s){s=document.createElement('style');s.id=id;"
                    + "(document.head||document.documentElement).appendChild(s);}"
                    + "s.textContent=\"" + APP_CHROME_CSS + "\";})();";

    private static final String STYLE_TAG =
            "<style id=\"thonly-app-chrome\">" + APP_CHROME_CSS + "</style>";

    private final Activity activity;
    private final WebView webView;
    private final ProgressBar progress;
    /** WebView 的方法只能在主线程调，拦截时跑在后台线程，所以提前存下来 */
    private String userAgent = "";
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
        userAgent = settings.getUserAgentString();

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUri(request.getUrl());
            }

            /**
             * 在主文档到达渲染器之前把样式塞进去，避免"先渲染完整页面再收起"的跳动。
             *
             * <p>只拦站内 GET 主文档；3xx 一律放回给 WebView 自己处理（跳转目标会再拦一次），
             * 任何异常都返回 null 退回正常加载，最多闪一下，不会白屏。
             */
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request == null || !request.isForMainFrame()) {
                    return null;
                }
                if (!"GET".equalsIgnoreCase(request.getMethod())) {
                    return null;
                }
                Uri uri = request.getUrl();
                if (!AppUrls.isInternal(uri)) {
                    return null;
                }
                return injectIntoDocument(uri.toString());
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
                    AppLog.e("web", "加载失败 " + request.getUrl()
                            + " code=" + error.getErrorCode()
                            + " " + error.getDescription(), null);
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

    // ------------------------------------------------------------ 加载前注入样式

    /**
     * 自己把主文档下载下来，在 {@code <head>} 后面插入样式再交给 WebView。
     *
     * <p>这样样式在首次绘制前就生效，不会出现「先显示完整页面再收起」的跳动。
     * 任何异常都返回 null，退回 WebView 的正常加载。
     */
    private WebResourceResponse injectIntoDocument(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            // 跳转交给 WebView 自己处理，跳转目标会再被拦一次
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(AppConfig.CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(AppConfig.READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("User-Agent", userAgent);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null && !cookie.isEmpty()) {
                connection.setRequestProperty("Cookie", cookie);
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                return null;
            }
            String contentType = connection.getContentType();
            if (contentType == null
                    || !contentType.toLowerCase(Locale.ROOT).contains("text/html")) {
                return null;
            }

            Charset charset = charsetOf(contentType);
            String injected = insertStyle(readAll(connection.getInputStream(), charset));
            forwardCookies(url, connection);

            WebResourceResponse response = new WebResourceResponse(
                    "text/html", charset.name(),
                    new ByteArrayInputStream(injected.getBytes(charset)));
            response.setStatusCodeAndReasonPhrase(status, "OK");
            return response;
        } catch (Exception e) {
            AppLog.e("web", "注入样式失败，退回正常加载 " + url, e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** 拦截后 WebView 不再自己处理响应头，Set-Cookie 得手动塞回 CookieManager */
    private static void forwardCookies(String url, HttpURLConnection connection) {
        CookieManager manager = CookieManager.getInstance();
        boolean changed = false;
        for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
            if (entry.getKey() == null || !"Set-Cookie".equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            for (String value : entry.getValue()) {
                manager.setCookie(url, value);
                changed = true;
            }
        }
        if (changed) {
            manager.flush();
        }
    }

    private static String insertStyle(String html) {
        int head = html.toLowerCase(Locale.ROOT).indexOf("<head");
        if (head >= 0) {
            int close = html.indexOf('>', head);
            if (close > 0) {
                return html.substring(0, close + 1) + STYLE_TAG + html.substring(close + 1);
            }
        }
        return STYLE_TAG + html;
    }

    private static Charset charsetOf(String contentType) {
        String lower = contentType.toLowerCase(Locale.ROOT);
        int index = lower.indexOf("charset=");
        if (index >= 0) {
            String name = lower.substring(index + 8).trim();
            int end = name.indexOf(';');
            if (end > 0) {
                name = name.substring(0, end);
            }
            try {
                return Charset.forName(name.trim());
            } catch (Exception ignored) {
                // 落到 UTF-8
            }
        }
        return Charset.forName("UTF-8");
    }

    private static String readAll(InputStream stream, Charset charset) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int count;
        while ((count = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, count);
        }
        stream.close();
        return new String(buffer.toByteArray(), charset);
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
