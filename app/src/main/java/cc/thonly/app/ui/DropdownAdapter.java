package cc.thonly.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

import cc.thonly.app.R;

/**
 * 下拉菜单适配器：收起时带箭头，展开时是普通列表项。
 *
 * <p>Spinner 一旦换了自定义背景就丢了系统自带的箭头，所以箭头画在收起态的布局里。
 */
public final class DropdownAdapter extends ArrayAdapter<String> {

    private final LayoutInflater inflater;

    public DropdownAdapter(Context context, List<String> items) {
        super(context, R.layout.item_dropdown, items);
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return bind(convertView, parent, R.layout.item_dropdown, position);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return bind(convertView, parent, R.layout.item_dropdown_option, position);
    }

    private View bind(View convertView, ViewGroup parent, int layoutRes, int position) {
        TextView view = convertView instanceof TextView
                ? (TextView) convertView
                : (TextView) inflater.inflate(layoutRes, parent, false);
        view.setText(getItem(position));
        return view;
    }
}
