package cc.thonly.app.ui;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import cc.thonly.app.R;
import cc.thonly.app.data.Checklist;

/**
 * 百宝箱页：工具列表。
 *
 * <p>卡片在代码里生成，加工具只要在构造里多一行 addCard，不用改布局。
 */
public final class ToolboxController {

    /** 百宝箱里的功能页跳转，由 MainActivity 实现 */
    public interface Navigator {
        void openMusic();

        void openGlossary();

        void openItinerary();

        void openMessages();
    }

    private final Activity activity;
    private boolean firstInGroup = true;

    public ToolboxController(final Activity activity, View root, final Navigator navigator) {
        this.activity = activity;
        LinearLayout list = root.findViewById(R.id.toolList);

        addGroupTitle(list, R.string.toolbox_group_tools);
        addCard(list, R.string.tool_budget_title, R.string.tool_budget_desc,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        BudgetDialog.show(activity);
                    }
                });
        addCard(list, R.string.tool_qr_title, R.string.tool_qr_desc,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        QrDialog.show(activity);
                    }
                });
        addCard(list, R.string.tool_glossary_title, R.string.tool_glossary_desc,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        navigator.openGlossary();
                    }
                });

        addGroupTitle(list, R.string.toolbox_group_content);
        addCard(list, R.string.tool_music_title, R.string.tool_music_desc,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        navigator.openMusic();
                    }
                });
        addCard(list, R.string.tool_fortune_title, R.string.tool_fortune_desc,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        FortuneDialog.show(activity);
                    }
                });
        addCard(list, R.string.tool_itinerary_title, R.string.tool_itinerary_desc,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        navigator.openItinerary();
                    }
                });
        addCard(list, R.string.tool_messages_title, R.string.tool_messages_desc,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        navigator.openMessages();
                    }
                });

        addGroupTitle(list, R.string.toolbox_group_memo);
        addCard(list, R.string.tool_packing_title, R.string.tool_packing_desc,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ChecklistDialog.show(activity, "packing",
                                R.string.tool_packing_title, Checklist.PACKING);
                    }
                });
        addCard(list, R.string.tool_cosplay_title, R.string.tool_cosplay_desc,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ChecklistDialog.show(activity, "cosplay",
                                R.string.tool_cosplay_title, Checklist.COSPLAY);
                    }
                });
    }

    public void onShown() {
        // 目前没有需要刷新的状态
    }

    /** 分组标题：每组第一张卡片前面不加分隔线 */
    private void addGroupTitle(LinearLayout list, int titleRes) {
        View title = LayoutInflater.from(activity)
                .inflate(R.layout.item_tool_group, list, false);
        ((TextView) title).setText(titleRes);
        list.addView(title);
        firstInGroup = true;
    }

    private void addCard(LinearLayout list, int titleRes, int descRes,
                         View.OnClickListener click) {
        if (!firstInGroup) {
            list.addView(createDivider());
        }
        firstInGroup = false;
        View card = LayoutInflater.from(activity)
                .inflate(R.layout.item_tool_card, list, false);
        ((TextView) card.findViewById(R.id.cardTitle)).setText(titleRes);
        ((TextView) card.findViewById(R.id.cardDesc)).setText(descRes);
        card.setOnClickListener(click);
        list.addView(card);
    }

    private View createDivider() {
        View divider = new View(activity);
        divider.setBackgroundColor(activity.getColor(R.color.divider));
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
        params.leftMargin = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        divider.setLayoutParams(params);
        return divider;
    }
}
