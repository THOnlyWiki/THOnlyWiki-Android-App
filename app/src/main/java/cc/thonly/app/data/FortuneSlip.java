package cc.thonly.app.data;

import java.util.Collections;
import java.util.List;

/** 一张签：首行是「运势 + 角色」，其余是签文正文。 */
public final class FortuneSlip {

    private final String id;
    private final List<String> content;
    private final String sign;

    public FortuneSlip(String id, List<String> content, String sign) {
        this.id = id;
        this.content = Collections.unmodifiableList(content);
        this.sign = sign == null ? "" : sign;
    }

    public String id() {
        return id;
    }

    /** 首行，形如「吉」 古明地恋 */
    public String title() {
        return content.isEmpty() ? "" : content.get(0);
    }

    /** 去掉首行之后的正文 */
    public List<String> body() {
        return content.size() <= 1 ? Collections.<String>emptyList() : content.subList(1, content.size());
    }

    /** 署名，可能为空 */
    public String sign() {
        return sign;
    }

    /** 凶签用不同颜色显示 */
    public boolean isBadLuck() {
        return title().contains("凶");
    }
}
