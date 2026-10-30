package cc.thonly.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cc.thonly.app.R;
import cc.thonly.app.data.Event;
import cc.thonly.app.data.Team;
import cc.thonly.app.data.TeamApi;

/**
 * 组队跑展页：某场活动下的队伍列表。
 *
 * <p>入口在活动详情弹窗的「组队」按钮。联系方式由服务端控制，
 * 没加入时接口根本不返回，这里只是照显示。
 */
public final class TeamController {

    /** 没有「拼房」：平台不撮合陌生人合宿 */
    private static final String[] TYPE_VALUES = {"company", "car", "booth", "shoot"};
    private static final String[] TYPE_LABELS = {"同行", "拼车", "摊位", "拍摄"};
    /** 安全提醒的确认状态（版本号变了会再弹一次） */
    private static final String PREFS_SAFETY = "team_safety";
    private static final String KEY_ACK = "ack_version";
    private static final int NOTICE_VERSION = 1;

    private static final String[] JOIN_VALUES = {"approve", "instant"};
    private static final String[] JOIN_LABELS = {"需要我批准", "立即进入"};

    private final Activity activity;
    private final TextView eventLabel;
    private final TextView createButton;
    private final TextView emptyView;
    private final ListView listView;
    private final TeamAdapter adapter;
    private final List<Team> teams = new ArrayList<Team>();

    private int year;
    private Event event;

    public TeamController(final Activity activity, View root) {
        this.activity = activity;
        this.eventLabel = root.findViewById(R.id.teamEvent);
        this.createButton = root.findViewById(R.id.teamCreate);
        this.emptyView = root.findViewById(R.id.teamEmpty);
        this.listView = root.findViewById(R.id.teamList);

        this.adapter = new TeamAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position,
                                    long id) {
                openDetail(teams.get(position).id());
            }
        });
        createButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCreate();
            }
        });
        root.findViewById(R.id.teamSafety).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSafetyNotice(true);
            }
        });
    }

    /** 切到某场活动的组队列表 */
    public void show(int year, Event event) {
        this.year = year;
        this.event = event;
        eventLabel.setText(event.name());
        showSafetyNotice(false);
        reload();
    }

    /**
     * 组队安全提醒。
     *
     * <p>第一次进组队页必弹，确认后记住版本号不再打扰；
     * 文案改了把 {@link #NOTICE_VERSION} +1 就会重新弹一次。
     */
    private void showSafetyNotice(boolean force) {
        android.content.SharedPreferences prefs = activity
                .getSharedPreferences(PREFS_SAFETY, android.content.Context.MODE_PRIVATE);
        if (!force && prefs.getInt(KEY_ACK, 0) >= NOTICE_VERSION) {
            return;
        }
        final android.content.SharedPreferences target = prefs;
        new AlertDialog.Builder(activity)
                .setTitle(R.string.team_safety_title)
                .setMessage(R.string.team_safety_body)
                .setPositiveButton(R.string.team_safety_ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                target.edit().putInt(KEY_ACK, NOTICE_VERSION).apply();
                            }
                        })
                .setCancelable(false)
                .show();
    }

    public void reload() {
        if (event == null) {
            return;
        }
        final int targetYear = year;
        final int eventId = event.id();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final TeamApi.Result result = TeamApi.list(activity, targetYear, eventId);
                    final List<Team> loaded = TeamApi.teams(result);
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            teams.clear();
                            teams.addAll(loaded);
                            adapter.notifyDataSetChanged();
                            emptyView.setVisibility(teams.isEmpty() ? View.VISIBLE : View.GONE);
                        }
                    });
                } catch (final IOException e) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            emptyView.setText(R.string.team_load_failed);
                            emptyView.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }
        }, "team-list").start();
    }

    // ------------------------------------------------------------ 详情

    private void openDetail(final long id) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final TeamApi.Result result = TeamApi.detail(activity, id);
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!result.success) {
                                toast(result.message);
                                return;
                            }
                            showDetail(result.data);
                        }
                    });
                } catch (IOException e) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            toast(activity.getString(R.string.team_load_failed));
                        }
                    });
                }
            }
        }, "team-detail").start();
    }

    private void showDetail(JSONObject data) {
        JSONObject json = data.optJSONObject("team");
        if (json == null) {
            return;
        }
        final Team team = Team.fromJson(json);
        boolean isOwner = data.optBoolean("isOwner", false);
        boolean joined = data.optBoolean("joined", false);
        boolean contactVisible = data.optBoolean("contactVisible", false);
        String contact = data.optString("contact", "");

        StringBuilder message = new StringBuilder();
        message.append(activity.getString(R.string.team_meta_simple,
                team.ownerName(), team.joined(), team.capacity()));
        message.append('\n').append(team.joinMode().equals("approve")
                ? activity.getString(R.string.team_need_approve)
                : activity.getString(R.string.team_instant));
        if (!team.note().isEmpty()) {
            message.append("\n\n").append(team.note());
        }

        JSONArray members = data.optJSONArray("members");
        if (members != null && members.length() > 0) {
            message.append("\n\n").append(activity.getString(R.string.team_members));
            for (int i = 0; i < members.length(); i++) {
                JSONObject member = members.optJSONObject(i);
                if (member == null) {
                    continue;
                }
                message.append(i == 0 ? " " : "、").append(member.optString("userName", "?"));
                if ("owner".equals(member.optString("role"))) {
                    message.append(activity.getString(R.string.team_owner_tag));
                }
                if ("pending".equals(member.optString("status"))) {
                    message.append(activity.getString(R.string.team_pending_tag));
                }
            }
        }

        message.append("\n\n");
        if (contactVisible && !contact.isEmpty()) {
            message.append(activity.getString(R.string.team_contact_visible, contact));
        } else {
            message.append(activity.getString(R.string.team_contact_hidden));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(team.title())
                .setMessage(message.toString())
                .setNegativeButton(R.string.dialog_close, null);

        if (isOwner) {
            builder.setPositiveButton(R.string.team_close_team,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            act(new TeamApi.Call() {
                                @Override
                                public TeamApi.Result execute() throws IOException {
                                    return TeamApi.close(activity, team.id());
                                }
                            });
                        }
                    });
        } else if (joined) {
            builder.setPositiveButton(R.string.team_leave,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            act(new TeamApi.Call() {
                                @Override
                                public TeamApi.Result execute() throws IOException {
                                    return TeamApi.leave(activity, team.id());
                                }
                            });
                        }
                    });
        } else if (team.isOpen() && !team.isFull()) {
            builder.setPositiveButton(team.joinMode().equals("approve")
                            ? R.string.team_apply : R.string.team_join,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            act(new TeamApi.Call() {
                                @Override
                                public TeamApi.Result execute() throws IOException {
                                    return TeamApi.join(activity, team.id());
                                }
                            });
                        }
                    });
        }
        builder.setNeutralButton(R.string.team_report, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                openReport(team.id());
            }
        });
        builder.show();
    }

    private void openReport(final long id) {
        final EditText input = new EditText(activity);
        input.setHint(R.string.team_report_hint);
        input.setSingleLine(false);
        input.setMaxLines(3);
        new AlertDialog.Builder(activity)
                .setTitle(R.string.team_report)
                .setView(input)
                .setPositiveButton(R.string.team_report_submit,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String reason = input.getText().toString().trim();
                                if (reason.isEmpty()) {
                                    toast(activity.getString(R.string.team_report_empty));
                                    return;
                                }
                                act(new TeamApi.Call() {
                                    @Override
                                    public TeamApi.Result execute() throws IOException {
                                        return TeamApi.report(activity, id, reason);
                                    }
                                });
                            }
                        })
                .setNegativeButton(R.string.edit_cancel, null)
                .show();
    }

    // ------------------------------------------------------------ 发起

    private void openCreate() {
        if (event == null) {
            return;
        }
        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_team_create, null);
        final EditText titleInput = content.findViewById(R.id.teamTitleInput);
        final Spinner typeSpinner = content.findViewById(R.id.teamTypeSpinner);
        final Spinner joinSpinner = content.findViewById(R.id.teamJoinModeSpinner);
        final EditText capacityInput = content.findViewById(R.id.teamCapacityInput);
        final EditText contactInput = content.findViewById(R.id.teamContactInput);
        final EditText noteInput = content.findViewById(R.id.teamNoteInput);

        typeSpinner.setAdapter(new DropdownAdapter(activity, labels(TYPE_LABELS)));
        joinSpinner.setAdapter(new DropdownAdapter(activity, labels(JOIN_LABELS)));

        new AlertDialog.Builder(activity)
                .setTitle(R.string.team_create)
                .setView(content)
                .setPositiveButton(R.string.team_publish, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String title = titleInput.getText().toString().trim();
                        String contact = contactInput.getText().toString().trim();
                        if (title.isEmpty()) {
                            toast(activity.getString(R.string.team_title_required));
                            return;
                        }
                        if (contact.isEmpty()) {
                            toast(activity.getString(R.string.team_contact_required));
                            return;
                        }
                        int capacity = 2;
                        try {
                            capacity = Integer.parseInt(capacityInput.getText().toString().trim());
                        } catch (NumberFormatException ignored) {
                            // 用默认值
                        }
                        final int finalCapacity = Math.max(2, Math.min(30, capacity));
                        final String finalTitle = title;
                        final String finalContact = contact;
                        act(new TeamApi.Call() {
                            @Override
                            public TeamApi.Result execute() throws IOException {
                                return TeamApi.create(activity, year, event.id(), event.name(),
                                        TYPE_VALUES[typeSpinner.getSelectedItemPosition()],
                                        finalTitle, finalCapacity,
                                        JOIN_VALUES[joinSpinner.getSelectedItemPosition()],
                                        finalContact, noteInput.getText().toString().trim());
                            }
                        });
                    }
                })
                .setNegativeButton(R.string.edit_cancel, null)
                .show();
    }

    // ------------------------------------------------------------ 工具

    /** 网络请求不能放主线程，包一层：后台执行 → 主线程提示 → 刷新列表 */
    private void act(final TeamApi.Call call) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final TeamApi.Result result = call.execute();
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            toast(result.message);
                            if (result.success) {
                                reload();
                            }
                        }
                    });
                } catch (IOException e) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            toast(activity.getString(R.string.team_network_failed));
                        }
                    });
                }
            }
        }, "team-action").start();
    }

    private void toast(String text) {
        if (text != null && !text.isEmpty()) {
            Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
        }
    }

    private static String typeLabel(String type) {
        for (int i = 0; i < TYPE_VALUES.length; i++) {
            if (TYPE_VALUES[i].equals(type)) {
                return TYPE_LABELS[i];
            }
        }
        return TYPE_LABELS[0];
    }

    private static List<String> labels(String[] values) {
        List<String> list = new ArrayList<String>();
        for (String value : values) {
            list.add(value);
        }
        return list;
    }

    private final class TeamAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return teams.size();
        }

        @Override
        public Object getItem(int position) {
            return teams.get(position);
        }

        @Override
        public long getItemId(int position) {
            return teams.get(position).id();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(activity)
                        .inflate(R.layout.item_team, parent, false);
            }
            Team team = teams.get(position);
            ((TextView) view.findViewById(R.id.itemType)).setText(typeLabel(team.type()));
            ((TextView) view.findViewById(R.id.itemTitle)).setText(team.title());

            StringBuilder meta = new StringBuilder();
            meta.append(activity.getString(R.string.team_meta_simple,
                    team.ownerName(), team.joined(), team.capacity()));
            if (!team.isOpen()) {
                meta.append(" · ").append(activity.getString(R.string.team_closed_tag));
            } else if (team.isFull()) {
                meta.append(" · ").append(activity.getString(R.string.team_full_tag));
            }
            TextView metaView = view.findViewById(R.id.itemMeta);
            metaView.setText(meta);

            TextView noteView = view.findViewById(R.id.itemNote);
            if (team.note().isEmpty()) {
                noteView.setVisibility(View.GONE);
            } else {
                noteView.setText(team.note());
                noteView.setVisibility(View.VISIBLE);
            }
            return view;
        }
    }
}
