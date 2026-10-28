package cc.thonly.app.ui;

import android.app.Activity;
import android.view.View;

import cc.thonly.app.R;

/** 百宝箱页：工具列表。目前只有预算分摊，后面加工具就在这里挂一个卡片。 */
public final class ToolboxController {

    private final Activity activity;

    public ToolboxController(final Activity activity, View root) {
        this.activity = activity;
        root.findViewById(R.id.rowBudget).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BudgetDialog.show(activity);
            }
        });
        root.findViewById(R.id.rowFortune).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FortuneDialog.show(activity);
            }
        });
    }

    public void onShown() {
        // 目前没有需要刷新的状态
    }
}
