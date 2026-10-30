package cc.thonly.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cc.thonly.app.R;
import cc.thonly.app.data.MessagesApi;
import cc.thonly.app.data.UserSession;

/**
 * 消息中心：通知（Echo）+ 私信（SocialProfile）。
 *
 * <p>两个 tab 共用一个 ListView，只是数据源不同。
 * 点通知 = 标为已读；点会话 = 打开对话弹窗（打开即把对方发来的未读标已读）。
 */
public final class MessagesController {

    private static final int MODE_NOTIFY = 0;
    private static final int MODE_CHAT = 1;

    private final Activity activity;
    private final TextView tabNotify;
    private final TextView tabChat;
    private final ListView listView;
    private final TextView emptyView;
    private final MessageAdapter adapter;
    private final List<MessagesApi.Item> items = new ArrayList<MessagesApi.Item>();

    private int mode = MODE_NOTIFY;

    public MessagesController(final Activity activity, View root) {
        this.activity = activity;
        this.tabNotify = root.findViewById(R.id.tabNotify);
        this.tabChat = root.findViewById(R.id.tabChat);
        this.listView = root.findViewById(R.id.messageList);
        this.emptyView = root.findViewById(R.id.messageEmpty);

        this.adapter = new MessageAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position,
                                    long id) {
                MessagesApi.Item item = items.get(position);
                if (mode == MODE_NOTIFY) {
                    markRead(item);
                } else {
                    openThread(item.peer);
                }
            }
        });

        tabNotify.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchMode(MODE_NOTIFY);
            }
        });
        tabChat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchMode(MODE_CHAT);
            }
        });
    }

    /** 每次进入页面都重拉 */
    public void onShown() {
        reload();
    }

    private void switchMode(int target) {
        if (mode == target) {
            return;
        }
        mode = target;
        tabNotify.setTextColor(activity.getColor(
                mode == MODE_NOTIFY ? R.color.accent : R.color.text_secondary));
        tabChat.setTextColor(activity.getColor(
                mode == MODE_CHAT ? R.color.accent : R.color.text_secondary));
        reload();
    }

    private void reload() {
        if (!new UserSession(activity).isLoggedIn()) {
            items.clear();
            adapter.notifyDataSetChanged();
            emptyView.setText(R.string.messages_login_required);
            emptyView.setVisibility(View.VISIBLE);
            return;
        }
        final int target = mode;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final MessagesApi.Result result = target == MODE_NOTIFY
                            ? MessagesApi.notifications(activity)
                            : MessagesApi.conversations(activity);
                    final List<MessagesApi.Item> loaded = target == MODE_NOTIFY
                            ? MessagesApi.notificationItems(result)
                            : MessagesApi.conversationItems(result);
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!result.success) {
                                items.clear();
                                adapter.notifyDataSetChanged();
                                emptyView.setText(result.message.isEmpty()
                                        ? activity.getString(R.string.messages_load_failed)
                                        : result.message);
                                emptyView.setVisibility(View.VISIBLE);
                                return;
                            }
                            items.clear();
                            items.addAll(loaded);
                            adapter.notifyDataSetChanged();
                            emptyView.setText(target == MODE_NOTIFY
                                    ? R.string.messages_empty_notify : R.string.messages_empty_chat);
                            emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                        }
                    });
                } catch (IOException e) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            items.clear();
                            adapter.notifyDataSetChanged();
                            emptyView.setText(R.string.messages_load_failed);
                            emptyView.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }
        }, "messages-load").start();
    }

    private void markRead(MessagesApi.Item item) {
        if (!item.unread) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    MessagesApi.markNotificationRead(activity, item.id);
                } catch (IOException ignored) {
                    // 标已读失败无所谓，下次刷新还会显示未读
                }
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        reload();
                    }
                });
            }
        }, "messages-read").start();
    }

    // ------------------------------------------------------------ 对话

    private void openThread(final String peer) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final MessagesApi.Result result = MessagesApi.thread(activity, peer);
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!result.success) {
                                toast(result.message);
                                return;
                            }
                            showThread(peer, result);
                        }
                    });
                } catch (IOException e) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            toast(activity.getString(R.string.messages_load_failed));
                        }
                    });
                }
            }
        }, "messages-thread").start();
    }

    private void showThread(final String peer, MessagesApi.Result result) {
        String me = new UserSession(activity).username();
        String transcript = MessagesApi.transcript(result, me);

        ScrollView scroll = new ScrollView(activity);
        TextView text = new TextView(activity);
        text.setText(transcript.isEmpty()
                ? activity.getString(R.string.messages_empty_chat) : transcript);
        text.setTextSize(13);
        text.setLineSpacing(6f, 1f);
        text.setPadding(dp(18), dp(12), dp(18), dp(12));
        scroll.addView(text);

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(320));
        container.addView(scroll, scrollParams);

        final EditText input = new EditText(activity);
        input.setHint(R.string.messages_reply_hint);
        input.setMinLines(1);
        input.setMaxLines(3);
        container.addView(input);

        new AlertDialog.Builder(activity)
                .setTitle(peer)
                .setView(container)
                .setPositiveButton(R.string.messages_send, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String content = input.getText().toString().trim();
                        if (content.isEmpty()) {
                            return;
                        }
                        send(peer, content);
                    }
                })
                .setNegativeButton(R.string.dialog_close, null)
                .show();
    }

    private void send(final String peer, final String content) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final MessagesApi.Result result = MessagesApi.send(activity, peer, content);
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            toast(result.message);
                            reload();
                        }
                    });
                } catch (IOException e) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            toast(activity.getString(R.string.messages_send_failed));
                        }
                    });
                }
            }
        }, "messages-send").start();
    }

    private void toast(String text) {
        if (text != null && !text.isEmpty()) {
            Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int value) {
        return Math.round(activity.getResources().getDisplayMetrics().density * value);
    }

    private final class MessageAdapter extends BaseAdapter {

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
                        .inflate(R.layout.item_message, parent, false);
            }
            MessagesApi.Item item = items.get(position);
            ((TextView) view.findViewById(R.id.itemTitle)).setText(item.title);
            TextView body = view.findViewById(R.id.itemBody);
            if (item.body.isEmpty()) {
                body.setVisibility(View.GONE);
            } else {
                body.setText(item.body);
                body.setVisibility(View.VISIBLE);
            }
            ((TextView) view.findViewById(R.id.itemTime)).setText(item.time);
            view.findViewById(R.id.itemDot).setVisibility(item.unread ? View.VISIBLE : View.INVISIBLE);
            return view;
        }
    }
}
