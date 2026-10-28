package cc.thonly.app.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cc.thonly.app.util.AppLog;

/**
 * 东方原曲曲库：40 张专辑、944 首，音频放在 thbwiki 的 upload CDN 上。
 *
 * <p>数据来自 HorainingyouBot 的 thplaylist.json，打包进 assets，
 * 不联网也能看目录。
 */
public final class Playlist {

    private static final String ASSET_NAME = "thplaylist.json";

    private static List<Album> cache;

    private Playlist() {
    }

    public static final class Track {

        private final String name;
        private final String url;

        Track(String name, String url) {
            this.name = name;
            this.url = url;
        }

        public String name() {
            return name;
        }

        public String url() {
            return url;
        }

        /**
         * 同一首曲子往往有原版和 SC-88Pro 两个文件，名字一样，得区分开。
         */
        public String variant() {
            String file = url.substring(url.lastIndexOf('/') + 1).toLowerCase(java.util.Locale.US);
            if (file.contains("sc88pro")) {
                return "SC-88Pro";
            }
            if (file.contains("_arr") || file.contains("arrange")) {
                return "改编";
            }
            return "";
        }
    }

    public static final class Album {

        private final String name;
        private final List<Track> tracks;

        Album(String name, List<Track> tracks) {
            this.name = name;
            this.tracks = Collections.unmodifiableList(tracks);
        }

        public String name() {
            return name;
        }

        public List<Track> tracks() {
            return tracks;
        }
    }

    public static List<Album> all(Context context) {
        if (cache != null) {
            return cache;
        }
        List<Album> albums = new ArrayList<Album>();
        try {
            JSONObject root = new JSONObject(readAsset(context));
            JSONArray data = root.optJSONArray("data");
            if (data != null) {
                for (int i = 0; i < data.length(); i++) {
                    JSONObject albumJson = data.optJSONObject(i);
                    if (albumJson == null) {
                        continue;
                    }
                    JSONArray list = albumJson.optJSONArray("playlist");
                    if (list == null || list.length() == 0) {
                        continue;
                    }
                    List<Track> tracks = new ArrayList<Track>();
                    for (int j = 0; j < list.length(); j++) {
                        JSONObject trackJson = list.optJSONObject(j);
                        if (trackJson == null) {
                            continue;
                        }
                        String url = trackJson.optString("path", "");
                        if (url.isEmpty()) {
                            continue;
                        }
                        tracks.add(new Track(trackJson.optString("name", ""), url));
                    }
                    if (!tracks.isEmpty()) {
                        albums.add(new Album(albumJson.optString("name", ""), tracks));
                    }
                }
            }
        } catch (Exception e) {
            AppLog.e("music", "读取曲库失败", e);
        }
        cache = albums;
        return cache;
    }

    private static String readAsset(Context context) throws IOException {
        InputStream input = context.getAssets().open(ASSET_NAME);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int count;
        while ((count = input.read(chunk)) != -1) {
            buffer.write(chunk, 0, count);
        }
        input.close();
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }
}
