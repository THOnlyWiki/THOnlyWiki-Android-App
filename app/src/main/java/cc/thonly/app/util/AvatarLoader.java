package cc.thonly.app.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cc.thonly.app.AppConfig;
import cc.thonly.app.data.ServerConfig;

/**
 * 头像加载：内存缓存 + 后台线程 + 圆形裁剪。
 *
 * <p>项目不引第三方图片库（Glide/Picasso 都要几百 KB），这里手写一个够用的：
 * <ul>
 *     <li>LruCache 缓存已解码的圆形位图，列表滚动不重复请求</li>
 *     <li>固定 2 个线程池，避免快速滑动时开一堆线程</li>
 *     <li>没有头像 / 加载失败 → 用名字首字画一个彩色圆，不留空白</li>
 * </ul>
 *
 * <p>服务端给没设过头像的用户返回 {@code /img/avatar-default.svg}，
 * Android 的 BitmapFactory 解不了 SVG，所以遇到 svg 一律走首字头像。
 */
public final class AvatarLoader {

    private static final int MAX_CACHE_BYTES = 4 * 1024 * 1024;

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** 首字头像的底色（按名字 hash 取一个，同一个人永远同色） */
    private static final int[] COLORS = {
            0xFF5B8FF9, 0xFF61DDAA, 0xFF65789B, 0xFFF6BD16,
            0xFF7262FD, 0xFF78D3F8, 0xFF9661BC, 0xFFF6903D
    };

    private AvatarLoader() {
    }

    /**
     * 把头像塞进 ImageView。
     *
     * @param path         站内相对路径（如 {@code /avatars/xx.png}）；空 / svg 走首字头像
     * @param fallbackName 没有头像时用来画首字的用户名
     * @param sizePx       显示尺寸（像素）
     */
    public static void load(final ImageView view, final String path, final String fallbackName,
                            final int sizePx) {
        final String key = path == null ? "" : path;
        view.setTag(key);

        Bitmap cached = CACHE.get(key + "@" + sizePx);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        if (key.isEmpty() || key.endsWith(".svg")) {
            Bitmap letter = letterBitmap(fallbackName, sizePx);
            CACHE.put(key + "@" + sizePx, letter);
            view.setImageBitmap(letter);
            return;
        }

        view.setImageBitmap(letterBitmap(fallbackName, sizePx));
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = fetch(key, sizePx);
                if (bitmap == null) {
                    return;
                }
                CACHE.put(key + "@" + sizePx, bitmap);
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (key.equals(view.getTag())) {
                            view.setImageBitmap(bitmap);
                        }
                    }
                });
            }
        });
    }

    /** 下载 + 解码 + 圆形裁剪；失败返回 null（界面保持首字头像） */
    private static Bitmap fetch(String path, int sizePx) {
        HttpURLConnection connection = null;
        try {
            String url = path.startsWith("http") ? path : ServerConfig.baseUrl() + path;
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(AppConfig.CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(AppConfig.READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", AppConfig.USER_AGENT);
            if (connection.getResponseCode() / 100 != 2) {
                return null;
            }
            Bitmap raw;
            try (InputStream stream = connection.getInputStream()) {
                raw = BitmapFactory.decodeStream(stream);
            }
            return raw == null ? null : circle(raw, sizePx);
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** 缩放到目标尺寸后裁成圆 */
    private static Bitmap circle(Bitmap source, int sizePx) {
        Bitmap scaled = Bitmap.createScaledBitmap(source, sizePx, sizePx, true);
        Bitmap output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        float radius = sizePx / 2f;
        canvas.drawCircle(radius, radius, radius, paint);
        if (scaled != source) {
            scaled.recycle();
        }
        return output;
    }

    /** 首字头像：纯色圆 + 白色首字 */
    private static Bitmap letterBitmap(String name, int sizePx) {
        Bitmap output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        String text = (name == null || name.isEmpty()) ? "?" : name.substring(0, 1);

        Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        background.setColor(COLORS[Math.abs((name == null ? "" : name).hashCode()) % COLORS.length]);
        float radius = sizePx / 2f;
        canvas.drawCircle(radius, radius, radius, background);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTextSize(sizePx * 0.45f);
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = radius - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(text, radius, baseline, paint);
        return output;
    }
}
