package cc.thonly.app.ui;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import cc.thonly.app.R;
import cc.thonly.app.data.Glossary;

/** 术语词典页：搜索框 + 词条列表，边打边筛。 */
public final class GlossaryController {

    private final Activity activity;
    private final ListView listView;
    private final TextView emptyLabel;
    private final List<Glossary.Term> allTerms;
    private final List<Glossary.Term> shown = new ArrayList<Glossary.Term>();
    private final TermAdapter adapter;

    public GlossaryController(final Activity activity, View root) {
        this.activity = activity;
        this.allTerms = Glossary.all(activity);

        listView = root.findViewById(R.id.glossaryList);
        emptyLabel = root.findViewById(R.id.glossaryEmpty);
        EditText search = root.findViewById(R.id.glossarySearch);

        adapter = new TermAdapter();
        listView.setAdapter(adapter);

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyFilter(s.toString());
            }
        });

        applyFilter("");
    }

    private void applyFilter(String keyword) {
        shown.clear();
        for (Glossary.Term term : allTerms) {
            if (term.matches(keyword)) {
                shown.add(term);
            }
        }
        adapter.notifyDataSetChanged();
        emptyLabel.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private final class TermAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(activity)
                        .inflate(R.layout.item_glossary, parent, false);
            }
            Glossary.Term term = shown.get(position);
            ((TextView) view.findViewById(R.id.termName)).setText(term.term());
            ((TextView) view.findViewById(R.id.termDesc)).setText(term.desc());
            return view;
        }
    }
}
