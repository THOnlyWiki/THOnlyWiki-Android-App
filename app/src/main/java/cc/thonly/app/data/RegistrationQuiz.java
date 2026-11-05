package cc.thonly.app.data;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 注册问答状态及旧服务器注册表单兼容解析；不执行页面脚本。 */
public final class RegistrationQuiz {
    private static final Pattern FORM = Pattern.compile("<form\\b([^>]*)>(.*?)</form\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern INPUT = Pattern.compile("<input\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern LABEL = Pattern.compile("<label\\b([^>]*)>(.*?)</label\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    private final boolean required;
    private final int index;
    private final String question;
    private final boolean htmlQuestion;

    public RegistrationQuiz(boolean required, int index, String question) throws IOException {
        this(required, index, question, false);
    }

    private RegistrationQuiz(boolean required, int index, String question, boolean htmlQuestion) throws IOException {
        if (required && (index < 0 || question == null || question.trim().isEmpty())) {
            throw new IOException("Invalid registration challenge");
        }
        this.required = required;
        this.index = required ? index : -1;
        this.question = required ? question.trim() : "";
        this.htmlQuestion = htmlQuestion;
    }

    public boolean required() { return required; }
    public int index() { return index; }
    public String question() { return question; }
    public boolean htmlQuestion() { return htmlQuestion; }

    public void appendTo(Map<String, String> form, String answer) {
        if (required) {
            form.put("registerQuizIndex", String.valueOf(index));
            form.put("registerAnswer", answer == null ? "" : answer.trim());
        }
    }

    /** 只在题目 API 不存在时读取既有注册表单，不把错误页当成“验证码关闭”。 */
    public static RegistrationQuiz fromRegistrationHtml(String html) throws IOException {
        String form = null;
        Matcher forms = FORM.matcher(html == null ? "" : html);
        while (forms.find()) {
            if ("wiki-register-form".equals(attribute(forms.group(1), "id"))) {
                form = forms.group(2);
                break;
            }
        }
        if (form == null) { throw new IOException("Registration form missing"); }
        String index = null;
        boolean answerField = false;
        Matcher inputs = INPUT.matcher(form);
        while (inputs.find()) {
            String name = attribute(inputs.group(1), "name");
            if ("registerQuizIndex".equals(name)) { index = attribute(inputs.group(1), "value"); }
            if ("registerAnswer".equals(name)) { answerField = true; }
        }
        if (index == null && !answerField) { return new RegistrationQuiz(false, -1, ""); }
        String question = null;
        Matcher labels = LABEL.matcher(form);
        while (labels.find()) {
            if ("wpRegisterAnswer".equals(attribute(labels.group(1), "for"))) {
                question = labels.group(2).replaceAll("<[^>]*>", "").trim();
                break;
            }
        }
        if (!answerField || index == null) { throw new IOException("Incomplete registration challenge"); }
        try {
            return new RegistrationQuiz(true, Integer.parseInt(index.trim()), question, true);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid registration challenge index", e);
        }
    }

    private static String attribute(String attrs, String name) {
        Matcher matcher = ATTRIBUTE.matcher(attrs);
        while (matcher.find()) {
            if (name.equalsIgnoreCase(matcher.group(1))) {
                return matcher.group(2) != null ? matcher.group(2) : matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
            }
        }
        return null;
    }
}
