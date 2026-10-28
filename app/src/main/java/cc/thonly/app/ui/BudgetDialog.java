package cc.thonly.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cc.thonly.app.R;
import cc.thonly.app.data.BudgetTypes;

/**
 * 预算分摊计算器。
 *
 * <p>费用项是动态的：内置几个常用类型，用户还能自己加，自定义类型存本地、
 * 下次打开还在。金额和人数一变就重算，不用点按钮。
 */
public final class BudgetDialog {

    private final Activity activity;
    private final BudgetTypes types;
    private final LinearLayout rowsContainer;
    private final EditText peopleInput;
    private final TextView resultView;
    private final List<Row> rows = new ArrayList<Row>();

    private BudgetDialog(Activity activity, View content) {
        this.activity = activity;
        this.types = new BudgetTypes(activity);
        this.rowsContainer = content.findViewById(R.id.budgetRows);
        this.peopleInput = content.findViewById(R.id.budgetPeople);
        this.resultView = content.findViewById(R.id.budgetResult);

        peopleInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                recalc();
            }
        });

        content.findViewById(R.id.budgetAddRow).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addRow(types.all().get(0));
                recalc();
            }
        });
    }

    public static void show(Activity activity) {
        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_budget, null);
        BudgetDialog dialog = new BudgetDialog(activity, content);

        // 默认摆三行最常用的
        dialog.addRow("门票");
        dialog.addRow("交通");
        dialog.addRow("住宿");
        dialog.recalc();

        new AlertDialog.Builder(activity)
                .setTitle(R.string.tool_budget_title)
                .setView(content)
                .setPositiveButton(R.string.dialog_close, null)
                .show();
    }

    private void addRow(String type) {
        View rowView = LayoutInflater.from(activity)
                .inflate(R.layout.item_budget_row, rowsContainer, false);
        final Row row = new Row(rowView);
        row.bindOptions(type);
        rows.add(row);
        rowsContainer.addView(rowView);

        rowView.findViewById(R.id.rowRemove).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rows.remove(row);
                rowsContainer.removeView(row.view);
                recalc();
            }
        });
    }

    private void recalc() {
        double total = 0;
        for (Row row : rows) {
            total += row.amount();
        }
        int headcount = parseHeadcount();

        if (total <= 0) {
            resultView.setText(R.string.budget_result_empty);
            resultView.setTextColor(activity.getColor(R.color.text_secondary));
            return;
        }
        resultView.setText(activity.getString(R.string.budget_result,
                money(total), money(total / headcount)));
        resultView.setTextColor(activity.getColor(R.color.text_primary));
    }

    private int parseHeadcount() {
        String text = peopleInput.getText().toString().trim();
        if (text.isEmpty()) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(text));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** 整数不显示小数点，否则保留两位 */
    private static String money(double value) {
        if (Math.abs(value - Math.round(value)) < 0.005) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.US, "%.2f", value);
    }

    /** 新建自定义类型 */
    private void promptNewType(final Row row, final int previousIndex) {
        final EditText input = new EditText(activity);
        input.setHint(R.string.budget_new_type_hint);
        input.setTextSize(14);
        input.setSingleLine(true);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.budget_new_type)
                .setView(input)
                .setPositiveButton(R.string.edit_save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim();
                        if (types.addCustom(name)) {
                            row.bindOptions(name);
                        } else {
                            Toast.makeText(activity, R.string.budget_new_type_invalid,
                                    Toast.LENGTH_SHORT).show();
                            row.bindOptions(fallbackType(previousIndex));
                        }
                    }
                })
                .setNegativeButton(R.string.edit_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        row.bindOptions(fallbackType(previousIndex));
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        row.bindOptions(fallbackType(previousIndex));
                    }
                })
                .show();
    }

    private String fallbackType(int index) {
        List<String> all = types.all();
        return all.get(Math.min(Math.max(index, 0), all.size() - 1));
    }

    /** 一行费用：类型下拉 + 金额输入 */
    private final class Row implements AdapterView.OnItemSelectedListener {

        private final View view;
        private final Spinner typeSpinner;
        private final EditText amountInput;

        /** 重建下拉时抑制回调，避免把「新建类型」当成真的选了 */
        private boolean rebuilding;
        private int selectedIndex;

        Row(View view) {
            this.view = view;
            this.typeSpinner = view.findViewById(R.id.rowType);
            this.amountInput = view.findViewById(R.id.rowAmount);
            typeSpinner.setOnItemSelectedListener(this);
            amountInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    recalc();
                }
            });
        }

        void bindOptions(String selected) {
            List<String> options = types.options();
            int index = options.indexOf(selected);
            if (index < 0) {
                index = 0;
            }
            rebuilding = true;
            typeSpinner.setAdapter(new DropdownAdapter(activity, options));
            typeSpinner.setSelection(index, false);
            selectedIndex = index;
            rebuilding = false;
        }

        double amount() {
            String text = amountInput.getText().toString().trim();
            if (text.isEmpty()) {
                return 0;
            }
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
            if (rebuilding) {
                return;
            }
            if (position == types.all().size()) {
                // 选了「+ 新建类型…」
                promptNewType(this, selectedIndex);
                return;
            }
            selectedIndex = position;
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }
}
