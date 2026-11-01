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
import cc.thonly.app.util.AvatarLoader;

/**
 * 消息中心：通知（Echo）+ 私信（SocialProfile）。
 *
 * <p>两个 tab 共用一个 ListView，只是数据源不同。
 * 点通知 = 标为已读；点会话 = 打开对话弹窗（打开即把对方发来的未读标已读）。
 */
public final class MessagesController {

    private static final int MODE_NOTIFY = 0;
    private static final int MODE_CHAT = 1;

    /** 点会话时通知宿主打开独立聊天页 */
    public interface OnOpenChat {
        void openChat(String peer);
    }

    private final Activity activity;
    private final OnOpenChat onOpenChat;
    private final TextView tabNotify;
    private final TextView tabChat;
    private final ListView listView;
    private final TextView emptyView;
    private final EditText searchBox;
    private final MessageAdapter adapter;
    private final List<MessagesApi.Item> items = new ArrayList<MessagesApi.Item>();

    private int mode = MODE_NOTIFY;
    /** 正在显示搜索结果（此时点条目是开新对话） */
    private boolean searching;
    private final android.os.Handler debounce =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingSearch;
    /** 请求序号：只有最后一次发出的请求才有资格刷新界面 */
    private int requestSeq;

    public MessagesController(final Activity activity, View root, OnOpenChat onOpenChat) {
        this.activity = activity;
        this.onOpenChat = onOpenChat;
        this.tabNotify = root.findViewById(R.id.tabNotify);
        this.tabChat = root.findViewById(R.id.tabChat);
        this.listView = root.findViewById(R.id.messageList);
        this.emptyView = root.findViewById(R.id.messageEmpty);
        this.searchBox = root.findViewById(R.id.userSearch);

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
                    onOpenChat.openChat(item.peer);
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

        // 边打边搜，300ms 防抖，避免每个字都发一次请求
        searchBox.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable editable) {
                scheduleSearch(editable.toString().trim());
            }
        });
    }

    private void scheduleSearch(final String keyword) {
        if (pendingSearch != null) {
            debounce.removeCallbacks(pendingSearch);
        }
        pendingSearch = new Runnable() {
            @Override
            public void run() {
                if (keyword.isEmpty()) {
                    searching = false;
                    reload();
                } else {
                    search(keyword);
                }
            }
        };
        debounce.postDelayed(pendingSearch, 300L);
    }

    /** 搜人：结果直接铺在同一个列表里，点一条就开始新对话 */
    private void search(final String keyword) {
        if (!new UserSession(activity).isLoggedIn()) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                MessagesApi.Result result = null;
                try {
                    result = MessagesApi.searchUsers(activity, keyword);
                } catch (IOException ignored) {
                    // 网络问题就保持上一次的结果
                }
                final MessagesApi.Result loaded = result;
                if (loaded == null) {
                    return;
                }
                final List<MessagesApi.Item> found = MessagesApi.userItems(loaded);
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!keyword.equals(searchBox.getText().toString().trim())) {
                            return;   // 已经有更新的输入了，丢掉这次结果
                        }
                        if (!loaded.success) {
                            // 401 / 网络错误 ≠ 搜不到人，别把错误说成"没找到"
                            items.clear();
                            adapter.notifyDataSetChanged();
                            emptyView.setText(loaded.message.isEmpty()
                                    ? activity.getString(R.string.messages_load_failed)
                                    : loaded.message);
                            emptyView.setVisibility(View.VISIBLE);
                            return;
                        }
                        searching = true;
                        items.clear();
                        items.addAll(found);
                        adapter.notifyDataSetChanged();
                        emptyView.setText(R.string.messages_search_empty);
                        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
            }
        }, "messages-search").start();
    }

    /** 每次进入页面都重拉；顺带清掉上次留下的搜索词，别让框里的字和列表对不上 */
    public void onShown() {
        if (!searchBox.getText().toString().isEmpty()) {
            searchBox.setText("");
        }
        reload();
    }

    private void switchMode(int target) {
        if (mode == target) {
            return;
        }
        mode = target;
        searching = false;
        searchBox.setVisibility(target == MODE_CHAT ? View.VISIBLE : View.GONE);
        searchBox.setText("");
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
        final int seq = ++requestSeq;
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
                            // 切了标签或又发起了新请求：这次的结果已经过期，直接丢掉。
                            // 否则「私信」的慢响应会在切到「通知」之后才到，把私信列表画到通知页上。
                            if (seq != requestSeq) {
                                return;
                            }
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
            AvatarLoader.load(view.findViewById(R.id.itemAvatar), item.avatar, item.title,
                    dp(40));
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
