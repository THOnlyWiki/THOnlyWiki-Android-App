package cc.thonly.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cc.thonly.app.R;
import cc.thonly.app.data.MusicCache;
import cc.thonly.app.data.Playlist;

/**
 * 东方原曲播放页。
 *
 * <p>两级列表：专辑 → 曲目。底部一条轻量播放条（进度 + 播放/暂停），
 * 单曲播完自动接同专辑的下一首。
 *
 * <p>曲目右侧的 ↓ 可以缓存到本地，缓存过的直接播文件；
 * 没缓存的走流式播放。源站在 Cloudflare 后面，被拦时提供「在浏览器打开」。
 */
public final class MusicController {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final String REFERER = "https://thbwiki.cc/";
    /** U+23F8 会被渲染成 emoji，用数学符号的双竖线更稳 */
    private static final String PAUSE_GLYPH = "∥";

    private final Activity activity;
    private final TextView crumb;
    private final ListView listView;
    private final View playerBar;
    private final TextView playerTitle;
    private final TextView playButton;
    private final SeekBar seekBar;

    private final MusicCache cache;
    private final List<Playlist.Album> albums;
    private final RowAdapter adapter;

    private Playlist.Album currentAlbum;
    private MediaPlayer player;
    private Playlist.Track currentTrack;
    private boolean prepared;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable progressTask = new Runnable() {
        @Override
        public void run() {
            if (player == null || !prepared) {
                return;
            }
            try {
                seekBar.setMax(player.getDuration());
                seekBar.setProgress(player.getCurrentPosition());
            } catch (IllegalStateException ignored) {
                // 播放器刚释放，忽略
            }
            handler.postDelayed(this, 500);
        }
    };

    public MusicController(final Activity activity, View root) {
        this.activity = activity;
        this.cache = new MusicCache(activity);
        this.albums = Playlist.all(activity);

        this.crumb = root.findViewById(R.id.musicCrumb);
        this.listView = root.findViewById(R.id.musicList);
        this.playerBar = root.findViewById(R.id.playerBar);
        this.playerTitle = root.findViewById(R.id.playerTitle);
        this.playButton = root.findViewById(R.id.playerPlay);
        this.seekBar = root.findViewById(R.id.playerSeek);

        this.adapter = new RowAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (currentAlbum == null) {
                    showTracks(albums.get(position));
                } else {
                    play(currentAlbum.tracks().get(position));
                }
            }
        });

        crumb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentAlbum != null) {
                    showAlbums();
                }
            }
        });

        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlay();
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && player != null && prepared) {
                    player.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });

        showAlbums();
    }

    // ------------------------------------------------------------ 列表

    private void showAlbums() {
        currentAlbum = null;
        int total = 0;
        for (Playlist.Album album : albums) {
            total += album.tracks().size();
        }
        crumb.setText(activity.getString(R.string.music_album_summary, albums.size(), total));
        adapter.notifyDataSetChanged();
    }

    private void showTracks(Playlist.Album album) {
        currentAlbum = album;
        crumb.setText(activity.getString(R.string.music_back_to_albums, album.name()));
        adapter.notifyDataSetChanged();
        listView.setSelection(0);
    }

    private final class RowAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            if (currentAlbum != null) {
                return currentAlbum.tracks().size();
            }
            return albums.size();
        }

        @Override
        public Object getItem(int position) {
            return position;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(activity)
                        .inflate(R.layout.item_music_row, parent, false);
            }
            TextView title = view.findViewById(R.id.rowTitle);
            TextView sub = view.findViewById(R.id.rowSub);
            TextView action = view.findViewById(R.id.rowAction);

            if (currentAlbum == null) {
                Playlist.Album album = albums.get(position);
                title.setText(album.name());
                sub.setText(activity.getString(R.string.music_track_count, album.tracks().size()));
                action.setText("›");
                action.setClickable(false);
                action.setOnClickListener(null);
                sub.setVisibility(View.VISIBLE);
                return view;
            }

            final Playlist.Track track = currentAlbum.tracks().get(position);
            title.setText(track.name());

            // 副标题拼「版本 · 状态」，两者都没有就整行隐藏
            StringBuilder subText = new StringBuilder();
            if (!track.variant().isEmpty()) {
                subText.append(track.variant());
            }
            if (track == currentTrack) {
                appendSub(subText, activity.getString(R.string.music_playing));
            } else if (cache.isCached(track.url())) {
                appendSub(subText, activity.getString(R.string.music_cached));
            }
            if (subText.length() == 0) {
                sub.setVisibility(View.GONE);
            } else {
                sub.setText(subText);
                sub.setVisibility(View.VISIBLE);
            }

            action.setText(cache.isCached(track.url()) ? "✓" : "↓");
            action.setClickable(true);
            action.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cacheTrack(track);
                }
            });
            return view;
        }
    }

    private static void appendSub(StringBuilder builder, String text) {
        if (builder.length() > 0) {
            builder.append(" · ");
        }
        builder.append(text);
    }

    // ------------------------------------------------------------ 播放

    private void play(final Playlist.Track track) {
        releasePlayer();
        currentTrack = track;
        prepared = false;
        playerTitle.setText(track.name());
        playerBar.setVisibility(View.VISIBLE);
        playButton.setText(PAUSE_GLYPH);
        seekBar.setProgress(0);
        adapter.notifyDataSetChanged();

        player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());
        player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                prepared = true;
                mp.start();
                playButton.setText(PAUSE_GLYPH);
                handler.post(progressTask);
            }
        });
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                playNext();
            }
        });
        player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                onPlaybackError();
                return true;
            }
        });

        try {
            if (cache.isCached(track.url())) {
                player.setDataSource(cache.fileFor(track.url()).getAbsolutePath());
            } else {
                Map<String, String> headers = new HashMap<String, String>();
                headers.put("User-Agent", USER_AGENT);
                headers.put("Referer", REFERER);
                player.setDataSource(activity, Uri.parse(track.url()), headers);
            }
            player.prepareAsync();
        } catch (Exception e) {
            onPlaybackError();
        }
    }

    private void togglePlay() {
        if (player == null || !prepared) {
            return;
        }
        try {
            if (player.isPlaying()) {
                player.pause();
                playButton.setText("▶");
            } else {
                player.start();
                playButton.setText(PAUSE_GLYPH);
            }
        } catch (IllegalStateException ignored) {
            // 状态竞争，忽略
        }
    }

    /** 播完接同专辑下一首 */
    private void playNext() {
        if (currentAlbum == null || currentTrack == null) {
            return;
        }
        List<Playlist.Track> tracks = currentAlbum.tracks();
        int index = tracks.indexOf(currentTrack);
        if (index >= 0 && index + 1 < tracks.size()) {
            play(tracks.get(index + 1));
        } else {
            releasePlayer();
            playButton.setText("▶");
            adapter.notifyDataSetChanged();
        }
    }

    private void onPlaybackError() {
        releasePlayer();
        playButton.setText("▶");
        new AlertDialog.Builder(activity)
                .setTitle(R.string.music_play_failed_title)
                .setMessage(R.string.music_play_failed_message)
                .setPositiveButton(R.string.music_open_browser,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                openInBrowser();
                            }
                        })
                .setNegativeButton(R.string.edit_cancel, null)
                .show();
    }

    private void openInBrowser() {
        if (currentTrack == null) {
            return;
        }
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(currentTrack.url())));
        } catch (Exception e) {
            Toast.makeText(activity, R.string.music_no_browser, Toast.LENGTH_SHORT).show();
        }
    }

    private void releasePlayer() {
        handler.removeCallbacks(progressTask);
        prepared = false;
        if (player != null) {
            try {
                player.reset();
                player.release();
            } catch (Exception ignored) {
                // 释放失败无所谓
            }
            player = null;
        }
    }

    // ------------------------------------------------------------ 缓存

    private void cacheTrack(final Playlist.Track track) {
        if (cache.isCached(track.url())) {
            Toast.makeText(activity, R.string.music_cached, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(activity, R.string.music_cache_start, Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    cache.download(track.url());
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            adapter.notifyDataSetChanged();
                            Toast.makeText(activity, R.string.music_cache_done,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (final IOException e) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(activity, R.string.music_cache_failed,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }, "music-cache").start();
    }

    public void onDestroy() {
        releasePlayer();
    }
}
