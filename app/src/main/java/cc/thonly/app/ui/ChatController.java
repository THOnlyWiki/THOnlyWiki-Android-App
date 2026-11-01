package cc.thonly.app.ui;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cc.thonly.app.R;
import cc.thonly.app.data.MessagesApi;
import cc.thonly.app.data.UserSession;

/**
 * 私信对话页（独立页面，不是弹窗）。
 *
 * <p>实时同步用轮询：页面可见时每 3 秒拉一次 thread 接口，
 * 只在内容真变了才刷列表，避免闪动和多余重绘。
 * 页面不可见就停（{@link #onHidden()}），不浪费流量和电。
 */
public final class ChatController {

    private static final long POLL_INTERVAL_MS = 3000L;

    private final Activity activity;
    private final TextView title;
    private final TextView status;
    private final ListView listView;
    private final TextView emptyView;
    private final EditText input;
    private final TextView sendButton;
    private final ChatAdapter adapter;
    private final List<MessagesApi.Chat> items = new ArrayList<MessagesApi.Chat>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String peer = "";
    private boolean active;
    private boolean loading;
    private long lastSignature;

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            if (!active || peer.isEmpty()) {
                return;
            }
            load(false);
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    public ChatController(final Activity activity, View root, final Runnable onBack) {
        this.activity = activity;
        this.title = root.findViewById(R.id.chatTitle);
        this.status = root.findViewById(R.id.chatStatus);
        this.listView = root.findViewById(R.id.chatList);
        this.emptyView = root.findViewById(R.id.chatEmpty);
        this.input = root.findViewById(R.id.chatInput);
        this.sendButton = root.findViewById(R.id.chatSend);

        this.adapter = new ChatAdapter();
        listView.setAdapter(adapter);

        listView.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(android.widget.AdapterView<?> parent, View view,
                                           int position, long id) {
                confirmRecall(items.get(position));
                return true;
            }
        });
        root.findViewById(R.id.chatBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBack.run();
            }
        });
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send();
            }
        });
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    send();
                    return true;
                }
                return false;
            }
        });
    }

    /** 进入某个对话 */
    public void open(String peer) {
        this.peer = peer;
        title.setText(peer);
        items.clear();
        adapter.notifyDataSetChanged();
        emptyView.setVisibility(View.VISIBLE);
        status.setText("");
        input.setText("");
        load(true);
    }

    /** 页面可见：开始轮询 */
    public void onShown() {
        active = true;
        handler.removeCallbacks(pollTask);
        if (!peer.isEmpty()) {
            load(false);
            handler.postDelayed(pollTask, POLL_INTERVAL_MS);
        }
    }

    /** 页面不可见：停轮询 */
    public void onHidden() {
        active = false;
        handler.removeCallbacks(pollTask);
    }

    private void load(final boolean scrollToBottom) {
        if (loading || peer.isEmpty()) {
            return;
        }
        loading = true;
        final String target = peer;
        new Thread(new Runnable() {
            @Override
            public void run() {
                MessagesApi.Result result = null;
                try {
                    result = MessagesApi.thread(activity, target);
                } catch (IOException ignored) {
                    // 网络抖动：这一轮跳过，下一轮再试
                } catch (RuntimeException ignored) {
                    // 解析异常等也别让线程带着 loading=true 死掉，否则轮询从此停摆
                }
                final MessagesApi.Result loaded = result;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loading = false;
                        if (!target.equals(peer) || loaded == null) {
                            return;
                        }
                        if (!loaded.success) {
                            status.setText(loaded.message);
                            return;
                        }
                        status.setText("");
                        apply(MessagesApi.chatItems(loaded), scrollToBottom);
                    }
                });
            }
        }, "chat-load").start();
    }

    /** 只有内容真的变了才重绘 */
    private void apply(List<MessagesApi.Chat> loaded, boolean scrollToBottom) {
        long signature = 0L;
        for (MessagesApi.Chat chat : loaded) {
            // 只算 id 的话，对方撤回（id 不变、内容变空）会被当成"没变化"而不刷新
            signature = signature * 31 + chat.id;
            signature = signature * 31 + (chat.recalled ? 1 : 0);
            signature = signature * 31 + chat.text.length();
        }
        if (signature == lastSignature && !scrollToBottom) {
            return;
        }
        lastSignature = signature;

        items.clear();
        items.addAll(loaded);
        adapter.notifyDataSetChanged();
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        if (!items.isEmpty()) {
            listView.setSelection(items.size() - 1);
        }
    }

    private void send() {
        final String text = input.getText().toString().trim();
        if (text.isEmpty() || peer.isEmpty()) {
            return;
        }
        sendButton.setEnabled(false);
        sendButton.setAlpha(0.5f);
        final String target = peer;
        new Thread(new Runnable() {
            @Override
            public void run() {
                MessagesApi.Result result = null;
                try {
                    result = MessagesApi.send(activity, target, text);
                } catch (IOException ignored) {
                    // 下面统一提示
                }
                final MessagesApi.Result sent = result;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        sendButton.setEnabled(true);
                        sendButton.setAlpha(1f);
                        if (sent == null || !sent.success) {
                            toast(sent == null || sent.message.isEmpty()
                                    ? activity.getString(R.string.messages_send_failed)
                                    : sent.message);
                            return;
                        }
                        input.setText("");
                        lastSignature = 0L;   // 强制下一轮刷新
                        load(true);
                    }
                });
            }
        }, "chat-send").start();
    }

    /** 长按自己 2 分钟内的消息 → 确认撤回 */
    private void confirmRecall(final MessagesApi.Chat chat) {
        if (chat.incoming || chat.recalled) {
            return;
        }
        if (!MessagesApi.canRecall(chat.raw)) {
            toast(activity.getString(R.string.chat_recall_expired));
            return;
        }
        new android.app.AlertDialog.Builder(activity)
                .setMessage(R.string.chat_recall_confirm)
                .setPositiveButton(R.string.chat_recall, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        doRecall(chat.id);
                    }
                })
                .setNegativeButton(R.string.edit_cancel, null)
                .show();
    }

    private void doRecall(final long messageId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                MessagesApi.Result result = null;
                try {
                    result = MessagesApi.recall(activity, messageId);
                } catch (IOException ignored) {
                    // 下面统一提示
                }
                final MessagesApi.Result done = result;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (done == null || !done.success) {
                            toast(done == null || done.message.isEmpty()
                                    ? activity.getString(R.string.messages_send_failed)
                                    : done.message);
                            return;
                        }
                        lastSignature = 0L;   // 强制刷新
                        load(true);
                    }
                });
            }
        }, "chat-recall").start();
    }

    private int dp(int value) {
        return Math.round(activity.getResources().getDisplayMetrics().density * value);
    }

    private void toast(String text) {
        if (text != null && !text.isEmpty()) {
            Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
        }
    }

    private final class ChatAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(activity)
                        .inflate(R.layout.item_chat, parent, false);
            }
            MessagesApi.Chat chat = items.get(position);

            TextView bubble = view.findViewById(R.id.chatBubble);
            LinearLayout row = (LinearLayout) view;

            if (chat.recalled) {
                // 撤回后不留气泡，只留一行居中的灰字
                bubble.setText(chat.incoming ? R.string.chat_recalled_peer : R.string.chat_recalled_me);
                bubble.setBackground(null);
                bubble.setTextColor(activity.getColor(R.color.text_tertiary));
                bubble.setPadding(0, dp(2), 0, dp(2));
                row.setGravity(Gravity.CENTER_HORIZONTAL);
            } else {
                bubble.setText(chat.text);
                bubble.setBackgroundResource(chat.incoming
                        ? R.drawable.bg_bubble_in : R.drawable.bg_bubble_out);
                bubble.setTextColor(activity.getColor(chat.incoming
                        ? R.color.text_primary : android.R.color.white));
                bubble.setPadding(dp(13), dp(9), dp(13), dp(9));
                // 气泡靠哪边：整行是一个 LinearLayout，改它的 gravity
                row.setGravity(chat.incoming ? Gravity.START : Gravity.END);
            }

            // 时间：只有间隔超过 10 分钟才显示一条，不刷屏
            TextView time = view.findViewById(R.id.chatTime);
            String previous = position > 0 ? items.get(position - 1).raw : null;
            if (MessagesApi.needsTime(previous, chat.raw)) {
                time.setText(MessagesApi.displayTime(chat.raw));
                time.setVisibility(View.VISIBLE);
            } else {
                time.setVisibility(View.GONE);
            }
            return view;
        }
    }
}
