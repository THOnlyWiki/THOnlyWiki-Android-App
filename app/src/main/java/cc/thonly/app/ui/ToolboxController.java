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

    private final Activity activity;

    public ToolboxController(final Activity activity, View root, final Runnable openMusic) {
        this.activity = activity;
        LinearLayout list = root.findViewById(R.id.toolList);

        addCard(list, R.string.tool_music_title, R.string.tool_music_desc,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openMusic.run();
                    }
                });
        addCard(list, R.string.tool_budget_title, R.string.tool_budget_desc,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        BudgetDialog.show(activity);
                    }
                });
        addCard(list, R.string.tool_fortune_title, R.string.tool_fortune_desc,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        FortuneDialog.show(activity);
                    }
                });
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

    private void addCard(LinearLayout list, int titleRes, int descRes,
                         View.OnClickListener click) {
        if (list.getChildCount() > 0) {
            list.addView(createDivider());
        }
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
