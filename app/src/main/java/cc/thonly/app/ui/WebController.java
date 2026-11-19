package cc.thonly.app.ui;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.json.JSONArray;

import cc.thonly.app.AppConfig;
import cc.thonly.app.R;
import cc.thonly.app.data.ServerConfig;
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
            "#p-banner,#site-navigation .mdui-ripple{display:none!important}"
            // 插件的精修样式用桌面 padding 覆盖了它自己的移动端规则，
            // 手机上图片会被挤到只剩 4%，这里改回窄边距。
            + "@media(max-width:600px){"
            + ".wiki-image-viewer-stage{padding:48px 8px 72px!important}"
            + "}";

    /** 网页里 &lt;input type="file"&gt; 打开系统选择器；同一时间只处理一个请求 */
    public static final int REQUEST_FILE_CHOOSER = 0x574B;

    /** Android 10 以下保存到公共下载目录需要写外部存储权限 */
    private static final int REQUEST_WRITE_STORAGE = 0x574D;

    /** 网页端生成的缩放图一般只有几 MB；设上限避免把大文件读进内存 */
    private static final int MAX_SAVE_BYTES = 20 * 1024 * 1024;

    /** 返回时用的页面快照上限，避免把超大页面常驻内存 */
    private static final int MAX_CACHED_PAGE_CHARS = 4 * 1024 * 1024;
    private static final int MAX_CACHED_PAGES = 6;

    private static final String BRIDGE_NAME = "ThonlyApp";

    private final Activity activity;
    private final WebView webView;
    private final ProgressBar progress;
    private final TextView errorView;

    /** 等系统文件选择器返回的 WebView 回调；取消或页面切换时必须回 null */
    private ValueCallback<Uri[]> fileChooserCallback;

    /**
     * JS 桥的随机口令。WebView 的 addJavascriptInterface 对页面里的跨域 iframe
     * 也可见，跨域 iframe 读不到父页面里的口令，就没法调用保存 / 打印。
     */
    private final String bridgeToken = UUID.randomUUID().toString();

    /**
     * 返回时优先用上一页的 DOM 快照。站点主文档是 {@code Cache-Control: no-cache}，
     * WebView 的历史回退仍会重新请求服务器；这里把页面 HTML 留在内存里，
     * 返回时直接交给 WebView，服务器完全不会被访问。
     */
    private final Map<String, String> pageCache = Collections.synchronizedMap(
            new LinkedHashMap<String, String>(8, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_CACHED_PAGES;
                }
            });

    /** 这次后退是否要尝试用页面快照；没有命中就退回正常网络加载 */
    private volatile boolean backNavigation;

    /**
     * 刚才是哪个 URL 用了页面快照。快照页面的脚本会重新跑，
     * 但 500ms 时动态内容未必都回来了；不要再拿它覆盖原来的完整快照。
     */
    private volatile String servedFromCacheUrl;

    private final Runnable clearBackNavigation = new Runnable() {
        @Override
        public void run() {
            backNavigation = false;
        }
    };

    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private ViewGroup fullscreenDecor;
    private int previousSystemUiVisibility;
    private boolean previousFullscreenFlag;

    /** 自定义播放器用 CSS 全屏（WebView 不支持 Element.requestFullscreen 时） */
    private boolean pageFullscreen;
    private int pageFullscreenPreviousUi;
    private boolean pageFullscreenPreviousFlag;
    private int pageFullscreenBannerVisibility = View.GONE;

    private PendingSave pendingSave;

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

        // 打印（另存为 PDF）和 blob 下载走这个桥，必须在 loadUrl 之前挂上
        webView.addJavascriptInterface(new ThonlyBridge(), BRIDGE_NAME);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // 用户点新链接时，上一次返回的快照标记不能留到这次导航
                backNavigation = false;
                servedFromCacheUrl = null;
                return handleUri(request.getUrl());
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (!backNavigation || request == null || !request.isForMainFrame()
                        || !"GET".equalsIgnoreCase(request.getMethod())) {
                    return null;
                }
                Uri uri = request.getUrl();
                if (!AppUrls.isInternal(uri)) {
                    return null;
                }
                String html = pageCache.get(uri.toString());
                if (html == null) {
                    return null;
                }
                backNavigation = false;
                servedFromCacheUrl = uri.toString();
                byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
                WebResourceResponse response = new WebResourceResponse(
                        "text/html", "UTF-8", new ByteArrayInputStream(bytes));
                response.setStatusCodeAndReasonPhrase(200, "OK");
                return response;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (pageFullscreen) {
                    // 新文档不会保留上个页面的全屏 class，别把工具栏一直藏着
                    applyPageFullscreen(false);
                }
                errorView.setVisibility(View.GONE);
            }

            /** 页面开始可见时先注入一次，尽量赶在第一次绘制前把桥和样式装好。 */
            @Override
            public void onPageCommitVisible(WebView view, String url) {
                view.evaluateJavascript(appChromeScript(), null);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                backNavigation = false;
                boolean fromCache = url != null && url.equals(servedFromCacheUrl);
                servedFromCacheUrl = null;
                // 页面渲染完再补一次，防止站点自己的脚本重写 head 时把样式弄丢。
                view.evaluateJavascript(appChromeScript(), null);
                if (!fromCache) {
                    // 稍等一下再抓 DOM，确保样式和首屏脚本都已经落位；返回时直接用这份快照。
                    view.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            cacheCurrentPage(view, url);
                        }
                    }, 500);
                }
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

            /**
             * 网页里的 &lt;input type="file"&gt;（上传文件、上传头像、编辑器插图、批量上传等）
             * 全部走这里。没有这个回调时 WebView 点击文件框不会有任何反应。
             */
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                // 上一个请求还没结束就先取消，避免 WebView 一直等
                finishFileChooser(null);
                fileChooserCallback = callback;

                Intent intent;
                try {
                    intent = params != null
                            ? params.createIntent()
                            : new Intent(Intent.ACTION_GET_CONTENT);
                } catch (Exception e) {
                    AppLog.e("web", "创建文件选择器失败，退回通用选择器", e);
                    intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    if (params != null
                            && params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    }
                }
                try {
                    activity.startActivityForResult(intent, REQUEST_FILE_CHOOSER);
                    return true;
                } catch (ActivityNotFoundException e) {
                    AppLog.e("web", "没有可用的文件选择器", e);
                    finishFileChooser(null);
                    return false;
                } catch (Exception e) {
                    AppLog.e("web", "打开文件选择器失败", e);
                    finishFileChooser(null);
                    return false;
                }
            }

            /** 网页视频 / 图片查看器的全屏请求：盖到整个窗口上，连 App 工具栏一起遮掉。 */
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreenView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                if (pageFullscreen) {
                    applyPageFullscreen(false);
                }
                ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
                ViewParent parent = view.getParent();
                if (parent instanceof ViewGroup) {
                    ((ViewGroup) parent).removeView(view);
                }
                fullscreenView = view;
                fullscreenCallback = callback;
                fullscreenDecor = decor;
                previousSystemUiVisibility = decor.getSystemUiVisibility();
                previousFullscreenFlag =
                        (activity.getWindow().getAttributes().flags
                                & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0;
                decor.addView(view, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                decor.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }

            @Override
            public void onHideCustomView() {
                hideCustomView();
            }
        });
    }

    /** 按返回键时先处理全屏；返回 true 表示这次按键已经消费。 */
    public boolean onBackPressed() {
        if (fullscreenView != null) {
            hideCustomView();
            return true;
        }
        if (pageFullscreen) {
            // 图片查看器的「全屏」是页面内的浮层：先点它的关闭按钮；
            // 视频的 CSS 全屏才走 document.exitFullscreen()。
            webView.evaluateJavascript(
                    "(function(){var c=document.querySelector('.wiki-image-viewer-close');"
                            + "if(c){c.click();return;}"
                            + "try{document.exitFullscreen();}catch(e){}})();", null);
            return true;
        }
        return false;
    }

    private void hideCustomView() {
        View view = fullscreenView;
        WebChromeClient.CustomViewCallback callback = fullscreenCallback;
        ViewGroup decor = fullscreenDecor;
        fullscreenView = null;
        fullscreenCallback = null;
        fullscreenDecor = null;

        if (view != null) {
            ViewParent parent = view.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(view);
            }
        }
        if (decor != null) {
            decor.setSystemUiVisibility(previousSystemUiVisibility);
        }
        if (!previousFullscreenFlag) {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        if (callback != null) {
            callback.onCustomViewHidden();
        }
    }

    /**
     * 系统文件选择器返回。返回 true 表示这次结果属于文件选择，已经交给 WebView。
     */
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_FILE_CHOOSER) {
            return false;
        }
        if (fileChooserCallback == null) {
            return true;
        }
        Uri[] result = null;
        if (resultCode == Activity.RESULT_OK) {
            result = parseFileChooserResult(data);
        }
        finishFileChooser(result);
        return true;
    }

    /** 写下载目录的权限回来（只有 Android 10 以下会走这里）。 */
    public boolean onRequestPermissionsResult(int requestCode, int[] grantResults) {
        if (requestCode != REQUEST_WRITE_STORAGE) {
            return false;
        }
        PendingSave pending = pendingSave;
        pendingSave = null;
        if (pending != null && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            saveBase64Now(pending.name, pending.mime, pending.base64);
        } else {
            Toast.makeText(activity, R.string.web_save_permission, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    /** 把选择结果交回 WebView，并清空回调（必须调用，否则文件框会一直卡住）。 */
    private void finishFileChooser(Uri[] result) {
        ValueCallback<Uri[]> callback = fileChooserCallback;
        fileChooserCallback = null;
        if (callback != null) {
            callback.onReceiveValue(result);
        }
    }

    /** 兼容单选与多选（多选放在 ClipData 里，单选在 data 里）。 */
    private static Uri[] parseFileChooserResult(Intent data) {
        if (data == null) {
            return null;
        }
        ClipData clip = data.getClipData();
        if (clip != null && clip.getItemCount() > 0) {
            Uri[] uris = new Uri[clip.getItemCount()];
            for (int i = 0; i < clip.getItemCount(); i++) {
                uris[i] = clip.getItemAt(i).getUri();
            }
            return uris;
        }
        Uri uri = data.getData();
        return uri == null ? null : new Uri[]{uri};
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

    // ------------------------------------------------------------ 注入样式与 JS 桥

    private String appChromeScript() {
        return "(function(){var id='thonly-app-chrome';"
                + "var s=document.getElementById(id);"
                + "if(!s){s=document.createElement('style');s.id=id;"
                + "(document.head||document.documentElement).appendChild(s);}"
                + "s.textContent=\"" + APP_CHROME_CSS + "\";"
                + bridgeScript()
                + "})();";
    }

    /**
     * 安装打印和 blob 下载桥。只在页面里装一次；重复执行时靠 __wkAppBridge 短路。
     *
     * <p>下载菜单里的「原图」是普通 http 链接，但它在图片查看器里，
     * 也一并 fetch 成 blob 再交给原生保存，避免 WebView 直接打开图片。
     */
    private String bridgeScript() {
        return "var __wkToken='" + bridgeToken + "';"
                + "if(!window.__wkAppBridge){window.__wkAppBridge=true;"
                + "window.print=function(){try{ThonlyApp.print(__wkToken);}catch(e){}};"
                + "function wkSetPageFullscreen(on){try{ThonlyApp.setPageFullscreen(__wkToken,on);}catch(e){}}"
                + "function wkFsElement(){return window.__wkFullscreenElement||null;}"
                + "function wkFsChange(){document.dispatchEvent(new Event('fullscreenchange'));}"
                + "function wkFsExit(){var el=wkFsElement();if(!el)return Promise.resolve();"
                + "el.classList.remove('thonly-app-fullscreen');window.__wkFullscreenElement=null;"
                + "wkSetPageFullscreen(false);wkFsChange();return Promise.resolve();}"
                + "function wkSyncImageFullscreen(){"
                + "var open=document.documentElement&&"
                + "document.documentElement.classList.contains('wiki-image-viewer-open');"
                + "if(open)wkSetPageFullscreen(true);"
                + "else if(!document.fullscreenElement)wkSetPageFullscreen(false);}"
                + "try{if(window.MutationObserver&&document.documentElement){"
                + "new MutationObserver(wkSyncImageFullscreen).observe(document.documentElement,"
                + "{attributes:true,attributeFilter:['class']});"
                + "wkSyncImageFullscreen();}}catch(e){}"
                + "if(!document.fullscreenEnabled){"
                + "try{Object.defineProperty(document,'fullscreenEnabled',{configurable:true,"
                + "get:function(){return true;}});}catch(e){}"
                + "try{Object.defineProperty(document,'fullscreenElement',{configurable:true,"
                + "get:wkFsElement});}catch(e){}"
                + "try{Element.prototype.requestFullscreen=function(){"
                + "var el=this;if(wkFsElement()&&wkFsElement()!==el)wkFsExit();"
                + "window.__wkFullscreenElement=el;el.classList.add('thonly-app-fullscreen');"
                + "var st=document.getElementById('thonly-app-fullscreen');"
                + "if(!st){st=document.createElement('style');st.id='thonly-app-fullscreen';"
                + "st.textContent='.thonly-app-fullscreen{position:fixed!important;inset:0!important;"
                + "width:100%!important;height:100%!important;max-width:none!important;"
                + "max-height:none!important;margin:0!important;z-index:2147483647!important;"
                + "border-radius:0!important;background:#000!important;}"
                + ".thonly-app-fullscreen .wiki-media-element{width:100%!important;"
                + "height:100%!important;max-height:none!important;object-fit:contain!important;}';"
                + "(document.head||document.documentElement).appendChild(st);}"
                + "wkSetPageFullscreen(true);wkFsChange();return Promise.resolve();};}catch(e){}"
                + "try{Document.prototype.exitFullscreen=wkFsExit;}catch(e){"
                + "try{document.exitFullscreen=wkFsExit;}catch(e2){}}"
                + "}"
                + "document.addEventListener('click',function(e){"
                + "var a=e.target&&e.target.closest?e.target.closest('a[download]'):null;"
                + "if(!a)return;"
                + "var href=a.href||'';"
                + "var isBlob=/^(blob:|data:)/i.test(href);"
                + "var isViewerImage=/^https?:/i.test(href)&&a.closest&&a.closest('.wiki-image-viewer');"
                + "if(!isBlob&&!isViewerImage)return;"
                + "e.preventDefault();e.stopPropagation();"
                + "fetch(href,{credentials:'same-origin'}).then(function(r){"
                + "if(!r.ok)throw new Error('http '+r.status);return r.blob();"
                + "}).then(function(b){"
                + "if(b.size>" + MAX_SAVE_BYTES + ")return;"
                + "var reader=new FileReader();"
                + "reader.onloadend=function(){"
                + "var d=String(reader.result||'');var i=d.indexOf(',');"
                + "var name=a.getAttribute('download')||a.download||'download';"
                + "try{ThonlyApp.saveBase64(__wkToken,name,b.type||'',"
                + "i>=0?d.substring(i+1):d);}catch(err){}"
                + "};"
                + "reader.readAsDataURL(b);"
                + "}).catch(function(){});"
                + "},true);"
                + "}";
    }

    /**
     * 把当前页面的 DOM 存一份。只在 URL 仍然是这个页面时抓，
     * 避免用户已经跳到下一页时把新页面错误地记到上一页名下。
     */
    private void cacheCurrentPage(final WebView view, final String url) {
        if (url == null || url.isEmpty() || !url.equals(view.getUrl())
                || !AppUrls.isInternal(Uri.parse(url))) {
            return;
        }
        view.evaluateJavascript(
                "(function(){try{return document.documentElement.outerHTML;}"
                        + "catch(e){return '';}})()",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        if (value == null || value.length() < 3 || !url.equals(view.getUrl())) {
                            return;
                        }
                        String html;
                        try {
                            // evaluateJavascript 返回的是 JSON 字符串，先还原转义
                            html = new JSONArray("[" + value + "]").getString(0);
                        } catch (Exception e) {
                            return;
                        }
                        if (html == null || html.isEmpty()
                                || html.length() > MAX_CACHED_PAGE_CHARS) {
                            return;
                        }
                        pageCache.put(url, html);
                    }
                });
    }

    public void onShown() {
        if (!loaded) {
            loadHome();
        }
    }

    public void loadHome() {
        openUrl(AppUrls.home());
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
            absolute = ServerConfig.baseUrl() + (url.startsWith("/") ? url : "/" + url);
        }
        loaded = true;
        errorView.setVisibility(View.GONE);
        backNavigation = false;
        servedFromCacheUrl = null;
        webView.loadUrl(AppUrls.withReference(absolute));
    }

    /** 登录/注册成功后刷新，让页面带上新的登录态 */
    public void reload() {
        backNavigation = false;
        servedFromCacheUrl = null;
        // 登录 / 退出后页面内容会变，旧快照不能再拿去返回
        pageCache.clear();
        if (webView.getUrl() == null) {
            loadHome();
        } else {
            webView.reload();
        }
    }

    public boolean canGoBack() {
        return webView.canGoBack();
    }

    /**
     * 后退。不要在这里重新 loadUrl，也不要拦截主文档：
     * WebView 自带的历史缓存会直接恢复上一页，不会重新请求服务器。
     */
    public void goBack() {
        if (!webView.canGoBack()) {
            return;
        }
        backNavigation = true;
        webView.removeCallbacks(clearBackNavigation);
        webView.postDelayed(clearBackNavigation, 3000);
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
        finishFileChooser(null);
        hideCustomView();
        webView.removeJavascriptInterface(BRIDGE_NAME);
        webView.destroy();
    }

    // ------------------------------------------------------------ blob 下载 / 打印桥

    private boolean canUseActivity() {
        return !activity.isFinishing() && !activity.isDestroyed();
    }

    private boolean isTrustedPage() {
        String url = webView.getUrl();
        return url != null && AppUrls.isInternal(Uri.parse(url));
    }

    /** 页面内 CSS 全屏：隐藏 App 工具栏 / 系统栏；退出时恢复原来的状态。 */
    private void applyPageFullscreen(boolean fullscreen) {
        if (pageFullscreen == fullscreen) {
            return;
        }
        pageFullscreen = fullscreen;
        View toolbar = activity.findViewById(R.id.appToolbar);
        if (toolbar != null) {
            toolbar.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        }
        View banner = activity.findViewById(R.id.offlineBanner);
        if (banner != null) {
            if (fullscreen) {
                pageFullscreenBannerVisibility = banner.getVisibility();
                banner.setVisibility(View.GONE);
            } else {
                banner.setVisibility(pageFullscreenBannerVisibility);
            }
        }
        View decor = activity.getWindow().getDecorView();
        if (fullscreen) {
            pageFullscreenPreviousUi = decor.getSystemUiVisibility();
            pageFullscreenPreviousFlag =
                    (activity.getWindow().getAttributes().flags
                            & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0;
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        } else {
            decor.setSystemUiVisibility(pageFullscreenPreviousUi);
            if (!pageFullscreenPreviousFlag) {
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
        }
    }

    private static final class PendingSave {
        final String name;
        final String mime;
        final String base64;

        PendingSave(String name, String mime, String base64) {
            this.name = name;
            this.mime = mime;
            this.base64 = base64;
        }
    }

    private final class ThonlyBridge {

        @JavascriptInterface
        public void print(String token) {
            if (!bridgeToken.equals(token)) {
                return;
            }
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!canUseActivity() || !isTrustedPage()) {
                        return;
                    }
                    PrintManager manager = (PrintManager) activity.getSystemService(
                            Context.PRINT_SERVICE);
                    if (manager == null) {
                        Toast.makeText(activity, R.string.web_print_unavailable,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String title = webView.getTitle();
                    if (title == null || title.trim().isEmpty()) {
                        title = activity.getString(R.string.app_name);
                    } else {
                        title = title.trim();
                    }
                    PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter(title);
                    manager.print(title, adapter, new PrintAttributes.Builder().build());
                }
            });
        }

        /**
         * 自定义视频播放器在 WebView 里没有原生 Element.requestFullscreen 时，
         * 用页面 CSS 铺满 WebView；同时把 App 工具栏和状态栏收起来。
         */
        @JavascriptInterface
        public void setPageFullscreen(String token, final boolean fullscreen) {
            if (!bridgeToken.equals(token)) {
                return;
            }
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (canUseActivity()) {
                        applyPageFullscreen(fullscreen);
                    }
                }
            });
        }

        @JavascriptInterface
        public void saveBase64(final String token, final String rawName,
                               final String mime, final String base64) {
            if (!bridgeToken.equals(token) || base64 == null || base64.isEmpty()) {
                return;
            }
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!canUseActivity() || !isTrustedPage()) {
                        return;
                    }
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                            && activity.checkSelfPermission(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED) {
                        pendingSave = new PendingSave(rawName, mime, base64);
                        activity.requestPermissions(
                                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                REQUEST_WRITE_STORAGE);
                        return;
                    }
                    saveBase64Now(rawName, mime, base64);
                }
            });
        }
    }

    private void saveBase64Now(final String rawName, final String mime,
                               final String base64) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!canUseActivity()) {
                    return;
                }
                boolean success = false;
                try {
                    byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                    if (bytes.length > MAX_SAVE_BYTES) {
                        throw new IOException("文件超过 20 MB");
                    }
                    writeDownload(safeName(rawName, mime), normalizeMime(mime), bytes);
                    success = true;
                } catch (Exception e) {
                    AppLog.e("web", "保存网页下载失败", e);
                }
                final boolean ok = success;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!canUseActivity()) {
                            return;
                        }
                        Toast.makeText(activity,
                                ok ? R.string.web_save_success : R.string.web_save_failed,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }, "web-download").start();
    }

    private static String normalizeMime(String mime) {
        return mime == null || mime.isEmpty() ? "application/octet-stream" : mime;
    }

    private static String safeName(String raw, String mime) {
        String name = raw == null ? "" : raw.trim();
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        if (name.isEmpty()) {
            name = "download";
        }
        if (!name.contains(".")) {
            String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            if (extension != null && !extension.isEmpty()) {
                name = name + "." + extension;
            }
        }
        if (name.length() > 120) {
            int dot = name.lastIndexOf('.');
            if (dot > 0 && name.length() - dot <= 12) {
                name = name.substring(0, 120 - (name.length() - dot)) + name.substring(dot);
            } else {
                name = name.substring(0, 120);
            }
        }
        return name;
    }

    private void writeDownload(String name, String mime, byte[] bytes) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeToMediaStore(name, mime, bytes);
        } else {
            writeToPublicDownloads(name, mime, bytes);
        }
    }

    /** Android 10+：通过 MediaStore 写入「下载」目录，不需要存储权限。 */
    private void writeToMediaStore(String name, String mime, byte[] bytes) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, name);
        values.put(MediaStore.Downloads.MIME_TYPE, mime);
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        ContentResolver resolver = activity.getContentResolver();
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("无法创建下载文件");
        }
        try {
            OutputStream output = resolver.openOutputStream(uri);
            if (output == null) {
                throw new IOException("无法打开下载文件");
            }
            try {
                output.write(bytes);
            } finally {
                output.close();
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Downloads.IS_PENDING, 0);
            resolver.update(uri, done, null, null);
        } catch (IOException e) {
            resolver.delete(uri, null, null);
            throw e;
        }
    }

    /** Android 9 及以下：直接写公共下载目录，需要 WRITE_EXTERNAL_STORAGE。 */
    private void writeToPublicDownloads(String name, String mime, byte[] bytes)
            throws IOException {
        File directory = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("无法创建下载目录");
        }
        File target = uniqueFile(directory, name);
        FileOutputStream output = new FileOutputStream(target);
        try {
            output.write(bytes);
        } finally {
            output.close();
        }
        MediaScannerConnection.scanFile(activity,
                new String[]{target.getAbsolutePath()}, new String[]{mime}, null);
    }

    private static File uniqueFile(File directory, String name) {
        File file = new File(directory, name);
        if (!file.exists()) {
            return file;
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            file = new File(directory, base + "-" + i + extension);
            if (!file.exists()) {
                return file;
            }
        }
        return new File(directory, System.currentTimeMillis() + "-" + name);
    }
}