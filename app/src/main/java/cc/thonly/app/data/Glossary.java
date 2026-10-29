package cc.thonly.app.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cc.thonly.app.util.AppLog;

/** 圈内术语词典，数据在 assets/glossary.json。 */
public final class Glossary {

    private static final String ASSET_NAME = "glossary.json";

    private static List<Term> cache;

    private Glossary() {
    }

    public static final class Term {

        private final String term;
        private final String desc;

        Term(String term, String desc) {
            this.term = term;
            this.desc = desc;
        }

        public String term() {
            return term;
        }

        public String desc() {
            return desc;
        }

        /** 词条或释义命中关键字 */
        public boolean matches(String keyword) {
            if (keyword == null || keyword.trim().isEmpty()) {
                return true;
            }
            String key = keyword.trim().toLowerCase(java.util.Locale.US);
            return term.toLowerCase(java.util.Locale.US).contains(key)
                    || desc.toLowerCase(java.util.Locale.US).contains(key);
        }
    }

    public static List<Term> all(Context context) {
        if (cache != null) {
            return cache;
        }
        List<Term> terms = new ArrayList<Term>();
        try {
            JSONObject root = new JSONObject(readAsset(context));
            JSONArray array = root.optJSONArray("terms");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    String term = item.optString("term", "");
                    if (term.isEmpty()) {
                        continue;
                    }
                    terms.add(new Term(term, item.optString("desc", "")));
                }
            }
        } catch (Exception e) {
            AppLog.e("glossary", "读取术语表失败", e);
        }
        cache = terms;
        return cache;
    }

    private static String readAsset(Context context) throws IOException {
        InputStream input = context.getAssets().open(ASSET_NAME);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int count;
        while ((count = input.read(chunk)) != -1) {
            buffer.write(chunk, 0, count);
        }
        input.close();
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }
}
