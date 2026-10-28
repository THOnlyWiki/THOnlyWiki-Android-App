package cc.thonly.app.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import cc.thonly.app.R;

/** 筛选标签（年份、类型）的统一样式工厂，避免两处各写一遍。 */
public final class ChipFactory {

    private static final int PADDING_HORIZONTAL_DP = 14;
    private static final int PADDING_VERTICAL_DP = 7;
    private static final int CORNER_RADIUS_DP = 16;
    private static final int GAP_DP = 8;
    private static final float TEXT_SIZE_SP = 13f;

    private ChipFactory() {
    }

    public static TextView create(Context context, CharSequence label, boolean selected,
                                  View.OnClickListener listener) {
        TextView chip = new TextView(context);
        chip.setText(label);
        chip.setTextSize(TEXT_SIZE_SP);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(context, PADDING_HORIZONTAL_DP), dp(context, PADDING_VERTICAL_DP),
                dp(context, PADDING_HORIZONTAL_DP), dp(context, PADDING_VERTICAL_DP));
        chip.setTextColor(context.getColor(selected ? R.color.on_accent : R.color.text_secondary));

        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(context, CORNER_RADIUS_DP));
        background.setColor(context.getColor(selected ? R.color.accent : R.color.chip_bg));
        chip.setBackground(background);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(context, GAP_DP);
        chip.setLayoutParams(params);

        chip.setOnClickListener(listener);
        return chip;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
