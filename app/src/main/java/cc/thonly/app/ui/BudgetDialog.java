package cc.thonly.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Locale;

import cc.thonly.app.R;

/**
 * 预算分摊计算器：门票 + 交通 + 住宿 + 其他，按人数均摊。
 *
 * <p>边输边算，不用点「计算」按钮——这种小工具多一步操作就没人用了。
 */
public final class BudgetDialog {

    private BudgetDialog() {
    }

    public static void show(final Activity activity) {
        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_budget, null);
        final EditText ticket = content.findViewById(R.id.budgetTicket);
        final EditText travel = content.findViewById(R.id.budgetTravel);
        final EditText hotel = content.findViewById(R.id.budgetHotel);
        final EditText other = content.findViewById(R.id.budgetOther);
        final EditText people = content.findViewById(R.id.budgetPeople);
        final TextView result = content.findViewById(R.id.budgetResult);

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                update(activity, ticket, travel, hotel, other, people, result);
            }
        };
        ticket.addTextChangedListener(watcher);
        travel.addTextChangedListener(watcher);
        hotel.addTextChangedListener(watcher);
        other.addTextChangedListener(watcher);
        people.addTextChangedListener(watcher);

        update(activity, ticket, travel, hotel, other, people, result);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.tool_budget_title)
                .setView(content)
                .setPositiveButton(R.string.dialog_close, null)
                .show();
    }

    private static void update(Activity activity, EditText ticket, EditText travel, EditText hotel,
                               EditText other, EditText people, TextView result) {
        double total = parse(ticket) + parse(travel) + parse(hotel) + parse(other);
        int headcount = (int) parse(people);
        if (headcount < 1) {
            headcount = 1;
        }

        if (total <= 0) {
            result.setText(R.string.budget_result_empty);
            result.setTextColor(activity.getColor(R.color.text_secondary));
            return;
        }
        result.setText(activity.getString(R.string.budget_result,
                money(total), money(total / headcount)));
        result.setTextColor(activity.getColor(R.color.text_primary));
    }

    private static double parse(EditText input) {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 整数不显示小数点，否则保留两位 */
    private static String money(double value) {
        if (Math.abs(value - Math.round(value)) < 0.005) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.US, "%.2f", value);
    }
}
